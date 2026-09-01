# SlickHood production S3 cutover

Bucket: `slickhood-production-documents-603455138904-ca-central-1-an`

Region: `ca-central-1`

Bucket-owner account: `603455138904`

Runtime account: `603455138904`

Amazon Lightsail exposes an AWS-managed internal instance identity but does not
provide a customer-managed EC2 instance profile on this instance. Use a
dedicated `slickhood-storage-prod` IAM user with only the runtime policy in this
directory. Store its access key only in the root-readable production secret
file; never commit it or place it in the systemd unit.

## Mandatory release gates

1. Resolve the bucket with `GetBucketLocation` from the production instance.
   The bucket and customer workload are in account `603455138904`. Do not grant
   access to the AWS-managed Lightsail internal role from another account.
2. Apply and read back Block Public Access, bucket-owner-enforced ownership,
   SSE-S3 encryption, versioning, lifecycle rules and the HTTPS-only bucket
   policy.
3. Prove the runtime role can list the bucket, upload a harmless test object,
   read it, presign it and delete it. The role must not receive
   `s3:ListAllMyBuckets` or bucket-policy administration permissions.
4. Keep the existing Garage store online and unchanged.
5. Inventory every source object and every database object reference.
6. Bulk-copy all source objects with their existing keys and server-side
   encryption. Generate a SHA-256 manifest.
7. Enter a short upload-maintenance window, repeat the inventory, copy the
   delta, and verify every destination object byte-for-byte against the source.
8. Verify all database references exist in S3. Object keys are unchanged, so
   database values must not be rewritten.
9. Deploy the backend with IAM-role credentials, native regional S3 HTTPS,
   two-minute presigned URLs, fail-closed ClamAV scanning and production
   HTTPS-only CORS.
10. Test clean upload/download, EICAR rejection before storage, expired-link
    denial, cross-tenant denial, application health and rollback.
11. End the maintenance window only after all gates pass. Retain Garage and
    the local checksum manifests during the observation period. Do not delete
    local documents as part of this release.

## Runtime configuration

Set the dedicated storage user's key only through the root-readable production
secret file. Do not hard-code or commit either value.

```ini
GARAGE_S3_BUCKET=slickhood-production-documents-603455138904-ca-central-1-an
GARAGE_S3_REGION=ca-central-1
GARAGE_S3_PATH_STYLE=false
GARAGE_S3_REQUIRE_HTTPS=true
GARAGE_PRESIGNER_DURATION_SECONDS=120
GARAGE_BOOTSTRAP_ENABLED=false
ANTIVIRUS_ENABLED=true
ANTIVIRUS_REQUIRED=true
ANTIVIRUS_HOST=127.0.0.1
ANTIVIRUS_PORT=3310
ANTIVIRUS_CONNECT_TIMEOUT=PT2S
ANTIVIRUS_READ_TIMEOUT=PT30S
APP_CORS_REQUIRE_HTTPS=true
APP_CORS_ALLOWED_ORIGINS=https://app.slickhood.com
```

`GARAGE_S3_URL` and `GARAGE_PRESIGNER_URL` must be absent for native AWS S3.
`GARAGE_ACCESS_KEY` and `GARAGE_SECRET_KEY` must come only from the protected
runtime secret file for this Lightsail deployment.

## Rollback

If any post-cutover smoke test fails, stop writes, restore the previous backend
artifact and legacy Garage environment, restart the application, and repeat the
health and reference checks. Do not reverse S3 versioning, delete migrated S3
objects, delete source Garage objects, or edit database references during an
emergency rollback.
