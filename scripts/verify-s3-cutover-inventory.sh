#!/usr/bin/env bash
set -euo pipefail

source_config="${SOURCE_CONFIG:-/home/silverocean/backend/config/application.properties}"
target_env="${TARGET_ENV:-/etc/slickhood/pms-storage.env.next}"
source_bucket="${SOURCE_BUCKET:-slickhood-bucket}"
target_bucket="${TARGET_BUCKET:-slickhood-production-documents-603455138904-ca-central-1-an}"
target_region="${TARGET_REGION:-ca-central-1}"
aws_bin="${AWS_BIN:-/usr/local/bin/aws}"
report_root="${REPORT_ROOT:-/home/silverocean/storage-migration-20260901}"
run_id="$(date -u +%Y%m%dT%H%M%SZ)"
run_dir="$report_root/cutover-check-$run_id"

property_value() {
  local file="$1"
  local key="$2"
  sed -n "s/^${key}=//p" "$file" | tail -n 1 | tr -d '\r'
}

source_access="$(property_value "$source_config" 'garage.s3.access.key')"
source_secret="$(property_value "$source_config" 'garage.s3.secret.key')"
source_endpoint="$(property_value "$source_config" 'garage.s3.url')"
target_access="$(property_value "$target_env" 'GARAGE_S3_ACCESS_KEY')"
target_secret="$(property_value "$target_env" 'GARAGE_S3_SECRET_KEY')"

if [[ -z "$source_access" || -z "$source_secret" || -z "$source_endpoint" || -z "$target_access" || -z "$target_secret" ]]; then
  echo "Cutover inventory credentials are incomplete" >&2
  exit 1
fi

source_aws() {
  AWS_ACCESS_KEY_ID="$source_access" AWS_SECRET_ACCESS_KEY="$source_secret" \
  AWS_DEFAULT_REGION=garage AWS_EC2_METADATA_DISABLED=true \
  "$aws_bin" --endpoint-url "$source_endpoint" "$@"
}

target_aws() {
  AWS_ACCESS_KEY_ID="$target_access" AWS_SECRET_ACCESS_KEY="$target_secret" \
  AWS_DEFAULT_REGION="$target_region" AWS_EC2_METADATA_DISABLED=true \
  "$aws_bin" "$@"
}

install -d -m 700 -o silverocean -g silverocean "$run_dir"
source_inventory="$run_dir/source.json"
target_inventory="$run_dir/target.json"
mismatches="$run_dir/mismatches.json"

source_aws s3api list-objects-v2 --bucket "$source_bucket" --output json > "$source_inventory"
target_aws s3api list-objects-v2 --bucket "$target_bucket" --output json > "$target_inventory"
chmod 600 "$source_inventory" "$target_inventory"
chown silverocean:silverocean "$source_inventory" "$target_inventory"

if [[ "$(jq -r '.IsTruncated // false' "$source_inventory")" == true || "$(jq -r '.NextContinuationToken // empty' "$source_inventory")" != "" ]]; then
  echo "Source inventory is truncated" >&2
  exit 1
fi
if [[ "$(jq -r '.IsTruncated // false' "$target_inventory")" == true || "$(jq -r '.NextContinuationToken // empty' "$target_inventory")" != "" ]]; then
  echo "Target inventory is truncated" >&2
  exit 1
fi

jq -n --slurpfile source "$source_inventory" --slurpfile target "$target_inventory" '
  ($target[0].Contents // [] | map({key: .Key, value: {Size: .Size, ETag: .ETag}}) | from_entries) as $targetByKey
  | [($source[0].Contents // [])[]
      | .Key as $key
      | select(($targetByKey[$key] == null)
          or ($targetByKey[$key].Size != .Size)
          or ($targetByKey[$key].ETag != .ETag))
      | {Key: .Key, SourceSize: .Size, SourceETag: .ETag,
         TargetSize: ($targetByKey[$key].Size // null),
         TargetETag: ($targetByKey[$key].ETag // null)}]
' > "$mismatches"
chmod 600 "$mismatches"
chown silverocean:silverocean "$mismatches"

source_count="$(jq '.Contents | length' "$source_inventory")"
source_bytes="$(jq '[.Contents[]?.Size] | add // 0' "$source_inventory")"
target_count="$(jq '.Contents | length' "$target_inventory")"
mismatch_count="$(jq 'length' "$mismatches")"

echo "source_count=$source_count"
echo "source_bytes=$source_bytes"
echo "target_count=$target_count"
echo "mismatch_count=$mismatch_count"

if [[ "$mismatch_count" -ne 0 ]]; then
  echo "CUTOVER_INVENTORY_MISMATCH" >&2
  exit 1
fi

echo "CUTOVER_INVENTORY_VERIFIED"
