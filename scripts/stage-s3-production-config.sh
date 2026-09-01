#!/usr/bin/env bash
set -euo pipefail

credentials_file="/tmp/slickhood-s3-runtime-credentials.txt"
staged_env="/etc/slickhood/pms-storage.env.next"
staged_dropin="/etc/systemd/system/pms.service.d/storage.conf.next"

cleanup() {
  unset access_key secret_key
  shred -u "$credentials_file" 2>/dev/null || rm -f "$credentials_file"
  rm -f "$0"
}
trap cleanup EXIT

if [[ "$EUID" -ne 0 ]]; then
  echo "This script must run as root" >&2
  exit 1
fi

mapfile -t credential_lines < <(sed '/^[[:space:]]*$/d' "$credentials_file" | tr -d '\r')
if [[ "${#credential_lines[@]}" -lt 3 ]]; then
  echo "Credential file format is incomplete" >&2
  exit 1
fi
access_key="$(printf '%s' "${credential_lines[1]}" | tr -d '[:space:]')"
secret_key="$(printf '%s' "${credential_lines[2]}" | tr -d '[:space:]')"

install -d -m 700 -o root -g root /etc/slickhood
umask 077
{
  printf 'GARAGE_S3_ACCESS_KEY=%s\n' "$access_key"
  printf 'GARAGE_S3_SECRET_KEY=%s\n' "$secret_key"
  printf 'GARAGE_S3_BUCKET=slickhood-production-documents-603455138904-ca-central-1-an\n'
  printf 'GARAGE_S3_REGION=ca-central-1\n'
  printf 'GARAGE_S3_PATH_STYLE=false\n'
  printf 'GARAGE_S3_REQUIRE_HTTPS=true\n'
  printf 'GARAGE_S3_URL=\n'
  printf 'GARAGE_PRESIGNER_URL=\n'
  printf 'GARAGE_PRESIGNER_DURATION_SECONDS=120\n'
  printf 'GARAGE_BOOTSTRAP_ENABLED=false\n'
} > "$staged_env"
chown root:root "$staged_env"
chmod 600 "$staged_env"

{
  printf '[Service]\n'
  printf 'EnvironmentFile=/etc/slickhood/pms-storage.env\n'
} > "$staged_dropin"
chown root:root "$staged_dropin"
chmod 644 "$staged_dropin"

stat -c '%a %U:%G %n' "$staged_env" "$staged_dropin"
sed -E 's/=.*/=[REDACTED]/' "$staged_env"
echo "S3_PRODUCTION_CONFIG_STAGED_NOT_ACTIVE"
