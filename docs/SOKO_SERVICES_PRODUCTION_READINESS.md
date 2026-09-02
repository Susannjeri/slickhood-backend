# Soko and Services production readiness

## Decision

The code is a production release candidate only after every environment gate below passes. Production deployment is intentionally performed by the release operator, not by this development worktree.

## Second-pass audit findings closed

- Soko checkout now requires a customer-scoped idempotency key. A network retry returns the original order and cannot reserve stock or create an invoice twice.
- Six-digit delivery codes are encrypted with the platform key service at rest, excluded from API serialization, compared in constant time, and erased after successful confirmation. Legacy plaintext codes remain readable only during migration.
- Delivery proof is immutable once uploaded, limited to validated JPEG/PNG content and 5 MB, stored under a server-generated key, and available only to the customer or owning merchant through a short-lived URL.
- Catalogue, order, booking, directory and administrative pages have a server-side maximum of 100 records. Reservation expiry remains a 100-order batch.
- Reservation expiry and payment callback order reads use write locks. Stock mutation already uses product row locks.
- Soko order pages bulk-load items and stores. Services booking pages bulk-load service, provider and customer data instead of performing per-row queries.
- Late Services payments cannot reopen a cancelled job; they record the payment and request a refund.
- Empty route-permission lists now mean “authenticated access” instead of denying every user. This repairs direct Soko navigation.
- Browser prompts and alerts were replaced with contextual, keyboard-accessible forms for payment, cancellation, ratings, completion evidence, courier dispatch, proof upload and delivery-code display.
- Services booking notes are capped at 2,000 characters. Existing category-governed verification requirements remain configurable; no blanket document burden was added.

## Lifecycle covered

### Soko

Catalogue -> cart -> idempotent checkout -> merchant payment -> paid callback -> merchant confirmation -> packing -> pickup readiness or delivery dispatch -> proof -> customer code -> completion -> refund/settlement controls.

### Services

Verified listing -> directory search -> booking request -> provider acceptance and invoice -> payment callback -> work start -> completion evidence -> rating/complaint -> cancellation/refund -> settlement controls.

## Deployment sequence

1. Take a verified database snapshot and deploy the backend commit.
2. Run Flyway through `V50__soko_services_release_guardrails.sql`; verify the unique checkout key and reservation index exist.
3. Verify the production encryption key service can encrypt and decrypt a disposable value. Do not dispatch deliveries if this fails.
4. Verify object storage upload, server-side encryption, short-lived download URL generation and lifecycle retention for both product and delivery-proof prefixes.
5. Deploy the frontend commit and invalidate CDN/application caches so the new route guard and workflows are served.
6. In staging, run one complete Soko delivery and one pickup, including duplicate checkout submission, payment callback replay, proof validation, wrong-code lockout and settlement.
7. Run one complete Services booking, plus cancellation before payment, a simulated late callback, refund confirmation, rating and complaint review.
8. Confirm durable notification queues, payment webhook signature validation, dead-letter monitoring, database/HTTP latency dashboards and object-storage alarms.
9. Promote gradually and monitor checkout conflicts, reservation backlog, webhook failures, proof-upload failures, p95 response time and refund backlog.

## Release guardrails

- Do not bypass Flyway or manually edit migration checksums.
- Deploy the backend before the frontend begins sending `Idempotency-Key`; the header remains optional during the rolling-deployment window for backward compatibility.
- Reject promotion if a callback is not idempotent, stock becomes negative, an unrelated user can access an order/booking/proof, or an internal storage reference appears in an API response.
- Roll back application binaries independently if needed; retain the additive V50 columns and indexes. They are backward-compatible with the prior binaries.
- The merchant-managed delivery boundary remains: merchant riders, preferred delivery companies and one-off couriers. Live GPS, route optimisation and a Slickhood-operated logistics network are out of scope.

## Environment evidence still required

- Flyway V50 against a production-like MySQL clone.
- Real payment provider callback/replay tests.
- Real object storage and encryption-key integration.
- Real email/SMS delivery and retry/dead-letter checks.
- Staging load test at expected peak catalogue, checkout, order-history and booking-history concurrency.
