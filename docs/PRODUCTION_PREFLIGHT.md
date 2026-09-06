# SlickHood production preflight

This runbook applies to the integrated release at Flyway V71. It adds a blocking,
secret-safe host preflight; it does not replace backups, staging journeys or the
backend-before-frontend deployment order.

## Approved production identity

- Public application: `https://app.slickhood.com`
- Backend readiness: `https://app.slickhood.com/api/actuator/health/production-readiness`
- S3 bucket: `slickhood-production-documents-603455138904-ca-central-1-an`
- AWS region: `ca-central-1`
- Required readiness scope: `wealth,insurance,affiliate,services,soko,helpdesk`
- Required Flyway version: V71 with zero failed rows

Do not place a secret in this document, a command line, GitHub Actions output,
Git history, or a world-readable host file.

## 1. Remove temporary recovery permission

Delete the inline IAM policy `temporary-permission` from IAM user
`slickhood-storage-prod`. It granted Lightsail recovery access and is not an
application permission. The application principal must have only the S3 data
plane permissions for this bucket and `textract:DetectDocumentText`; it must not
have IAM, Lightsail, bucket-policy, public-access, or lifecycle administration.

Lightsail instances do not provide the EC2 instance-profile flow used by this
release. Keep `GARAGE_S3_USE_DEFAULT_CREDENTIALS=false` and load the scoped static
pair from a root-owned environment file. If SlickHood later moves to EC2/ECS with
an attached workload role, remove both static values and set that flag to `true`.
Never configure both sources.

The Lightsail application identity needs this minimum data-plane shape (keep the
bucket name exact). If the bucket uses a customer-managed KMS key, add only the
required encrypt/decrypt/data-key actions for that one KMS key.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SlickhoodBucket",
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation", "s3:ListBucket"],
      "Resource": "arn:aws:s3:::slickhood-production-documents-603455138904-ca-central-1-an"
    },
    {
      "Sid": "SlickhoodObjects",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::slickhood-production-documents-603455138904-ca-central-1-an/*"
    },
    {
      "Sid": "SlickhoodTextract",
      "Effect": "Allow",
      "Action": "textract:DetectDocumentText",
      "Resource": "*"
    }
  ]
}
```

## 2. Install host tools

The preflight requires `python3`, `mysql`, `curl`, `openssl`, `clamdscan`,
`systemctl`, and AWS CLI v2. Install AWS CLI v2 using AWS's signed Linux x86_64
installer. Download both the ZIP and signature and verify the PGP signature using
the current AWS signing key published in the official installation guide before
running the installer:

```bash
mkdir -m 0700 /tmp/awscli-install
cd /tmp/awscli-install
curl --proto '=https' --tlsv1.2 -fSLo awscliv2.zip https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip
curl --proto '=https' --tlsv1.2 -fSLo awscliv2.sig https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip.sig
# Import the AWS CLI public key and require this command to report a good signature.
gpg --verify awscliv2.sig awscliv2.zip
unzip -q awscliv2.zip
sudo ./aws/install --bin-dir /usr/local/bin --install-dir /usr/local/aws-cli
/usr/local/bin/aws --version
cd /
sudo rm -rf /tmp/awscli-install
```

Do not continue if signature verification fails.

## 3. Protect runtime configuration

Use the existing `silverocean` service group. Configuration containing no secret
may be `root:silverocean 0640`; secret-only files should be `root:root 0600`.

```bash
sudo chown root:silverocean /etc/slickhood/application.properties
sudo chmod 0640 /etc/slickhood/application.properties
sudo chown root:root /etc/slickhood/pms-antivirus.env /etc/slickhood/pms-ocr.env \
  /etc/slickhood/pms-release.env /etc/slickhood/pms-storage.env \
  /etc/slickhood/secrets/alpha-vantage.env /etc/slickhood/secrets/insurance-imap.env
sudo chmod 0600 /etc/slickhood/pms-antivirus.env /etc/slickhood/pms-ocr.env \
  /etc/slickhood/pms-release.env /etc/slickhood/pms-storage.env \
  /etc/slickhood/secrets/alpha-vantage.env /etc/slickhood/secrets/insurance-imap.env
sudo chown root:silverocean /home/silverocean/backend/config/application.properties
sudo chmod 0640 /home/silverocean/backend/config/application.properties
```

Confirm `pms.service` loads those files through `EnvironmentFile=` or Spring's
additional configuration, then run `sudo systemctl daemon-reload`. Never use
`systemctl show ... Environment` or shell tracing around secrets.

## 4. Required configuration contract

Set these names in the protected host configuration. Values shown below are
non-secret invariants; provider credentials must come from the production secret
store or protected environment files.

```ini
APP_PUBLIC_URL=https://app.slickhood.com
APP_CORS_ALLOWED_ORIGINS=https://app.slickhood.com

GARAGE_S3_BUCKET=slickhood-production-documents-603455138904-ca-central-1-an
GARAGE_S3_REGION=ca-central-1
GARAGE_S3_REQUIRE_HTTPS=true
GARAGE_BOOTSTRAP_ENABLED=false
GARAGE_PRESIGNER_DURATION_SECONDS=120
GARAGE_S3_USE_DEFAULT_CREDENTIALS=false
GARAGE_S3_ACCESS_KEY=<scoped-production-value>
GARAGE_S3_SECRET_KEY=<scoped-production-value>

KYC_OCR_PROVIDER=aws-textract
KYC_OCR_AWS_REGION=ca-central-1

WEALTH_MARKET_ENABLED=true
WEALTH_MARKET_ALPHA_VANTAGE_BASE_URL=https://www.alphavantage.co
WEALTH_VAULT_ANTIVIRUS_ENABLED=true
WEALTH_VAULT_ANTIVIRUS_REQUIRED=true
WEALTH_VAULT_ANTIVIRUS_HOST=127.0.0.1

INSURANCE_IMAP_ENABLED=true
INSURANCE_IMAP_SSL=true
HELPDESK_AI_ENABLED=true
HELPDESK_AI_BASE_URL=https://api.openai.com/v1
AFFILIATE_ELIGIBLE_PAYMENT_COUNT=3
```

Also provide SMTP host/user/password, Silverwood IMAP host/user/password and
sender/reply-to, Alpha Vantage key, OpenAI key, the explicit Affiliate rate,
minimum payout and hold period, and at least one authenticated Paystack or M-Pesa
callback secret. If Paystack is enabled, its API and browser callback URLs must
use HTTPS. M-Pesa and Paystack receiving-account details remain tenant/workspace
configuration; they must not be hard-coded as one global recipient.

Remove stale duplicate values from
`/home/silverocean/backend/config/application.properties`. In particular, its
canonical `app.public-url` and `app.cors.allowed-origins` values must match the two
lines above and must not retain `http://localhost:3000`. A protected environment
override currently wins on the live host, but leaving conflicting values creates
an avoidable precedence and rollback hazard.

## 5. S3 controls (operator principal, not application principal)

Confirm all four bucket Block Public Access controls, versioning, default
encryption and the TLS-only bucket policy. Lifecycle rules should retain current
versions according to the business retention policy, abort incomplete multipart
uploads, and transition or expire only noncurrent versions after the approved
recovery period. Do not expire active KYC, lease, insurance, payment, or audit
evidence merely to reduce storage cost.

Run the read-only controls with an AWS administrator/operator identity:

```bash
aws s3api get-public-access-block --bucket slickhood-production-documents-603455138904-ca-central-1-an --region ca-central-1
aws s3api get-bucket-versioning --bucket slickhood-production-documents-603455138904-ca-central-1-an --region ca-central-1
aws s3api get-bucket-encryption --bucket slickhood-production-documents-603455138904-ca-central-1-an --region ca-central-1
aws s3api get-bucket-policy-status --bucket slickhood-production-documents-603455138904-ca-central-1-an --region ca-central-1
aws s3api get-bucket-lifecycle-configuration --bucket slickhood-production-documents-603455138904-ca-central-1-an --region ca-central-1
```

All four public-access booleans must be `true`, versioning must be `Enabled`,
encryption must be present, and `PolicyStatus.IsPublic` must be `false`.

## 6. Run the blocking preflight

The backend workflow copies `production-preflight.py` with the signed release
artifact and verifies both checksums. Run it before replacing the JAR and again
after the new backend is healthy:

```bash
sudo python3 /tmp/slickhood-backend-release/production-preflight.py \
  --readiness-url https://app.slickhood.com/api/actuator/health/production-readiness \
  --public-origin https://app.slickhood.com \
  --expected-flyway-version 71 \
  --expected-bucket slickhood-production-documents-603455138904-ca-central-1-an \
  --expected-region ca-central-1
```

The script prints only check names and PASS/FAIL summaries. Its Textract test
creates a random object under `preflight/`, calls `DetectDocumentText`, and deletes
the object in a `finally` cleanup. A cleanup failure blocks deployment.

Expected final line:

```text
READY: all production preflight checks passed.
```

## 7. GitHub production environment

Protect the production environment with required reviewers and store these as
environment secrets:

- Backend: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `BACKEND_HEALTHCHECK_URL`.
- Frontend: `REMOTE_HOST`, `REMOTE_USER`, `SSH_PRIVATE_KEY`, `ENV_PRODUCTION`,
  `BACKEND_HEALTHCHECK_URL`.

`BACKEND_HEALTHCHECK_URL` must be the exact readiness URL above. The frontend
environment must set the exact HTTPS API/site URLs, valid Google OAuth and Maps
browser credentials, and `NEXT_PUBLIC_COMMIT_HASH` equal to the immutable release
commit. Public `NEXT_PUBLIC_*` values are not secret; provider secret keys must
never use that prefix.

## 8. Deployment and rollback gate

1. Record backend/frontend commit hashes and artifact SHA-256 values.
2. Back up the database and current backend/frontend artifacts; verify restores.
3. Run the host preflight against the current release.
4. Deploy backend, require Flyway V71, readiness `UP`, deployed hash, and the
   second host preflight.
5. Complete authenticated staging smoke tests before frontend promotion.
6. Build and deploy the frontend only after the backend passes.
7. Repeat login, registration, KYC upload, tenant/landlord isolation, payment,
   Help Desk, dashboard and report smoke journeys through the public origin.
8. Roll back frontend first for a frontend failure. For a backend failure, restore
   the prior JAR and restart it; do not reverse a successful forward-only migration
   or delete data. Prefer a forward fix after new-version data has been written.

Any failed preflight, provider authentication failure, stale ClamAV definitions,
Flyway failure, incorrect CORS response, secret exposure, or missing backup stops
the release.
