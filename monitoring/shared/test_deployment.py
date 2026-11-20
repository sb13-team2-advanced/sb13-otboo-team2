"""Exercise deployment safety locally with Docker and HTTPS commands stubbed."""

import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

from prepare_stack import CONFIG_DIRECTORY, CONFIG_FILES, prepare_template
from prepare_update import update_script, validate_address


class DeploymentTests(unittest.TestCase):
    """Verify retained data and credentials without connecting to AWS or Docker."""

    def setUp(self):
        """Create an isolated host filesystem and deterministic command stubs."""
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.remote = self.root / "deployment"
        self.remote.mkdir()
        self.backups = self.root / "backups"
        self.password = self.root / "admin-password"
        self.password.write_text("existing-password\n")
        self.smtp = self.root / "smtp"
        self.smtp.mkdir()
        for relative_path in CONFIG_FILES:
            destination = self.remote / relative_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text("previous configuration\n")
        self.volumes = self.root / "volumes"
        for name in ("grafana-data", "caddy-data", "caddy-config"):
            volume = self.volumes / name
            volume.mkdir(parents=True)
            (volume / "existing-data").write_text(name)
        binaries = self.root / "bin"
        binaries.mkdir()
        self.log = self.root / "docker.log"
        docker = binaries / "docker"
        docker.write_text("""#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ['FAKE_DOCKER_LOG'], 'a') as output:
    output.write(json.dumps(args) + '\\n')
if args[0] == 'compose' and 'config' in args:
    if os.environ.get('FAIL_COMPOSE'):
        sys.exit(1)
    if '--format' in args:
        print(json.dumps({'services': {'caddy': {'image': 'caddy:test'}}}))
elif args[0] == 'run' and os.environ.get('FAIL_CADDY'):
    sys.exit(1)
elif args[0] == 'run' and os.environ.get('FAIL_EMAIL_MOUNT') and '/bin/sh' in args:
    sys.exit(1)
elif args[:2] == ['volume', 'inspect']:
    if os.environ.get('FAIL_BACKUP'):
        sys.exit(1)
    name = args[-1].removeprefix('otboo-shared-monitoring_')
    print(pathlib.Path(os.environ['FAKE_VOLUMES_ROOT']) / name)
""")
        docker.chmod(0o755)
        curl = binaries / "curl"
        curl.write_text("#!/bin/sh\nprintf '%s\\n' '{\"database\":\"ok\"}'\n")
        curl.chmod(0o755)
        flock = binaries / "flock"
        flock.write_text("#!/bin/sh\nexit ${FAIL_LOCK:-0}\n")
        flock.chmod(0o755)
        self.environment = {
            **os.environ,
            "PATH": str(binaries) + os.pathsep + os.environ["PATH"],
            "FAKE_DOCKER_LOG": str(self.log),
            "FAKE_VOLUMES_ROOT": str(self.volumes),
        }

    def run_update(self):
        """Run the actual generated Bash payload against the isolated host."""
        script = update_script(
            "192.0.2.1", remote_directory=self.remote,
            password_file=self.password, backup_directory=self.backups,
            smtp_directory=self.smtp,
        )
        return subprocess.run(
            ["bash"], input=script, text=True, capture_output=True,
            env=self.environment, timeout=30,
        )

    def test_템플릿은_AMI를_명시적으로_받고_종료시_디스크를_보존한다(self):
        # given
        template = prepare_template()

        # when
        image = template["Parameters"]["ImageId"]
        instance = template["Resources"]["Instance"]["Properties"]

        # then
        self.assertEqual(image["Type"], "AWS::EC2::Image::Id")
        self.assertNotIn("Default", image)
        self.assertFalse(instance["BlockDeviceMappings"][0]["Ebs"]["DeleteOnTermination"])

    def test_설정_갱신은_비밀번호와_볼륨을_유지하고_복구용_백업을_남긴다(self):
        # given
        password_before = self.password.read_bytes()

        # when
        result = self.run_update()

        # then
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.password.read_bytes(), password_before)
        for relative_path in CONFIG_FILES:
            self.assertEqual(
                (self.remote / relative_path).read_bytes(),
                (CONFIG_DIRECTORY / relative_path).read_bytes(),
            )
        backup, = self.backups.iterdir()
        self.assertEqual((backup / "admin-password").read_bytes(), password_before)
        with tarfile.open(backup / "config.tar.gz") as archive:
            self.assertEqual(archive.extractfile("compose.yaml").read(), b"previous configuration\n")
        for name in ("grafana-data", "caddy-data", "caddy-config"):
            self.assertEqual((self.volumes / name / "existing-data").read_text(), name)
            with tarfile.open(backup / f"{name}.tar.gz") as archive:
                self.assertEqual(archive.extractfile("./existing-data").read().decode(), name)

    def test_후보_설정_검증이_실패하면_서비스를_중지하거나_설정을_변경하지_않는다(self):
        # given
        self.environment["FAIL_COMPOSE"] = "1"

        # when
        result = self.run_update()

        # then
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.remote / "compose.yaml").read_text(), "previous configuration\n")
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertFalse(any("stop" in call or "up" in call for call in calls))
        self.assertFalse(self.backups.exists())

    def test_기존_서버에_없는_새_설정은_백업에서_제외하고_추가한다(self):
        # given
        added_paths = (
            "dashboards/application.json", "provisioning/alerting/otboo.json",
            "compose.email.yaml", "email/notifications.json",
            "provisioning/alerting/email.json",
        )
        for relative_path in added_paths:
            (self.remote / relative_path).unlink()

        # when
        result = self.run_update()

        # then
        self.assertEqual(result.returncode, 0, result.stderr)
        backup, = self.backups.iterdir()
        with tarfile.open(backup / "config.tar.gz") as archive:
            self.assertIn("compose.yaml", archive.getnames())
            for relative_path in added_paths:
                self.assertNotIn(relative_path, archive.getnames())
                self.assertEqual(
                    (self.remote / relative_path).read_bytes(),
                    (CONFIG_DIRECTORY / relative_path).read_bytes(),
                )

    def test_SMTP_설정이_없으면_기본_Compose만_사용한다(self):
        # given
        self.assertEqual(list(self.smtp.iterdir()), [])

        # when
        result = self.run_update()

        # then
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertFalse(any("compose.email.yaml" in argument for call in calls for argument in call))

    def test_SMTP_설정이_완전하면_이메일_Compose를_사용하고_비밀값을_백업한다(self):
        # given
        secret_values = {
            "smtp-user": "sender@example.com\n",
            "smtp-password": "existing-smtp-password\n",
            "alert-email": "recipient@example.com\n",
        }
        for name, value in secret_values.items():
            (self.smtp / name).write_text(value)

        # when
        result = self.run_update()

        # then
        self.assertEqual(result.returncode, 0, result.stderr)
        backup, = self.backups.iterdir()
        for name, value in secret_values.items():
            self.assertEqual((self.smtp / name).read_text(), value)
            self.assertEqual((backup / name).read_text(), value)
            self.assertNotIn(value.strip(), result.stdout + result.stderr)
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertTrue(any("compose.email.yaml" in argument for call in calls for argument in call))

    def test_SMTP_설정이_일부만_있으면_서비스를_변경하지_않는다(self):
        # given
        (self.smtp / "smtp-user").write_text("sender@example.com\n")

        # when
        result = self.run_update()

        # then
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.log.exists())
        self.assertEqual((self.remote / "compose.yaml").read_text(), "previous configuration\n")

    def test_이메일_파일_마운트_검증이_실패하면_기존_서비스를_중지하지_않는다(self):
        # given
        (self.smtp / "smtp-user").write_text("sender@example.com\n")
        (self.smtp / "smtp-password").write_text("existing-smtp-password\n")
        (self.smtp / "alert-email").write_text("recipient@example.com\n")
        self.environment["FAIL_EMAIL_MOUNT"] = "1"

        # when
        result = self.run_update()

        # then
        self.assertNotEqual(result.returncode, 0)
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertFalse(any("stop" in call or "up" in call for call in calls))
        self.assertFalse(self.backups.exists())
        self.assertEqual((self.remote / "compose.yaml").read_text(), "previous configuration\n")

    def test_Caddy_검증이_실패하면_기존_설정과_비밀번호가_유지된다(self):
        # given
        self.environment["FAIL_CADDY"] = "1"

        # when
        result = self.run_update()

        # then
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.remote / "Caddyfile").read_text(), "previous configuration\n")
        self.assertEqual(self.password.read_text(), "existing-password\n")
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertFalse(any("stop" in call or "up" in call for call in calls))

    def test_비밀번호가_없으면_새로_생성하지_않고_갱신을_거부한다(self):
        # given
        self.password.unlink()

        # when
        result = self.run_update()

        # then
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.password.exists())
        self.assertFalse(self.log.exists())

    def test_볼륨_백업이_실패하면_설정을_유지하고_기존_컨테이너_시작을_시도한다(self):
        # given
        self.environment["FAIL_BACKUP"] = "1"

        # when
        result = self.run_update()

        # then
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.remote / "compose.yaml").read_text(), "previous configuration\n")
        self.assertEqual(self.password.read_text(), "existing-password\n")
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        self.assertTrue(any("stop" in call for call in calls))
        self.assertTrue(any("start" in call for call in calls))
        self.assertFalse(any("up" in call for call in calls))

    def test_다른_갱신이_실행_중이면_중복_갱신을_거부한다(self):
        # given
        self.environment["FAIL_LOCK"] = "1"

        # when
        result = self.run_update()

        # then
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.log.exists())
        self.assertEqual((self.remote / "compose.yaml").read_text(), "previous configuration\n")

    def test_프로토콜이나_셸_명령이_포함된_주소는_거부한다(self):
        # given
        invalid_addresses = ("https://example.com", "example.com:443", "example.com; touch /tmp/x", "999.1.1.1")

        # when / then
        for address in invalid_addresses:
            with self.subTest(address=address), self.assertRaises(ValueError):
                validate_address(address)


if __name__ == "__main__":
    unittest.main()
