# Notifications audit — 7 September 2026

## Release status

Corrections are in the local consolidated release checkout, alongside the earlier uncommitted billing changes. Nothing in this audit has been published or deployed. Do not describe WhatsApp as live. No customer messages, OTPs, overdue notices, termination notices or test payments were sent by this audit.

User decision: park WhatsApp integration for later because the Meta App Secret is not available. Preserve the prepared integration and setup checklist; do not enable WhatsApp, bypass signature verification, or replace the working email/SMS configuration.

Backend baseline: `28363d2a1abab9b6b1dc401947237a04b3355e3d`. Frontend checkout baseline: `8982d18a6c0d2958fdfdd43aa03ebd85a4c2a16e`.

## Live read-only evidence

- Host: `13.205.200.43`, service `pms` running.
- Inspected `E:\WhatsApp Webhooks.txt` without printing or copying credential values into source or logs.
- Supplied webhook URL is `https://app.slickhood.com/api/callback/whatsapp`.
- From the host, Meta `/v22.0/me/permissions` returned HTTP 200. Granted permissions included `business_management`, `whatsapp_business_management`, and `whatsapp_business_messaging`. This verifies token validity/reachability, not phone registration, template approval or message delivery.
- The inspected host configuration's verification token did not match the supplied file; no non-placeholder Meta App Secret was found. The App Secret is distinct from both the verification token and access token.
- Last-30-day aggregates: 119 email records marked accepted/delivered by the application's SMTP sender; 20 SMS OTP records, 18 marked delivered and two unconfirmed. SMTP acceptance does not establish inbox delivery.
- No WhatsApp receipt records or currently overdue invoice rows were returned. No live overdue/termination journey was therefore certified.
- Local-PC HTTPS probing encountered certificate authentication failure. TLS validation was not disabled; the host probe succeeded with normal certificate validation.

## Confirmed corrections

| Finding | Correction |
| --- | --- |
| Async queue creation could escape the originating transaction | `sendNotification` and `queueNotification` now persist within the PMS transaction; transport remains asynchronous after commit. |
| Temporary database/executor/key-service errors permanently disabled retries | Recovery preserves eligibility for transient exceptions; unsupported stored types and empty metadata remain separately handled. Provider payloads are not logged. |
| WhatsApp handled only the first status and assumed optional fields existed | Handle all statuses; safely ignore inbound-message-only/empty updates; optional pricing is not required. |
| Out-of-order callbacks could misrepresent delivery | Lock matching active WhatsApp receipt rows; read/delivered do not regress to sent/failed; unknown or other-provider receipts are not modified. |
| Callback JSON parser ignored snake-case fields | Use Jackson mappings after raw-body signature verification. Invalid JSON receives 400. |
| Webhook verification returned 200 on failure | Require subscribe mode and a non-empty matching token; invalid verification returns 403. Existing HMAC authentication remains mandatory for POST. |
| Configurable Meta URL could leak the bearer token to another host | Restrict sends to the HTTPS Graph messages endpoint, numeric phone ID, validated international recipient and non-placeholder token. |
| Empty provider response could masquerade as successful dispatch | Require exactly one valid message receipt; record acceptance separately from delivery. Template name/language are configurable. |
| Global WhatsApp provider selection could put OTPs in a utility template | Explicitly reject OTP use of the utility template. Do not change the active SMS provider during WhatsApp rollout. |
| No general automated rental-overdue reminder; existing email meant a late fee was assessed | Added a separate no-fee reminder through the durable outbox. Cursor batching, invoice/due-date/period deduplication, current-balance and paid/date checks, transactional queue acknowledgement. Disabled until deliberate rollout. |
| Rent invoices lacked the contractual due date; shrinking offset pagination skipped leases | Carry `nextPaymentDate` into the invoice and use ordered lease-ID cursor pagination. Historical null due dates are not guessed or backfilled. |
| Invoice PDF emails could be sent before commit and silently lost on failure/restart | Persist an invoice-email outbox event within invoice creation; generate/send after commit; safe failures propagate to outbox retry/dead-letter handling. |
| Estate notices could send stale overdue wording after a date change or zero balance | Recheck date phase and positive balance before queueing. |
| User-entered reasons/names could inject HTML into notices | Escape lease termination, ownership-ended, sales-status and estate reminder values. |
| Existing termination could be overwritten and notified again | Reject a second termination request while `NOTICE_GIVEN`; keep the agreed notice-period check. This is not a new amendment/cancellation workflow. |
| User/admin delivery labels disagreed or overstated evidence | Both distinguish mail-server acceptance from confirmed delivery; unconfirmed is not automatically “failed”. Personal feed strips email markup as text, reports load failures, and ignores stale responses. Admin fetch errors no longer log raw request objects. |
| Shared HTTP/SMS logging exposed URLs or provider payloads | Remove response bodies, token-bearing URLs, raw receipts and exception messages from the audited transport logging sites. This is not a claim that every unrelated logger in the application has been redacted. |

## Coverage and remaining gaps

| Process | Source audit result / remaining work |
| --- | --- |
| Registration, login OTP, password flows, invitations | Existing email/SMS types and personal-feed scoping examined. OTPs intentionally have no long-lived retries. Several invitation types remain non-retryable; introduce expiry-aware resend/outbox recovery, not blanket retries of expired links. Two historical SMS OTP deliveries remain unconfirmed; no provider-level diagnosis of those individual attempts was performed. |
| Rental invoices, late payments, fees | Invoice outbox and due dates corrected. New general reminder is separate from manual late-fee assessment. Verify contractual cadence and historical dates before enabling. Legacy sale leases and their charge recurrence still require a separate billing review. |
| Lease notice, renewal, expiry, termination | Current notice emails target tenant and property owner; request respects the configured notice period. Expiry worker uses batches of 200. No automatic nonpayment termination was introduced. A notice amendment/cancellation process, completion notice, and durable proof-of-service workflow remain missing. |
| Estate service charges / ownership | Existing pre-due and repeated-overdue outbox mechanisms inspected; stale-state and HTML handling corrected. Ownership-ending email exists. Existing service-charge outbox handler acknowledgement is not atomic with notification creation, leaving a crash-window duplicate risk. |
| Property sale / offers / homeowner agreements | Sale status notifications exist and are escaped. Governed document issuance still directly sends an attachment inside its state-change transaction; migrate that path to an outbox before treating delivery evidence as authoritative. |
| Soko | Store/product moderation notifications found. Complete order, packing, dispatch, delivery, refund and settlement notification coverage is not established by those moderation hooks. |
| Services | Approval/rejection, booking and complaint hooks exist. Several types are non-retryable and booking notification failures can be swallowed. Durable lifecycle delivery remains incomplete. |
| Smart gate / visitors | Host booking/arrival/departure and access SMS hooks exist, with channel configuration. No live gate hardware or recipient-delivery test performed. WhatsApp is not an independent configured channel yet. |
| Payments / subscriptions | Payment success SMS and sales-request admin email exist. No complete subscription renewal/expiry/cancellation notification lifecycle found. Billing callback/recipient-binding blockers from the billing audit remain separate release blockers. |
| Insurance | Application, quote, payment, policy, claim and renewal hooks exist. Renewal scheduler stages and bounded queries inspected. Stage label can overstate exact days remaining after a missed run; first-page reminder selection needs load/backlog certification. |
| Help Desk | Active-superadmin escalation, SLA and authenticated-customer reply hooks exist. Host evidence confirms SMTP acceptance. No actual customer escalation or new support ticket was generated. |
| Affiliate / Wealth / community funds | No complete dedicated notification lifecycle found in the audited service paths. Shared invoice/payment messages do not replace payout, document-expiry, contribution and operational alerts. |
| In-app feed / administration | Existing recipient scoping and negative ownership tests retained; honest status/error display corrected. PDF attachment outbox records are not yet unified into the personal notification feed. Recipient ownership should ultimately be stored as user/workspace IDs, not inferred only from email/phone variants. |

## Important reliability limits

- Transport is at-least-once, not exactly-once. SMTP acceptance followed by a database/network failure can produce a retry duplicate. Correlation/idempotency identifiers and delivery receipts are required to improve this.
- The generic retry worker still rebuilds stored message text without rechecking the originating invoice/document. A payment after notification queueing but before a later transport retry can make reminder text stale. Entity/version references and send-time eligibility checks are needed before enabling large-scale debt notices.
- A WhatsApp callback arriving before the sender persists its message receipt can be missed. A durable webhook inbox and reconciliation are still needed; receipt row locking alone does not solve that race.
- The generic SMS recovery path cannot distinguish accepted-but-awaiting-receipt from failed transport. Do not enable WhatsApp as the global SMS provider: doing so could resend accepted notifications and would block OTP utility-template misuse by design.
- Missing per-recipient opt-in/opt-out preferences and per-message approved-template mappings block broad WhatsApp notifications. Do not convert all emails to WhatsApp or send debt notices to every stored phone number.
- No new database migration is required by these source changes. Existing outbox schema is required. MySQL repository tests use disposable Hibernate-created schema; they are not a production migration rehearsal.

## Validation evidence

- Initial focused tests: 63 passed, no skips.
- Final backend unit/regression suite: 710 discovered, 709 passed, zero failures/errors, one pre-existing application-context skip.
- Docker-backed MySQL integration: five passed, zero failures/errors/skips. Disposable MySQL with Hibernate-created schema; not a Flyway migration rehearsal.
- Final frontend build passed. Focused browser regression: twelve passed (three notification and nine billing journeys); not the complete application browser suite.
- Final frontend lint passed: zero errors, 464 warnings within the existing 478-warning budget.
- Backend and frontend `git diff --check` passed (line-ending normalization warnings only).
- Evidence logs: `D:\SlickHood-Codex\operations\notification-final-backend-tests-20260907.log`, `notification-mysql-tests-20260907.log`, `notification-final-frontend-build-20260907.log`, `notification-final-browser-tests-20260907.log`, `notification-final-frontend-lint-20260907.log`.

## Secure WhatsApp setup / deployment handoff

1. Supply the Meta App Secret through the protected local credential file or a secret manager. Never paste it in chat, commit it or put it in a public frontend variable.
2. Confirm the registered business phone-number ID and approved utility template with body parameters `name` and `data`, plus the matching template language. A valid token alone does not establish these.
3. Securely provision backend `whatsapp.verifyToken` and `whatsapp.appSecret` in the protected runtime configuration. Set `whatsapp.utility-template` and `whatsapp.template-language` to the approved values.
4. Through the authorized, encrypted configuration-maintenance mechanism, configure `WHATSAPP_URL`, `WHATSAPP_BUSINESS_PHONENUMBER_ID`, and `WHATSAPP_ACCESS_TOKEN`. Do not change `ACTIVE_SMS_PROVIDER` from the working SMS route. Implement a separately selected WhatsApp channel with consent and template routing before customer rollout.
5. Meta callback: `https://app.slickhood.com/api/callback/whatsapp`; subscribe to `messages`. Preserve `X-Hub-Signature-256` and the exact raw body through the proxy. Invalid verification must be 403; invalid POST signature must be 401. Do not log verification-token query parameters.
6. Run a single approved-template canary to an explicitly consenting test recipient after configuration, using masked evidence and signed delivery/read callbacks. Test malformed, duplicate, mixed-batch, out-of-order and unknown receipt cases. Do not use real late-payment or termination content as a canary.
7. Deploy backend corrections before frontend only after the combined pending billing/release gates pass. Rebuild with actual production environment and immutable release hashes; the local browser-test build uses a loopback API and is not deployable.
8. Keep `pms.rental.reminder.enabled=false` until due dates, recipient scope, aged-debt eligibility and cadence have been reviewed. Defaults when deliberately enabled: 08:15 Nairobi daily scan, seven-day repeat interval, twelve age-based occurrence windows. Review skipped legacy null-date records; never infer debt dates silently.
9. Monitor notification retry exhaustion, dead outbox rows, provider acceptance vs delivery, webhook rejection rate, queue age, SMTP failure and scheduler duration. Drain/quiesce new event types before rolling back to code without their handlers; preserve all pending rows.

Official references: [Meta webhook validation](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/webhooks/start/), [Meta webhook payload reference](https://www.postman.com/meta/whatsapp-business-platform/folder/tduohwq/webhook-payload-reference), [WhatsApp business messaging policy](https://business.whatsapp.com/policy).
