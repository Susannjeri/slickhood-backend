# Pending audit follow-up — 15 September 2026

This document supplements the Marketplace, Wealth, Notification and Reporting audits from this date. Earlier audit results describe earlier snapshots; the changes below are local follow-up changes, not evidence of a production deployment.

The later [notification completion follow-up](notification-completion-2026-09-15.md) supersedes notification items 3 and 7 below except historical reminder rollout, and records the new V86 migration and receipt-reconciliation changes. This document's earlier test results and no-new-migration statement remain historical, not the latest release certificate.

## Confirmed policy and implemented corrections

- The customer explicitly chose **verified, registered SlickHood riders for every new delivery**. New dispatch rejects a missing rider ID and requires an active, available, verified rider linked to a user, with the existing KYC checks retained. The merchant picker applies the same eligibility rules. Historical one-off courier records are preserved; their existing completion path is not retroactively removed.
- Rider pages are account-keyed and refresh on focus, visibility and a timer. Phone-friendly controls, rear-camera proof input, a 5 MB photo limit and an in-page failed-delivery/return reason form replace browser prompts. Buyer codes remain hidden from riders until buyers provide them.
- Dispatch estimates now display Africa/Nairobi time, matching backend interpretation. The form permits nearer estimates instead of forcing a four-hour minimum; the backend still rejects past arrival times.
- Soko catalogue, shop and merchant views hydrate variation stock from authoritative active variation rows. No inventory quantity is changed by this display correction, and locked checkout calculations remain intact.
- Provider referees reject malformed, duplicate-normalized and self-referential contacts. Add/edit use a provider-profile write lock for duplicate prevention. Name/contact limits now match frontend and backend. Inactive or uncorrected rejected referees cannot be confirmed. Approved referees remain protected from customer edits/removal. This is not certification of every concurrent referee review/edit race.
- Personal notification queries, unread counts, mark-read updates and decryption guards exclude OTP and buyer delivery-code/recovery email records. Dedicated buyer-only code/recovery workflows are retained. Corrupt ciphertext gives a safe placeholder without failing the entire feed.
- Mark-read and positive delivery confirmations are atomic, independent updates. Reading a notification cannot overwrite retry/provider state; positive delivery confirmation cannot erase its read timestamp. Positive confirmation is monotonic.
- SMS retry candidates/claims exclude notifications with an active nonblank provider receipt. This prevents resending provider-accepted messages while awaiting delivery receipts. It conservatively also suppresses retry where a saved receipt reports failure: provider-specific reconciliation and ambiguous network timeouts still require follow-up. Individual SMS receipt-state concurrency and a durable acceptance/reconciliation lifecycle are not claimed fully corrected.
- The bell distinguishes unknown/loading/failed counts from zero and remounts for account/role changes. The personal inbox refreshes without losing its page, preserves confirmed read state and cancels stale account requests.
- Wealth goal edits fetch an owner-scoped original-currency detail response; converted dashboard amounts are not reused for writes. Existing view/manage authorities remain separate. Reporting navigation/export/envelope fixes from the Reporting audit are retained, without widening financial access.

## Database and configuration

No new migration is required for the notification, stock-display, referee-validation or goal-detail changes in this follow-up. Existing pending Marketplace V84/V85 migrations remain part of the wider bundle and require full-schema staging validation. Production databases, credentials, provider configuration and reminder flags were not changed.

The notification repository integration test is opt-in and accepts only `jdbc:mysql://127.0.0.1:3416/notification_audit`. It creates/drops its own notification/SMS tables in an isolated MySQL fixture. No production data is involved. Three checks passed: code exclusion and recipient isolation, concurrent read/delivery updates, and receipt-backed retry suppression.

## Verification

- Full backend regression: 950 tests across 172 suites, 949 passed, one intentionally skipped context test, zero failures/errors. Counts use this run's results and exclude the separately run MySQL integration XML.
- Isolated MySQL repository integration: three tests passed, no failures/errors/skips. The local fixture server was shut down gracefully afterwards; files were retained.
- Fresh frontend production-mode local build: compilation, TypeScript and all 103 pages passed. The first invocation correctly failed the missing API URL guard; it was rerun with an explicit loopback API URL, without changing or bypassing the guard. This is not a configured production artifact.
- Full lint: zero errors, 473 warnings within the existing 478-warning budget. Existing warning debt remains; the budget was not increased.
- Final Chromium regression: 74 passed, zero failures, against the freshly built production-mode local frontend (2.4 minutes). Coverage includes verified-only merchant dispatch/Nairobi estimates, phone camera and failed-delivery forms, original-currency goal edits, KYC reuse, private rider evidence/admin review, service resumption/referees, Marketplace payment UI, notification count recovery/pagination, reports/CSV failures, Wealth navigation and team access/shared roles. Browser fixtures mock APIs; a passing browser test is not a real-provider or production-permission end-to-end certificate.
- Git whitespace checks passed in both worktrees (line-ending warnings only). No source files changed after the tested frontend build.

## Still blocked or unresolved

1. Subsequently approved scope-only financial-report isolation is implemented locally; see [the financial-report follow-up](financial-report-workspace-scope-2026-09-15.md) for final verification and release checks. No new role grants are introduced.
2. Authorised/provider-confirmed full Soko refund/reversal state cleanup remains approval-gated after the earlier wider payment/stock/delivery proposal was rejected. Orders can otherwise retain inconsistent paid/fulfillable and delivery/rider state. No new automatic refund or payout API calls are authorised.
3. Generic rider verification/action alerts via email/in-app remain approval-gated; documents, identity details and review notes must not be included.
4. Rider-initiated WhatsApp links would expose the assigned buyer's phone and order reference to WhatsApp; Directions links would expose the delivery address to Google Maps. A proposed rewrite was rejected for lack of explicit destination/data approval. The safe implementation contains neither link. A separate customer question requests approval; nothing is sent automatically.
5. Returned perishable groceries must not automatically become saleable. Retry/reassignment and safe stock-disposition policy require a business decision.
6. Independent service-listing descriptions/images and the complete arbitrary custom service-matrix upload/review mapping remain incomplete. The catalogue cleanup in this follow-up does not claim those features.
7. Notification event-versus-channel counting, lifecycle-aware invitation actions, a complete durable stakeholder/event matrix and historical reminder rollout still need completion/decisions.
8. Sold/closed Wealth holding accounting semantics are unchanged. Actual scanning, private storage, currency/quote providers and authenticated staging validation are required before release.

## Release status

**Not deployed. Not ready for whole-system end-to-end completion sign-off.** The verified-rider policy is implemented locally; the remaining security/payment approvals and staging checks must be resolved before publishing this combined batch.
