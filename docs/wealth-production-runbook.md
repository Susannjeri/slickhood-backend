# Wealth production runbook

## Required production configuration

Set these as secrets or deployment environment variables. Do not commit their values.

```text
WEALTH_MARKET_ENABLED=true
WEALTH_MARKET_ALPHA_VANTAGE_API_KEY=<licensed provider key>
WEALTH_MARKET_ALPHA_VANTAGE_FRESHNESS=EOD
WEALTH_MARKET_MINIMUM_REFRESH=PT15M
WEALTH_MARKET_SCHEDULER_DELAY_MS=900000
WEALTH_MARKET_BATCH_SIZE=50
WEALTH_VAULT_ANTIVIRUS_ENABLED=true
WEALTH_VAULT_ANTIVIRUS_REQUIRED=true
WEALTH_VAULT_ANTIVIRUS_HOST=<private ClamAV service name>
WEALTH_VAULT_ANTIVIRUS_PORT=3310
WEALTH_VAULT_ANTIVIRUS_TIMEOUT_MS=5000
```

Confirm the object-storage bucket enforces server-side encryption, blocks public access, versions objects, and applies the approved backup and retention policy.

## Deployment order

1. Take a restorable database backup and record the currently deployed backend/frontend commit hashes.
2. Deploy the backend. Flyway must apply `V47__personal_wealth_advisor.sql` successfully before any frontend promotion.
3. Confirm application health, `GET /wealth/market/status`, object storage, ClamAV connectivity, and that a clean test PDF can be uploaded.
4. Confirm an EICAR test file is rejected and never appears in object storage.
5. Deploy the frontend and run the smoke matrix below.
6. Observe quote failures, scheduler failures, upload failures, database latency and dashboard latency through one complete scheduler interval before promotion.

## Smoke and isolation matrix

- Create and update every asset type; verify history is retained on update and archive.
- Create a market asset, refresh it twice inside the minimum interval, and confirm only one provider request is made.
- Disable the provider and confirm the last known value remains unchanged and status becomes stale/unavailable.
- Upload personal will and trust documents; upload a document linked to an asset; verify both lists and downloads.
- As a second user, attempt direct access to the first user's asset, valuation and vault IDs; every request must return not-found/denied without metadata leakage.
- Verify dashboard totals, currency conversion, goals, deadlines, advisor actions and a portfolio with at least 10,000 assets.
- Verify mobile and desktop layouts and keyboard-only document upload.

## Rollback

Roll back application binaries to the recorded commits. V47 is additive and old binaries ignore its new tables and columns, so the database migration should normally remain in place. Do not drop V47 columns during an incident. Restore the pre-deployment backup only when data integrity is compromised and after preserving incident evidence.
