# Security, acceptance, performance and recovery remediation

Date: 2026-09-16

## Corrections completed

- Browser-session cookie mutations now require JSON, a private CSRF header and a
  same-origin request. Production also requires HTTPS. The comparison uses the
  forwarded/host boundary rather than the application server's internal URL so
  it remains correct behind the production reverse proxy. Set
  `BROWSER_SESSION_ORIGIN=https://app.slickhood.com` to pin the public origin.
- Service-category changes carry an optimistic version. Edit, deactivate and
  reactivate reject stale clients; a simultaneous database race returns HTTP
  409 with an instruction to refresh and review.
- Affiliate directory referral/conversion totals are loaded by one grouped
  page query. A 100-row regression proves query volume stays constant: one
  directory repository call, one identity batch and one grouped count instead
  of two referral-count calls per affiliate.
- Payment callback timing/outcome and rejected-signature meters were added.
  Existing outbox meters plus Actuator's datasource and JVM/GC meters are now
  available through the authenticated metrics endpoint.
- CI enforces the current modular-monolith boundaries and the frontend blocks
  production dependency advisories at moderate severity or higher.
- Paystack regression coverage now explicitly includes successful, pending,
  failed, duplicate-callback and refund/reversal-notice handling. Refunds and
  reversals remain records for the authorised manual finance operation; an
  unsolicited provider notice cannot silently mutate the ledger.

## Reproducible isolated fixture

Use the reserved `@qa.slickhood.test` identities and the existing controlled
OTP policy. Enable it only in the isolated fixture:

```text
SECURITY_TEST_OTP_ENABLED=true
SECURITY_TEST_OTP_ACCOUNTS=owner-a@qa.slickhood.test=<staged-six-digits>,payer-a@qa.slickhood.test=<staged-six-digits>,owner-b@qa.slickhood.test=<staged-six-digits>,payer-b@qa.slickhood.test=<staged-six-digits>
```

Create Workspace A and Workspace B through the real API/UI once, then retain
the disposable fixture database between runs. Never point this profile at a
customer database. Store account passwords and Paystack test keys in the
fixture host's secret environment, not source control.

Required acceptance evidence for each workspace is:

1. real login and workspace selection;
2. create/update operation and read it back after a new authenticated request;
3. verify the intended recipient receives an in-app notification and the other
   workspace cannot read either record;
4. initialise Paystack with `sk_test_*`, then exercise success, pending, failure
   and signed duplicate callbacks;
5. record refund/reversal through the authorised finance workflow and verify
   invoice, ledger, receipt and audit history agree.

The isolated performance runner is `scripts/authenticated-load-test.py`. It
accepts only a staged synthetic bearer token and refuses SlickHood production
hostnames. Run normal, peak, soak and recovery profiles and retain the JSON
outputs with Actuator metrics snapshots.

The restore runner is `scripts/rehearse-backup-restore.py`. It accepts only
loopback MySQL and a new database whose name begins
`slickhood_restore_rehearsal_`; it refuses to overwrite a database and retains
the restored database for explicit acceptance checks.

## Verification performed

- Backend focused regression: 48 passed, zero failed or skipped. The
  affiliate directory was then retested separately with the new 100-row
  constant-query-volume case: 10 passed, zero failed or skipped.
- Frontend changed-file lint: zero errors (four pre-existing hook/navigation
  warnings in touched legacy components).
- Browser session and affected login/invitation retest: 6 passed.
- Frontend dependency audit after the compatible lockfile patch: zero known
  vulnerabilities, including development dependencies.
- Maven parent triage found no newer stable Spring Boot 3.x parent; the only
  parent offered was the incompatible 4.2.0 milestone, so it was not applied.
- Architecture boundary check: passed.
- Safety checks: load runner refused `app.slickhood.com`; restore runner refused
  an invalid database name; all Python runners compiled successfully.

## Outstanding execution gates

No equivalent isolated full stack or Paystack sandbox credentials were
available during this change. Consequently, the real two-workspace journey,
provider sandbox callback delivery, authenticated normal/peak/soak/recovery
measurements and actual backup restoration have not been claimed as passed.
The tooling and fail-closed fixture controls are ready, but these four items are
release evidence gates.

Monetary normalization was deliberately not changed globally. SlickHood needs
an approved currency minor-unit/rounding policy before replacing legacy
`double` fields; assuming two decimals for every currency can change settlements.

## Production-readiness status

The corrected code is suitable for CI/release-candidate validation. A peak
capacity or disaster-recovery sign-off is **not established** until the
isolated runner and restore rehearsal produce retained evidence. Do not run the
load runner against production.
