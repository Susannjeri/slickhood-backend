#!/usr/bin/env bash
set -euo pipefail

if [[ "$EUID" -ne 0 ]]; then
  echo "This script must run as root" >&2
  exit 1
fi
if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <staged-jar> <expected-sha256>" >&2
  exit 1
fi

staged_jar="$1"
expected_sha="$2"
app_jar="/home/silverocean/backend/silverocean-0.0.1-SNAPSHOT.jar"
staged_env="/etc/slickhood/pms-storage.env.next"
active_env="/etc/slickhood/pms-storage.env"
staged_dropin="/etc/systemd/system/pms.service.d/storage.conf.next"
active_dropin="/etc/systemd/system/pms.service.d/storage.conf"
cutover_check="/tmp/verify-s3-cutover-inventory.sh"
release_dir="/home/silverocean/releases/s3-cutover-$(date -u +%Y%m%dT%H%M%SZ)"
health_url="https://127.0.0.1:8989/actuator/health"
activated=false

require_file() {
  [[ -f "$1" ]] || { echo "Required file missing: $1" >&2; exit 1; }
}

require_file "$staged_jar"
require_file "$staged_env"
require_file "$staged_dropin"
require_file "$cutover_check"

actual_sha="$(sha256sum "$staged_jar" | awk '{print $1}')"
if [[ "$actual_sha" != "$expected_sha" ]]; then
  echo "Staged JAR checksum mismatch" >&2
  exit 1
fi
if ! curl -fsk --max-time 10 "$health_url" | grep -q '"status":"UP"'; then
  echo "Existing production health is not UP" >&2
  exit 1
fi

install -d -m 700 -o root -g root "$release_dir"
install -m 600 -o root -g root "$app_jar" "$release_dir/previous.jar"
systemctl cat pms.service > "$release_dir/previous-systemd.txt"

systemctl stop pms.service
if ! "$cutover_check" > "$release_dir/cutover-inventory.txt" 2>&1; then
  systemctl start pms.service
  cat "$release_dir/cutover-inventory.txt"
  echo "Cutover aborted; existing application restarted unchanged" >&2
  exit 1
fi

install -m 600 -o root -g root "$staged_env" "$active_env"
install -m 644 -o root -g root "$staged_dropin" "$active_dropin"
install -m 750 -o silverocean -g silverocean "$staged_jar" "$app_jar"
systemctl daemon-reload
systemctl start pms.service
activated=true

healthy=false
for _ in $(seq 1 45); do
  if curl -fsk --max-time 5 "$health_url" | grep -q '"status":"UP"'; then
    healthy=true
    break
  fi
  sleep 2
done

if [[ "$healthy" != true ]]; then
  systemctl stop pms.service || true
  install -m 750 -o silverocean -g silverocean "$release_dir/previous.jar" "$app_jar"
  rm -f "$active_dropin"
  systemctl daemon-reload
  systemctl start pms.service
  activated=false
  echo "New S3 release failed health checks and was rolled back" >&2
  exit 1
fi

sha256sum "$app_jar" > "$release_dir/deployed.sha256"
systemctl cat pms.service > "$release_dir/deployed-systemd.txt"
curl -fsk --max-time 10 "$health_url" > "$release_dir/deployed-health.json"
chmod 600 "$release_dir"/*

echo "S3_CUTOVER_DEPLOYED"
echo "release_dir=$release_dir"
echo "activated=$activated"
cat "$release_dir/deployed-health.json"
