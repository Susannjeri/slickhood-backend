# Rental, property sales and estate agreement release

This additive patch packages the three 7 September journey audits on backend
37c005d and frontend ea2f1e1. Both bases include the fetched protected main history.
No migrations, recipient payment configuration changes or customer data fixes are
included. Preserve existing S3, antivirus, OCR, CORS and active-role protections.

## Scope and evidence

- Rental invitations, governed lease document/history, frozen charges and signing/occupancy guards.
- Property sale offer status, protected offer PDF, acceptance response privacy and escrow beneficiary checks.
- Estate ownership-specific agreements, protected history, signing eligibility and resilient document/template loading.
- The frontend build guard now loads production environment files before checking them, matching Next.js. Missing or unsafe configuration remains blocking.
- Combined audit: 675 unit and 5 integration passes, 126 browser passes; final focused 21/21. Existing skips and live-acceptance boundaries are recorded in the three audit reports.

## Deployment gates

1. Record immutable release hashes; publish only reviewed changes. Rebuild with those hashes, never deploy the earlier loopback frontend verification artifact.
2. Retain current backend/frontend artifacts and make a root-only database backup with verified archive integrity. Do not remove old backups to make space implicitly.
3. Require production-preflight.py success with Flyway at least V72, no failed records, valid providers, protected configuration, S3/Textract and ClamAV.
4. Deploy backend first, retain automatic rollback until readiness, exact deployed hash and protected endpoint checks pass.
5. Build frontend with the existing restricted Maps key and Google OAuth client, app.slickhood.com/api and slickhood.com site URL. Require clean install, lint/build and complete browser tests; package only the resulting verified artifact.
6. Smoke candidate before cutover. Retain previous frontend directory and roll back on startup/public checks failure. Confirm compiled SHA, API origin and private PDF/CSP contract after cutover.
7. Record actual outcomes and backup locations outside the immutable source package. Live invited-user mail receipt, both-party signing and payee-specific provider settlement must not be described as passed solely from mocked browser tests.

The host preflight passed before packaging on 7 September. Live backend remained
37c005d at that point. This document alone is not evidence of deployment.
