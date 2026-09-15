# Notification completion — 15 September 2026

This follow-up supersedes the notification gaps in the earlier audits. Changes are local and preserve the unrelated pending Marketplace, Reporting, Wealth and team-role work. Nothing has been committed, pushed, deployed or applied to production during this follow-up.

## Problems corrected

- Security/verification payloads no longer appear in the normal personal list, unread count or mark-read response. OTPs, buyer delivery codes and delivery-recovery challenges remain in their existing dedicated workflows.
- Personal access is recipient-bound. Phone-addressed records are included only for a verified account phone. Global delivery metadata requires both Superadmin role and the existing delivery-monitor authority; possession of a customer notification permission does not grant cross-account access. Responses use `Cache-Control: no-store`.
- Read receipts and confirmed delivery updates are independent atomic operations. Confirmed TextSMS/Africa's Talking delivery cannot regress after a late failure receipt. Unknown, wrong-provider, ambiguous and malformed receipt IDs are ignored. The existing serialized WhatsApp receipt workflow is preserved.
- Saved provider receipt IDs exclude accepted SMS from resend eligibility. TextSMS receipt checks now persist their next-check time and attempt count, use an atomic five-minute claim, resume after a restart and stop after a bounded number of checks. Exhaustion records a manual-reconciliation status, not another send. Historical receipt rows are not automatically backfilled.
- New paired email/in-app records share a business-event key and count once in the personal feed. The in-app copy is canonical when present. Legacy records remain intact; no historical deduplication guesses or deletion were performed. Admin projections use the latest active SMS receipt so attempts do not duplicate rows/counts.
- Property and internal-team invitations now use the same paired-record path. Explicit invitation resends retain their existing business behaviour. Opening an inbox does not consume an invitation. Actions are resolved against the current invite, recipient and expiry; cancelled, accepted, expired or unavailable invitations retain history but no invitation action.
- The shared bell distinguishes an unknown/error count from zero and invalidates stale account requests. The inbox refreshes on focus/timer without losing pagination or confirmed read state. Server action metadata takes precedence over legacy message parsing. Links must be safe internal destinations; private details are opened under existing destination permissions.
- Marketplace status changes publish generic recipient-specific business alerts through the existing transactional domain-event outbox. Durable notification delivery keys protect handler replay, including the commit-to-processed gap. Verification documents, review notes, buyer codes and delivery-failure reasons are not mirrored into these generic messages.
- Delivery-code notification queue errors now propagate to the existing transactional caller instead of being logged and swallowed. An isolated regression first reproduced the swallowed exception. The approved correction removes only that helper's catch; it does not change buyer selection, code generation, payment, stock, rider or account authorization rules. Successful enqueue is not proof of successful email delivery.
- Existing insurance emails now have generic in-app counterparts. Renewal stages and selected-quote payment reminders use stable keys. Estate reminder delivery has replay-safe paired keys; existing rental/sale reminder balance checks and handler locking remain intact.
- Authorized manual refund/receiving-payment record changes queue generic alerts without new refund/payout API calls or financial-state authorization changes. Receiving-payment/settlement records are provider-only; buyers and riders do not receive provider settlement details.

## Recipient/event wiring

| Event | Recipients | Secure destination |
| --- | --- | --- |
| Soko checkout awaiting payment, verified payment, preparation/pickup, cancellation or reservation expiry | Buyer and merchant owner | Soko order workspace |
| Delivery assignment, acceptance, collection, completion, failure or return | Buyer, merchant owner and assigned registered rider; duplicate identities receive one alert | Buyer/merchant Soko workspace; rider delivery workspace |
| Provider-confirmed refund or reversal | Buyer, merchant owner and assigned rider through the existing order notification path; generic status only | Their authorized order/delivery workspace |
| Authorized manual refund record | Buyer and merchant/service provider owner | Authorized order/booking workspace |
| Authorized receiving-payment/settlement record | Merchant/service provider owner only | Provider order/booking workspace |
| Rider supplemental credential submitted | Registered rider and active Superadmins | Rider checklist; administrator verification queue |
| Supplemental credential reviewed | Registered rider only; generic next-step message | Private rider checklist |
| Rider activated, rejected or suspended | Rider and merchant owner; no confidential review notes | Their rider/merchant workspaces |
| Service booking created, confirmed, started, completed, cancelled or verified paid | Customer and active provider owner | Authorized services/booking workspace |
| Soko/service listing moderation | Listing owner | Listing workspace |
| Insurance case/quote/payment/policy update | Existing intended email recipient, mirrored only for an active matching account | Insurance workspace |
| Insurance renewal/selected-quote payment reminder | Policy/customer recipient | Insurance policy/payment workspace |
| Property/internal-team invite | Invited email; in-app only for an active matching account | Current recipient-bound invitation |
| Rental/sale arrears, estate overdue, late fee and lease/ownership notices | Existing customer/biller/contract-party recipients, unchanged | Existing billing/document workspace |

The table describes implemented local paths, not a claim that every provider delivered every message. Rider alerts contain generic status and safe internal destinations, not buyer phone/address links to external applications. No WhatsApp or Maps sharing was introduced.

## Database and configuration

Additive pending migration `V86__notification_business_events.sql` adds:

- Nullable notification `businessEventKey`, `deliveryKey` and `actionPath`; a unique delivery-key index and an event/recipient/channel lookup index.
- SMS `receiptCheckAttempts` (default zero), nullable `nextReceiptCheckAt` and a due-receipt index.

Existing pending V84/V85 changes belong to the wider bundle and require full-schema staging validation. V86 has not been applied to production. Existing messages, SMS attempts and encrypted payloads are preserved.

TextSMS receipt settings introduced:

- `notifications.textsms-receipt-checks-enabled`: defaults to `true`; only rows explicitly queued with a due check are processed.
- `notifications.textsms-receipt-max-attempts`: defaults to 12, bounded to 1–24.
- `notifications.textsms-receipt-scan-ms`: defaults to 60000; individual claims delay the next attempt five minutes.

Existing TextSMS endpoint/partner/key configuration is reused. No secrets, phone destinations or provider IDs are logged by the reconciler. No production provider configuration or arrears reminder flags were changed.

## Verification

- Fresh local production-mode frontend build: compilation, TypeScript and all 103 pages passed with an explicit loopback API URL. This is not a production-configured artifact.
- Frontend lint: zero errors; 473 warnings within the unchanged 478-warning budget.
- Browser regression archive: 77 expected passes, zero unexpected failures, flaky tests or skips. Twelve are notification-specific. The wider selection includes Marketplace payment/governance/rider flows, phone delivery controls, Wealth, reports and team access. APIs are mocked; no real emails, payments or SMS were sent.
- Final full backend regression: 983 tests across 177 suites; 982 passed, one intentionally skipped application-context test, zero failures or errors. All result XML belongs to the final source-frozen run; stale separately executed integration reports were excluded. The existing Wealth two-second performance threshold was not relaxed and passed in the final suite.
- Isolated MySQL repository integration: six tests passed, zero failures/errors/skips. Checks cover personal code/recipient isolation, concurrent atomic read/delivery updates, accepted-receipt resend suppression, canonical paired-alert counting/read state, latest active receipt projections and exclusive receipt-check claims. The fixture accepts only `jdbc:mysql://127.0.0.1:3416/notification_audit`; it creates/drops its own notification/SMS tables. This validates the repository against MySQL, not the complete production Flyway chain.
- Git whitespace checks passed in both worktrees under their normal line-ending configuration (CRLF conversion warnings only). No application source changed after the final backend compilation or tested frontend build. Earlier interim/partially compiled runs are not a final certificate.

The isolated MySQL fixture was verified by its exact workspace data directory and loopback port, then shut down gracefully after the passing tests. Fixture files were retained; no production data or user files were removed.

## Release checks and remaining boundaries

1. Validate V84/V85/V86 against the complete staging schema and test real authenticated buyer, merchant, rider, biller and Superadmin accounts. Local mocks do not prove production role provisioning or access to every destination.
2. Verify actual SMTP/provider delivery, TextSMS response/receipt contracts, callbacks and callback security in staging. SMTP acceptance is not delivery to the customer's mailbox. Transport timeouts where a provider accepted a send but the receipt ID was never persisted still require controlled manual/provider reconciliation; exactly-once external delivery is not claimed.
3. Review existing historical overdue invoices and effective reminder flags before enabling a production backlog. No retrospective blast or SMS receipt backfill has been authorized or executed.
4. Strict selected-workspace financial-report isolation was subsequently approved and implemented locally without new role grants; see [the financial-report follow-up](financial-report-workspace-scope-2026-09-15.md) for verification and release checks. Complete Soko refund/reversal stock/rider state cleanup remains approval-gated. Notification wiring does not authorise those payment changes. Grocery return disposition and service catalogue/matrix gaps remain wider-scope work.
5. No deployment was performed. Whole-system production readiness remains conditional on these checks; passing local tests alone is not end-to-end production sign-off.
