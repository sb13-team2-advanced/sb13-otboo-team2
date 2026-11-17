"""Package the shared Grafana configuration into a CloudFormation template.

The generated template creates an EC2 host with a query-only IAM role. Public
access is through Caddy HTTPS; Grafana's port is only on the Docker network.
The administrator password is generated on the host and never enters the stack.
"""

import argparse
import base64
import json
from pathlib import Path

CONFIG_DIRECTORY = Path(__file__).resolve().parent
CONFIG_FILES = (
    "compose.yaml",
    "Caddyfile",
    "provisioning/datasources/aws.yaml",
    "provisioning/dashboards/ecs.yaml",
    "dashboards/ecs.json",
)
USER_DATA_LIMIT_BYTES = 16384


def config_payloads(directory=CONFIG_DIRECTORY):
    """Encode only the allowlisted non-secret files for remote deployment."""
    return [
        (relative_path, base64.b64encode((directory / relative_path).read_bytes()).decode())
        for relative_path in CONFIG_FILES
    ]


def prepare_template():
    """Embed explicit non-secret configuration files into EC2 bootstrap data."""
    bootstrap = [
        "#!/bin/bash",
        "set -euo pipefail",
        "dnf install -y docker openssl",
        "systemctl enable --now docker amazon-ssm-agent",
        "install -d /usr/local/lib/docker/cli-plugins /opt/otboo-grafana",
        "curl --retry 5 --fail --location --silent --show-error https://github.com/docker/compose/releases/download/v5.7.1/docker-compose-linux-aarch64 -o /usr/local/lib/docker/cli-plugins/docker-compose",
        "curl --retry 5 --fail --location --silent --show-error https://github.com/docker/compose/releases/download/v5.7.1/docker-compose-linux-aarch64.sha256 -o /tmp/docker-compose.sha256",
        "cd /usr/local/lib/docker/cli-plugins",
        "sed 's/docker-compose-linux-aarch64/docker-compose/' /tmp/docker-compose.sha256 | sha256sum --check -",
        "chmod 755 docker-compose",
        "install -d -m 700 /etc/otboo-grafana",
        "umask 077",
        "if [ ! -e /etc/otboo-grafana/admin-password ]; then",
        "  openssl rand -hex 32 > /etc/otboo-grafana/admin-password",
        "fi",
        "test -s /etc/otboo-grafana/admin-password",
        "chown 472:472 /etc/otboo-grafana/admin-password",
        "chmod 400 /etc/otboo-grafana/admin-password",
    ]
    for relative_path, encoded in config_payloads():
        destination = f"/opt/otboo-grafana/{relative_path}"
        bootstrap.extend([
            f"install -d '{Path(destination).parent}'",
            f"printf '%s' '{encoded}' | base64 --decode > '{destination}'",
            f"chmod 644 '{destination}'",
        ])
    bootstrap.extend([
        "export GRAFANA_ADDRESS='${PublicAddress}'",
        "export GRAFANA_ADMIN_PASSWORD_FILE=/etc/otboo-grafana/admin-password",
        "docker compose --env-file /dev/null -f /opt/otboo-grafana/compose.yaml up -d",
    ])
    user_data = "\n".join(bootstrap) + "\n"
    if len(user_data.encode()) > USER_DATA_LIMIT_BYTES:
        raise ValueError("EC2 user data exceeds its 16 KiB limit")
    policy = json.loads((CONFIG_DIRECTORY / "metrics-read-policy.json").read_text())
    return {
        "AWSTemplateFormatVersion": "2010-09-09",
        "Description": "Otboo shared Grafana: EC2, HTTPS, CloudWatch and AMP read access",
        "Parameters": {
            "VpcId": {"Type": "AWS::EC2::VPC::Id"},
            "SubnetId": {"Type": "AWS::EC2::Subnet::Id"},
            "ImageId": {
                "Type": "AWS::EC2::Image::Id",
                "Description": "Explicit Amazon Linux 2023 ARM64 AMI ID. Keep this value for routine updates.",
            },
        },
        "Resources": {
            "PublicAddress": {"Type": "AWS::EC2::EIP", "Properties": {"Domain": "vpc"}},
            "SecurityGroup": {
                "Type": "AWS::EC2::SecurityGroup",
                "Properties": {
                    "GroupDescription": "Otboo Grafana HTTPS and ACME validation",
                    "VpcId": {"Ref": "VpcId"},
                    "SecurityGroupIngress": [
                        {"IpProtocol": "tcp", "FromPort": port, "ToPort": port, "CidrIp": "0.0.0.0/0"}
                        for port in (80, 443)
                    ],
                },
            },
            "Role": {
                "Type": "AWS::IAM::Role",
                "Properties": {
                    "AssumeRolePolicyDocument": {
                        "Version": "2012-10-17",
                        "Statement": [{"Effect": "Allow", "Principal": {"Service": "ec2.amazonaws.com"}, "Action": "sts:AssumeRole"}],
                    },
                    "ManagedPolicyArns": ["arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"],
                    "Policies": [{"PolicyName": "OtbooMetricsReadOnly", "PolicyDocument": policy}],
                },
            },
            "InstanceProfile": {
                "Type": "AWS::IAM::InstanceProfile",
                "Properties": {"Roles": [{"Ref": "Role"}]},
            },
            "Instance": {
                "Type": "AWS::EC2::Instance",
                "Properties": {
                    "ImageId": {"Ref": "ImageId"},
                    "InstanceType": "t4g.small",
                    "CreditSpecification": {"CPUCredits": "standard"},
                    "IamInstanceProfile": {"Ref": "InstanceProfile"},
                    "MetadataOptions": {"HttpTokens": "required", "HttpPutResponseHopLimit": 2, "HttpEndpoint": "enabled"},
                    "NetworkInterfaces": [{
                        "DeviceIndex": "0", "AssociatePublicIpAddress": True,
                        "SubnetId": {"Ref": "SubnetId"}, "GroupSet": [{"Ref": "SecurityGroup"}],
                    }],
                    "BlockDeviceMappings": [{
                        "DeviceName": "/dev/xvda",
                        "Ebs": {"VolumeType": "gp3", "VolumeSize": 20, "Encrypted": True, "DeleteOnTermination": False},
                    }],
                    "Tags": [{"Key": "Name", "Value": "otboo-grafana"}],
                    "PropagateTagsToVolumeOnCreation": True,
                    "UserData": {"Fn::Base64": {"Fn::Sub": user_data}},
                },
            },
            "AddressAssociation": {
                "Type": "AWS::EC2::EIPAssociation",
                "Properties": {"AllocationId": {"Fn::GetAtt": ["PublicAddress", "AllocationId"]}, "InstanceId": {"Ref": "Instance"}},
            },
        },
        "Outputs": {
            "InstanceId": {"Value": {"Ref": "Instance"}},
            "GrafanaUrl": {"Value": {"Fn::Sub": "https://${PublicAddress}/"}},
        },
    }


def main():
    """Write a template for review without making any AWS API calls."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    arguments = parser.parse_args()
    arguments.output.write_text(json.dumps(prepare_template(), indent=2) + "\n")
    print(f"Prepared CloudFormation template: {arguments.output}")


if __name__ == "__main__":
    main()
