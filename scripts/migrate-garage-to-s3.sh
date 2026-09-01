#!/usr/bin/env bash
set -euo pipefail

source_config="${SOURCE_CONFIG:-/home/silverocean/backend/config/application.properties}"
target_credentials_file="${TARGET_CREDENTIALS_FILE:-/tmp/slickhood-s3-credentials.txt}"
source_bucket="${SOURCE_BUCKET:-slickhood-bucket}"
target_bucket="${TARGET_BUCKET:-slickhood-production-documents-603455138904-ca-central-1-an}"
target_region="${TARGET_REGION:-ca-central-1}"
aws_bin="${AWS_BIN:-/usr/local/bin/aws}"
max_parallel="${MAX_PARALLEL:-4}"
report_root="${REPORT_ROOT:-/home/silverocean/storage-migration-20260901}"
run_id="$(date -u +%Y%m%dT%H%M%SZ)"
run_dir="$report_root/run-$run_id"
manifest="$run_dir/verified-objects.tsv"
failure_log="$run_dir/failures.tsv"
manifest_lock="$run_dir/manifest.lock"
parent_pid="$BASHPID"

cleanup() {
  [[ "$BASHPID" == "$parent_pid" ]] || return 0
  unset SOURCE_ACCESS_KEY SOURCE_SECRET_KEY TARGET_ACCESS_KEY TARGET_SECRET_KEY
  shred -u "$target_credentials_file" 2>/dev/null || rm -f "$target_credentials_file"
}
trap cleanup EXIT

property_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$source_config" | tail -n 1 | tr -d '\r'
}

SOURCE_ACCESS_KEY="$(property_value 'garage.s3.access.key')"
SOURCE_SECRET_KEY="$(property_value 'garage.s3.secret.key')"
source_endpoint="$(property_value 'garage.s3.url')"

mapfile -t target_lines < <(sed '/^[[:space:]]*$/d' "$target_credentials_file" | tr -d '\r')
if [[ "${#target_lines[@]}" -lt 3 ]]; then
  echo "Target credential file format is incomplete" >&2
  exit 1
fi
TARGET_ACCESS_KEY="$(printf '%s' "${target_lines[1]}" | tr -d '[:space:]')"
TARGET_SECRET_KEY="$(printf '%s' "${target_lines[2]}" | tr -d '[:space:]')"

if [[ -z "$SOURCE_ACCESS_KEY" || -z "$SOURCE_SECRET_KEY" || -z "$source_endpoint" ]]; then
  echo "Source Garage configuration is incomplete" >&2
  exit 1
fi

source_aws() {
  AWS_ACCESS_KEY_ID="$SOURCE_ACCESS_KEY" \
  AWS_SECRET_ACCESS_KEY="$SOURCE_SECRET_KEY" \
  AWS_DEFAULT_REGION="garage" \
  AWS_EC2_METADATA_DISABLED=true \
  "$aws_bin" --endpoint-url "$source_endpoint" "$@"
}

target_aws() {
  AWS_ACCESS_KEY_ID="$TARGET_ACCESS_KEY" \
  AWS_SECRET_ACCESS_KEY="$TARGET_SECRET_KEY" \
  AWS_DEFAULT_REGION="$target_region" \
  AWS_EC2_METADATA_DISABLED=true \
  "$aws_bin" "$@"
}

install -d -m 700 -o silverocean -g silverocean "$run_dir"
printf 'key\tbytes\tsource_sha256\ttarget_sha256\tversion_id\n' > "$manifest"
printf 'key\treason\n' > "$failure_log"
chown silverocean:silverocean "$manifest" "$failure_log"
chmod 600 "$manifest" "$failure_log"
: > "$manifest_lock"
chmod 600 "$manifest_lock"
chown silverocean:silverocean "$manifest_lock"

source_inventory="$run_dir/source-inventory.json"
source_aws s3api list-objects-v2 --bucket "$source_bucket" --output json > "$source_inventory"
chmod 600 "$source_inventory"
chown silverocean:silverocean "$source_inventory"

source_count="$(jq '.Contents | length' "$source_inventory")"
source_bytes="$(jq '[.Contents[]?.Size] | add // 0' "$source_inventory")"
if [[ "$(jq -r '.IsTruncated // false' "$source_inventory")" == "true" || "$(jq -r '.NextContinuationToken // empty' "$source_inventory")" != "" ]]; then
  echo "Source inventory is paginated; refusing an incomplete migration" >&2
  exit 1
fi

echo "SOURCE_INVENTORY count=$source_count bytes=$source_bytes"

migrate_one() {
  local encoded_key="$1"
  local key source_file target_file head_file expected_size actual_size source_sha
  local content_type content_disposition content_encoding cache_control metadata
  local put_result target_sha version_id verified_now
  local -a put_args

  key="$(printf '%s' "$encoded_key" | base64 --decode)"
  source_file="$(mktemp)"
  target_file="$(mktemp)"
  head_file="$(mktemp)"

  fail_object() {
    local reason="$1"
    {
      flock -x 9
      printf '%s\t%s\n' "$key" "$reason" >> "$failure_log"
    } 9>"$manifest_lock"
    rm -f "$source_file" "$target_file" "$head_file"
    return 0
  }

  if ! source_aws s3api head-object --bucket "$source_bucket" --key "$key" --output json > "$head_file"; then
    fail_object "source head failed"
    return 0
  fi
  if ! source_aws s3api get-object --bucket "$source_bucket" --key "$key" "$source_file" >/dev/null; then
    fail_object "source download failed"
    return 0
  fi

  expected_size="$(jq -r '.ContentLength' "$head_file")"
  actual_size="$(stat -c '%s' "$source_file")"
  if [[ "$expected_size" != "$actual_size" ]]; then
    fail_object "source size mismatch"
    return 0
  fi

  source_sha="$(sha256sum "$source_file" | awk '{print $1}')"
  put_args=(s3api put-object --bucket "$target_bucket" --key "$key" --body "$source_file" --server-side-encryption AES256)
  content_type="$(jq -r '.ContentType // empty' "$head_file")"
  content_disposition="$(jq -r '.ContentDisposition // empty' "$head_file")"
  content_encoding="$(jq -r '.ContentEncoding // empty' "$head_file")"
  cache_control="$(jq -r '.CacheControl // empty' "$head_file")"
  metadata="$(jq -c '.Metadata // {}' "$head_file")"
  [[ -z "$content_type" ]] || put_args+=(--content-type "$content_type")
  [[ -z "$content_disposition" ]] || put_args+=(--content-disposition "$content_disposition")
  [[ -z "$content_encoding" ]] || put_args+=(--content-encoding "$content_encoding")
  [[ -z "$cache_control" ]] || put_args+=(--cache-control "$cache_control")
  [[ "$metadata" == "{}" ]] || put_args+=(--metadata "$metadata")

  if ! put_result="$(target_aws "${put_args[@]}")"; then
    fail_object "target upload failed"
    return 0
  fi
  if ! target_aws s3api get-object --bucket "$target_bucket" --key "$key" "$target_file" >/dev/null; then
    fail_object "target download failed"
    return 0
  fi
  target_sha="$(sha256sum "$target_file" | awk '{print $1}')"
  if [[ "$source_sha" != "$target_sha" ]]; then
    fail_object "SHA-256 mismatch"
    return 0
  fi

  version_id="$(jq -r '.VersionId // empty' <<<"$put_result")"
  {
    flock -x 9
    printf '%s\t%s\t%s\t%s\t%s\n' "$key" "$actual_size" "$source_sha" "$target_sha" "$version_id" >> "$manifest"
    verified_now="$(( $(wc -l < "$manifest") - 1 ))"
    if (( verified_now % 50 == 0 || verified_now == source_count )); then
      echo "MIGRATION_PROGRESS verified=$verified_now total=$source_count"
    fi
  } 9>"$manifest_lock"
  rm -f "$source_file" "$target_file" "$head_file"
}

running=0
while IFS= read -r encoded_key; do
  [[ -n "$encoded_key" ]] || continue
  migrate_one "$encoded_key" &
  running=$((running + 1))
  if (( running >= max_parallel )); then
    wait -n || true
    running=$((running - 1))
  fi
done < <(jq -r '.Contents[]?.Key | @base64' "$source_inventory")
while (( running > 0 )); do
  wait -n || true
  running=$((running - 1))
done

verified_count="$(( $(wc -l < "$manifest") - 1 ))"
verified_bytes="$(awk -F '\t' 'NR > 1 {total += $2} END {printf "%.0f", total}' "$manifest")"
failure_count="$(( $(wc -l < "$failure_log") - 1 ))"

db_refs_file="$report_root/database-object-refs.txt"
db_missing_target="$run_dir/missing-database-refs.txt"
: > "$db_missing_target"
if [[ -f "$db_refs_file" ]]; then
  while IFS= read -r key; do
    [[ -n "$key" ]] || continue
    if ! target_aws s3api head-object --bucket "$target_bucket" --key "$key" >/dev/null 2>&1; then
      printf '%s\n' "$key" >> "$db_missing_target"
    fi
  done < "$db_refs_file"
fi
chmod 600 "$db_missing_target"
chown silverocean:silverocean "$db_missing_target"
db_missing_count="$(wc -l < "$db_missing_target")"

summary="$run_dir/summary.txt"
{
  echo "run_id=$run_id"
  echo "source_count=$source_count"
  echo "source_bytes=$source_bytes"
  echo "verified_count=$verified_count"
  echo "verified_bytes=$verified_bytes"
  echo "failure_count=$failure_count"
  echo "missing_database_refs=$db_missing_count"
  echo "source_deleted=false"
} > "$summary"
chmod 600 "$summary"
chown silverocean:silverocean "$summary"
cat "$summary"

if [[ "$verified_count" -ne "$source_count" || "$verified_bytes" -ne "$source_bytes" || "$failure_count" -ne 0 || "$db_missing_count" -ne 0 ]]; then
  echo "MIGRATION_VERIFICATION_FAILED" >&2
  exit 1
fi

echo "MIGRATION_VERIFIED"
