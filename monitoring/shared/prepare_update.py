"""Prepare an SSM Run Command payload to update an existing Grafana host.

Candidate files are validated before stopping services. Existing configuration,
credentials and Docker volumes are backed up before applying the update. The
administrator password is never generated or overwritten by this update path.
This local generator does not invoke AWS APIs or modify a remote server.
"""

import argparse
import base64
import ipaddress
import json
from pathlib import Path
import re
import shlex

from prepare_stack import CONFIG_DIRECTORY, CONFIG_FILES, config_payloads

REMOTE_DIRECTORY = Path("/opt/otboo-grafana")
PASSWORD_FILE = Path("/etc/otboo-grafana/admin-password")
BACKUP_DIRECTORY = Path("/var/backups/otboo-grafana")
SMTP_DIRECTORY = Path("/etc/otboo-grafana")
COMMAND_TIMEOUT_SECONDS = 900
DNS_LABEL_PATTERN = re.compile(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")


def validate_address(address):
    """Accept an IPv4 address or DNS hostname without scheme, port or shell input."""
    try:
        parsed_address = ipaddress.ip_address(address)
    except ValueError:
        labels = address.split(".")
        if len(address) <= 253 and len(labels) > 1 and all(
            DNS_LABEL_PATTERN.fullmatch(label) for label in labels
        ) and not all(label.isdigit() for label in labels):
            return address
    else:
        if parsed_address.version == 4:
            return address
    raise ValueError("address must be an IPv4 address or DNS hostname without a scheme or port")


def update_script(address, directory=CONFIG_DIRECTORY, remote_directory=REMOTE_DIRECTORY,
                  password_file=PASSWORD_FILE, backup_directory=BACKUP_DIRECTORY,
                  smtp_directory=SMTP_DIRECTORY):
    """Validate optional SMTP secrets and preserve them alongside consistent volume backups."""
    validate_address(address)
    commands = [
        "#!/bin/bash",
        "set -euo pipefail",
        "umask 077",
        f"deploy_root={shlex.quote(str(remote_directory))}",
        f"backup_root={shlex.quote(str(backup_directory))}",
        f"export GRAFANA_ADDRESS={shlex.quote(address)}",
        f"export GRAFANA_ADMIN_PASSWORD_FILE={shlex.quote(str(password_file))}",
        f"export GRAFANA_SMTP_USER_FILE={shlex.quote(str(smtp_directory / 'smtp-user'))}",
        f"export GRAFANA_SMTP_PASSWORD_FILE={shlex.quote(str(smtp_directory / 'smtp-password'))}",
        f"export GRAFANA_ALERT_EMAIL_FILE={shlex.quote(str(smtp_directory / 'alert-email'))}",
        'test -s "$GRAFANA_ADMIN_PASSWORD_FILE"',
        'test -f "$deploy_root/compose.yaml"',
        'exec 9>"$deploy_root/.deploy.lock"',
        "flock -n 9 || { echo 'Another Grafana update is running' >&2; exit 1; }",
        "smtp_files=0",
        'for secret_file in "$GRAFANA_SMTP_USER_FILE" "$GRAFANA_SMTP_PASSWORD_FILE" "$GRAFANA_ALERT_EMAIL_FILE"; do',
        '  if [ -e "$secret_file" ]; then test -s "$secret_file"; smtp_files=$((smtp_files + 1)); fi',
        "done",
        '[ "$smtp_files" -eq 0 ] || [ "$smtp_files" -eq 3 ] || { echo "SMTP configuration requires all three secret files" >&2; exit 1; }',
        'if [ "$smtp_files" -eq 3 ]; then export GRAFANA_ALERT_EMAIL="$(cat "$GRAFANA_ALERT_EMAIL_FILE")"; fi',
        'staging=$(mktemp -d "$deploy_root/.update.XXXXXX")',
        'trap \'rm -rf -- "$staging"\' EXIT',
    ]
    for relative_path, encoded in config_payloads(directory):
        parent = Path(relative_path).parent.as_posix()
        commands.extend([
            f'install -d -m 755 "$staging/{parent}"',
            f'printf %s {shlex.quote(encoded)} | base64 --decode | gzip --decompress > "$staging/{relative_path}"',
            f'chmod 644 "$staging/{relative_path}"',
        ])
    config_paths = " ".join(shlex.quote(path) for path in CONFIG_FILES)
    commands.extend([
        'compose_at() { local config_root="$1"; shift; if [ "$smtp_files" -eq 3 ] && [ -f "$config_root/compose.email.yaml" ]; then docker compose --env-file /dev/null -f "$config_root/compose.yaml" -f "$config_root/compose.email.yaml" "$@"; else docker compose --env-file /dev/null -f "$config_root/compose.yaml" "$@"; fi; }',
        'candidate() { compose_at "$staging" "$@"; }',
        'current() { compose_at "$deploy_root" "$@"; }',
        "candidate config --quiet",
        'candidate config --format json > "$staging/resolved.json"',
        'caddy_image=$(python3 -c \'import json,sys; print(json.load(open(sys.argv[1]))["services"]["caddy"]["image"])\' "$staging/resolved.json")',
        "candidate pull",
        'docker run --rm --network none -e GRAFANA_ADDRESS -v "$staging/Caddyfile:/etc/caddy/Caddyfile:ro" --entrypoint caddy "$caddy_image" validate --config /etc/caddy/Caddyfile --adapter caddyfile',
        'if [ "$smtp_files" -eq 3 ]; then docker run --rm --network none -v "$staging/provisioning:/etc/grafana/provisioning:ro" -v "$staging/email/notifications.json:/etc/grafana/provisioning/alerting/email.json:ro" --entrypoint /bin/sh "$caddy_image" -c \'test -r /etc/grafana/provisioning/alerting/email.json && test -r /etc/grafana/provisioning/alerting/otboo.json\'; fi',
        'install -d -m 700 "$backup_root"',
        'backup=$(mktemp -d "$backup_root/update-$(date -u +%Y%m%dT%H%M%SZ).XXXXXX")',
        "existing_paths=()",
        f"for path in {config_paths}; do",
        '  if [ -f "$deploy_root/$path" ]; then existing_paths+=("$path"); fi',
        "done",
        'tar -czf "$backup/config.tar.gz" -C "$deploy_root" "${existing_paths[@]}"',
        'cp -p "$GRAFANA_ADMIN_PASSWORD_FILE" "$backup/admin-password"',
        'if [ "$smtp_files" -eq 3 ]; then cp -p "$GRAFANA_SMTP_USER_FILE" "$GRAFANA_SMTP_PASSWORD_FILE" "$GRAFANA_ALERT_EMAIL_FILE" "$backup/"; fi',
        'cleanup() { result=$?; trap - EXIT; current start >/dev/null 2>&1 || true; rm -rf -- "$staging"; exit "$result"; }',
        "trap cleanup EXIT",
        "current stop",
        "for volume in grafana-data caddy-data caddy-config; do",
        '  mountpoint=$(docker volume inspect --format \'{{.Mountpoint}}\' "otboo-shared-monitoring_$volume")',
        '  tar -czf "$backup/$volume.tar.gz" -C "$mountpoint" .',
        "done",
    ])
    for relative_path in CONFIG_FILES:
        parent = Path(relative_path).parent.as_posix()
        commands.extend([
            f'install -d -m 755 "$deploy_root/{parent}"',
            f'install -m 644 "$staging/{relative_path}" "$deploy_root/{relative_path}"',
        ])
    commands.extend([
        "current up -d --force-recreate",
        'curl --fail --silent --show-error --connect-timeout 5 --max-time 10 --retry 18 --retry-all-errors --retry-delay 5 "https://$GRAFANA_ADDRESS/api/health"',
        "trap - EXIT",
        'rm -rf -- "$staging"',
        'printf "\\nGrafana update verified. Backup: %s\\n" "$backup"',
    ])
    return "\n".join(commands) + "\n"


def main():
    """Write reviewable SSM parameters without sending any remote commands."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--address", required=True)
    parser.add_argument("--output", required=True, type=Path)
    arguments = parser.parse_args()
    try:
        script = update_script(arguments.address)
    except ValueError as error:
        parser.error(str(error))
    encoded = base64.b64encode(script.encode()).decode()
    parameters = {
        "commands": ["bash -o pipefail -c " + shlex.quote(f"printf %s {encoded} | base64 --decode | bash")],
        "executionTimeout": [str(COMMAND_TIMEOUT_SECONDS)],
    }
    arguments.output.write_text(json.dumps(parameters, indent=2) + "\n")
    print(f"Prepared SSM update parameters: {arguments.output}")


if __name__ == "__main__":
    main()
