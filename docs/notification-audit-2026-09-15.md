# Notification audit — 15 September 2026

This initial audit is historical. Subsequent code-exclusion, atomic-update, SMS retry and inbox fixes are recorded in [pending audit follow-up](pending-audit-completion-2026-09-15.md). No production deployment is implied.

For the latest notification wiring, lifecycle actions, canonical unread counting, V86 schema changes and verification, see [notification completion](notification-completion-2026-09-15.md). Findings below describe the original inspected snapshot, not the corrected local implementation.

## Scope and result

Reviewed the current frontend and backend worktrees, including personal notifications, unread badges, delivery reporting/retries, invitations, rent/sale arrears, estate service-charge reminders, lease termination/renewal, payment receipts, insurance, and Soko delivery/recovery events.

Result: **not ready for an unconditional notification sign-off**. Existing regression checks pass, but the findings below require corrections and additional regression coverage. This audit changed no application behavior, database schema, configuration, or production deployment. Unrelated pending Marketplace and Wealth work was preserved.

## Priority findings

### 1. Security challenges are exposed through the ordinary inbox (high)

`NotificationRepo.findAllForRecipients` and `countUnreadForRecipients` include every active message type/channel for the user's email/phone. `NotificationReportService.getMyNotifications` decrypts each message; `markMyNotificationRead` also returns its decrypted body. Consequently, stored email/SMS OTPs and `SOKO_DELIVERY_RECOVERY_EMAIL` challenges are accessible through the authenticated personal-notification API.

Soko recovery deliberately requires a fresh OTP sent to the verified buyer's email before replacing the delivery code. Obtaining that OTP through the existing logged-in session defeats the additional email-possession check. Recipient restrictions still prevent an arbitrary merchant/rider from reading another user's inbox; this is a failure of the separate verification factor, not evidence of cross-account access.

Recommendation: centrally classify security/verification payloads and exclude them consistently from the personal list, count, and read endpoints. Keep actual code access restricted to its dedicated workflow, respecting expiry/use. Do not mirror verification challenges into in-app messages.

### 2. Read receipts and provider callbacks can overwrite each other's state (high)

`markMyNotificationRead` loads the notification, sets `viewedOn`, and saves the entire entity. Delivery callbacks similarly load and save the entity. Notification and its base entities have no optimistic version. Concurrent operations can therefore restore stale `delivered`, `retry`, retry-count, or `viewedOn` values. A read action must never turn a successfully delivered notification back into an eligible retry.

Recommendation: use narrowly scoped, recipient-authorized atomic updates for read receipts and monotonic delivery-state updates. Add a concurrent database integration test, not only mocks.

### 3. Accepted SMS messages awaiting delivery reports remain eligible for resend (high reliability risk)

Retry eligibility checks only active, undelivered, retry-enabled records and their age/count. `SmsDispatcherService` does not persist a separate provider-accepted state or stop automatic resends after acceptance. TextSMS saves the provider message ID and waits at least five minutes for its first delivery-report check, without marking the notification delivered. Retry-enabled messages such as payment-success SMS can therefore be resent while an earlier accepted submission is still pending, depending on the configured retry delay.

Recommendation: separate provider submission acceptance from confirmed delivery. Poll/reconcile accepted submissions by provider ID instead of resubmitting them. Retain bounded retries for confirmed retryable submission failures and explicitly handle ambiguous provider timeouts.

### 4. Badge errors and account changes can show misleading unread counts (medium)

`NotificationBell` initializes its count to zero and silently ignores count-fetch failures. It labels that state as no unread notifications, even when the count is unknown. It refreshes based on role title rather than account/session identity, and cleanup/logout does not invalidate pending requests. An old account's count can briefly overwrite a newer session's display.

Recommendation: distinguish unknown/loading/error from zero, key refreshes to the authenticated identity, and invalidate pending requests on account change/unmount.

### 5. Inbox and business-event navigation are incomplete (medium)

The badge polls and refreshes on focus; `MyNotifications` does not subscribe to those refresh triggers, so a newly increased count can coexist with an unchanged inbox. Action buttons are restricted to `IN_APP` messages. Soko and insurance business notifications are generally email-only, so they do not provide equivalent navigation from the personal feed. Invitation notifications do not carry lifecycle metadata that can hide an action after cancellation, expiry, or use; the invitation backend still validates the token.

Recommendation: refresh the inbox without disturbing pagination/read state, and generate structured, recipient-scoped business alerts with safe internal destinations and lifecycle-aware actions. Do not turn arbitrary emails or security challenges into actionable in-app notifications.

### 6. Some Soko activity has no corresponding stakeholder alert (medium)

Soko order status messages target the buyer. The inspected cancellation and payment-completion methods do not queue order alerts, and delivery/order activity does not consistently notify the merchant or assigned rider. Rider moderation informs the store owner, but does not establish a complete rider-facing notification workflow.

Recommendation: define a recipient/event matrix for merchant, buyer, rider and support; implement only authorized, non-sensitive messages with durable, deduplicated event handling. Do not email confidential KYC review notes.

### 7. Automatic rent/sale arrears reminders are deployment-gated (release check)

`RentalOverdueReminder` defaults to disabled unless `pms.receivables.reminder.enabled` or the legacy rental flag is enabled. This affects both rental and sale reminders. The estate service-charge scheduler has a separate schedule. Existing handlers/outbox tests verify duplicate suppression, balance/due-date rechecks and recipient selection, but do not establish the production flag's actual value.

Recommendation: verify effective production configuration and review the population of historical overdue invoices before enabling the scheduler, to avoid an unintended backlog of messages. No production flag was inspected or changed during this local audit.

### 8. Corrupt inbox records and delivery-report edge cases need isolation (medium/low)

- A decryption exception in a single personal-feed record fails the entire page. Retry recovery also treats thrown decryption failures as deferred recovery rather than distinguishing permanently corrupt payloads from temporary key/service outages.
- TextSMS sets delivered directly from each final report rather than preserving an already confirmed delivery, allowing a later negative report to regress state.
- Africa's Talking callback logging takes the last four characters of every nonblank phone number without checking its length; a short malformed value raises an exception before processing the receipt.
- Personal unread counts count email and in-app copies separately, rather than unique business events. The admin delivery projection similarly joins multiple SMS attempts to the same notification, potentially duplicating rows. These are presentation/count-model limitations, not additional business events.

Recommendation: isolate unreadable messages with a safe placeholder and diagnostic metadata; retain encrypted payloads for investigation. Make delivery transitions monotonic, validate callback input, and define whether badges count business events or individual channel records.

## Existing protections verified in implementation/tests

- Personal access is recipient-scoped; another recipient's read action is denied.
- Delivery-monitor endpoints require a separate permission; the normal page opens personal alerts first.
- Personal messages render as text, not executable HTML; action parsing rejects direct off-site links.
- Email submission is queued after persistence/commit rather than before the business transaction commits.
- Retry claims are atomic and bounded for retry-enabled records.
- Rent/sale and estate reminder handlers recheck outstanding balance/due dates and use durable outbox keys to suppress repeated processing.
- Lease termination/renewal and property invitations have business-event notification paths.
- WhatsApp receipt handling is serialized and preserves delivered/read progress.
- Payment ledger replay protection prevents duplicate receipt processing for the same applied payment.

These protections do not resolve the priority findings above.

## Test results

Executed existing tests against the current worktrees:

- Backend: **29 tests passed; zero failures, errors or skips** across NotificationServiceTest (4), NotificationReportServiceTest (4), NotificationRetryRoutineTest (3), RentalOverdueReminderTest (6), ServiceChargeReminderServiceTest (2), ServiceChargeReminderHandlerTest (5), CreateInvoiceNotificationTest (1), and WhatsAppCallbackTest (4).
- Frontend: **7 Chromium browser tests passed** in `e2e/notifications-audit.spec.ts`, covering shared unread badge, admin delivery terminology, personal-first navigation, safe message rendering/read action, feed retry behavior, invitation navigation and off-site action rejection.

The browser checks use mocked APIs and a local standalone server. Backend checks are existing unit/regression tests, not live-provider delivery or production database concurrency tests. No actual emails/SMS were sent and no production accounts were modified.

Missing regression cases: secret-code exclusion (including read endpoint), recovery-factor isolation, concurrent read/provider updates, accepted-SMS reconciliation without resend, unknown badge count/account-switch races, live inbox refresh, invitation invalidation, and full merchant/buyer/rider event coverage.

## Changes, decisions and readiness

- Database/configuration changes: **none**.
- Application fixes deployed: **none in this audit**.
- Actual production delivery, effective reminder configuration, and end-to-end delivery through configured email/SMS providers: **not verified**.
- Decisions to confirm before implementation: whether unread counts represent distinct business events or channel records; the approved stakeholder/event notification matrix; how historical reminders are rolled out.
- Release recommendation: address the security-challenge exposure, concurrent status-update risk and accepted-SMS resend behavior first, then complete the user-facing navigation/refresh gaps and validate in staging before production sign-off.
