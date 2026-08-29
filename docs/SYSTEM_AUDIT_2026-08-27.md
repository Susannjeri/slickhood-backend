# SlickHood PMS whole-system audit

Date: 28 August 2026  
Scope: backend and frontend source, module wiring, permissions, workflows, reporting, build and automated tests. Production payment-provider, smart-gate hardware and external messaging certification require separate live-environment test plans.

## Executive assessment

SlickHood is a modular monolith with broad functional coverage. The current production build generates 65 frontend routes, and the backend suite covers 43 test suites. The strongest areas are role selection and scoping, property/unit foundations, subscription lifecycle, visitor access, Soko delivery controls, Wealth analytics and the recently added AI Help Desk. Remaining risk is concentrated in production integrations, provider certification and browser/database coverage of the complete tenancy lifecycle.

## Implemented during this audit

- Added a role-scoped Reports centre with date selection, metrics, detail tables and CSV export.
- Added eight report families: invoice collections and arrears, payment reconciliation, occupancy and rent roll, visitor and gate activity, sales pipeline, estate service charges, service marketplace bookings, and Soko orders and delivery.
- Added a missing Payments operational screen with filtering and pagination.
- Fixed property-manager invoice authorization to compare the invoice property with the manager's `propertyId`, not the manager mapping row ID.
- Fixed subscription revenue selection so invoice type is actually applied, including compatibility with historical subscription invoices.
- Marked newly created subscription invoices with `billingType=SUBSCRIPTION`.
- Prevented a caller from requesting dashboard totals for a role other than the JWT-selected active role.
- Added PATCH to the default CORS methods because the application uses PATCH endpoints.
- Restricted the Flutterwave browser-return update endpoint to authenticated GET requests.
- Repaired broken sidebar destinations for Admin and General Settings and removed a stray API-console log.
- Corrected the OpenAI environment template and retained secret values only in the ignored local environment file.
- Added report tests for role filtering, date limits, arrears calculation and PII exclusion.
- Added an immutable, double-entry financial journal for invoice issuance, receipt allocation and unapplied overpayment credits. Provider references are idempotent and invoice rows are locked during allocation.
- Added account statements and lease expiry/renewal forecasts. Lease ownership is derived through the existing normalized `Lease → UnitTenant → Unit → Property` chain rather than duplicated foreign keys.
- Completed service-category edit/deactivate actions and corrected approve/reject frontend calls to the backend PUT contract.
- Added maintenance work orders with controlled state transitions and wired maintenance plus generated agreements/notices into the unit dashboard.
- Replaced empty dashboard activity/task/health placeholders with role-authorized operational report data.
- Added subscription lifecycle, affiliate earnings, KYC operations, notification delivery, smart-gate health and maintenance reports.
- Added a Privacy Centre with authenticated portable-data download, formal access/erasure requests, parameterised due dates, duplicate-open-request prevention, controlled super-admin review, legal-hold/retention grounds, secure result references and audit events. Credentials, authentication secrets, document paths, hashes and encrypted OCR are excluded from exports.
- Added authorization-scoped PDF payment receipts. Receipts are available only for finalized successful payments with a provider reference and include the receipt number, invoice reference, provider reference, channel, currency, amount, payment time, payer and payee.
- Added controller-level signed webhook tests for Paystack and shared-token rejection tests for M-PESA, including proof that a valid Paystack callback is dispatched exactly once.
- Corrected the frontend parameter API authorization header so authenticated parameter lookups consistently send a valid Bearer token.
- Added a controlled seeded property → unit → accepted tenancy → signed lease → rental invoice → Paystack initialization → signed callback → balanced ledger → reconciliation journey. It uses the real domain services with deterministic provider responses and proves callback replay idempotency.
- Corrected Paystack's internal completed status from `success` to the canonical card-payment status `successful`; this aligns reconciliation, payment presentation and receipt eligibility.
- Added an opt-in disposable MySQL 8.4 Testcontainers fixture for real repository persistence, locking, authorization, journal idempotency and report-query certification. It is fail-safe: without Docker it skips and never falls back to a configured SlickHood database.

## Report catalogue

| Report | Status | Audience and purpose |
|---|---|---|
| Invoice collections and arrears | Implemented | Owners, tenants, managers, finance and administrators; billed, collected, outstanding and overdue |
| Payment reconciliation | Implemented | Role-scoped payment-channel success, pending and exception records |
| Occupancy and rent roll | Implemented | Current occupancy, vacancy, unit pricing and advertising |
| Visitor and gate activity | Implemented | Walk-ins, drive-ins, deliveries, gate state and times; direct PII is excluded |
| Property sales pipeline | Implemented | Offers, due diligence and completion counts and details |
| Estate service charges | Implemented | Billed, outstanding and paid service-charge position |
| Service marketplace operations | Implemented | Bookings, completion, cancellation and quoted values |
| Soko operations | Implemented | Orders, payment state, dispatch, delivery and delivery-code verification |
| Landlord and tenant ledger statements | Implemented | Immutable invoice, receipt, customer-credit, settlement, fee, refund, reversal and chargeback entries |
| Lease expiry and renewal forecast | Implemented | Uses the normalized tenancy/unit/property relationship and role scoping |
| Settlement and split-payout operations | Implemented foundation | Append-only operational cases and balanced ledger entries; live provider settlement ingestion/certification remains |
| Subscription lifecycle | Implemented foundation | Trial, active plan, renewal/expiry reporting; MRR/ARR and cohort churn remain |
| Affiliate commission liability | Implemented foundation | Earnings, approval and payout allocation; aging/tax adjustments remain |
| KYC operations | Implemented foundation | Consent, phone, submission and review status; OCR-confidence SLA remains |
| Maintenance SLA and cost | Implemented foundation | Work orders, controlled status, estimates/actuals; attachments and vendor SLA remain |
| Notification delivery performance | Implemented for current channels | Delivery/retry metrics; WhatsApp remains outside unified orchestration |
| Smart-gate health | Implemented foundation | Enablement and last-seen health; firmware/offline duration history remains |

Monetary summaries in the new reports are grouped by currency. The system does not add KES, USD or other currencies into a misleading combined total.

## Prioritized gaps

### P0 — production and financial controls

1. Core accounting now includes immutable double-entry invoice, receipt, provider-fee, settlement, refund, reversal and chargeback events. Production provider settlement/refund ingestion and certification remain external integration work.
2. Production certification remains outstanding for each provider route: M-PESA Paybill/bank callback, STK, Paystack subaccounts, PesaLink and Flutterwave. Airtel Money is explicitly inactive and its implementation is a stub.
3. The AI Help Desk is code-complete but cannot answer until OpenAI API billing/quota is activated. The application safely escalates when AI is unavailable.
4. WhatsApp Cloud API production onboarding is not complete. WhatsApp wrapper classes exist, but `NotificationChannel` contains only EMAIL and SMS, so WhatsApp is not integrated into notification orchestration or delivery reporting.
5. The Playwright browser foundation now verifies unauthenticated routing, sign-in validation, explicit policy acceptance, role-carrying registration, parameterized role-scoped trials, privacy export/request handling, marketplace payment gating, safe Flutterwave/Paystack returns, and invoice → verified payment → PDF receipt download. The controlled backend journey now verifies the complete property → unit → lease → invoice → signed Paystack callback → reconciliation chain, including callback replay idempotency and balanced postings. A persisted MySQL/browser deployment fixture and certified provider sandbox remain outstanding.

### P1 — incomplete operational journeys

1. Service-provider approval/rejection and category maintenance are now wired; full provider-profile verification screens should next replace the unused placeholder component.
2. Maintenance and documents are wired into unit detail; maintenance attachments, comments and assigned-provider SLA escalation remain.
3. Dashboard activity, lease tasks, occupancy and collection health now use report APIs. Historical occupancy snapshots are still needed for a genuine trend chart.
4. Lease expiry reporting is implemented through the existing normalized tenancy relationship. Automated renewal offers, notice dispatch and acceptance workflows remain.
5. Rental accounting now has a security-deposit ledger, configurable late-fee rules/caps/grace periods, waivers and credit-note application. Renewal automation and richer approval policy remain.
6. Sales now gates agreement/completion/ownership transfer on due diligence, agreement, escrow, registered transfer and handover evidence. Detailed checklist ownership and buyer statement presentation remain.
7. Estate management now includes maintained annual budgets, planned-versus-actual lines, quorum-controlled meetings, resolutions and property-level common-area work orders. Homeowner statement presentation remains.
8. Soko now has expiring stock reservations, idempotent stock release, reasoned cancellation, late-payment refund flags, delivery evidence and merchant refund/settlement states. Service marketplace bookings now issue formal invoices to customers, snapshot the provider's verified merchant account, transition from paid callback to work start, require completion evidence, and expose finance-controlled refund/settlement states.
9. The service-provider dashboard now maintains the verified merchant destination and the marketplace UI exposes accept-and-invoice, customer payment, paid work start, evidence-backed completion, cancellation and refund status. The production frontend build passes with all 64 routes.
10. Actuator health/liveness/readiness probes are enabled; detailed metrics remain administrator-only. Administrators can inspect failed/dead outbox events without exposing event payloads, while existing retry, dead-letter, correlation-ID and backlog metrics remain active.

### P2 — scale, governance and insight

1. Add subscription analytics (MRR, ARR, cohort conversion, churn, expansion and failed renewal) after the subscription product decisions are finalized.
2. Health/readiness, outbox counters and payload-safe dead-letter visibility are implemented. Provider latency/error metrics, scheduled-job dashboards and alert thresholds remain.
3. Subject-access/export and erasure-request governance are implemented. Define and automate the record-class retention schedule and approved anonymisation/deletion executors across KYC, visitor PII, messages, documents and audit logs before enabling destructive processing.
4. Add report scheduling and delivery only after recipients, encryption, retention and authorization-at-delivery rules are defined.
5. Add row-count queries and asynchronous exports for datasets beyond the current 5,000-row interactive safety limit.
6. The first real-MySQL repository integration fixture is implemented. Docker is not installed on this workstation, so its execution is currently skipped. Broader JPQL coverage and Flyway certification remain; Flyway cannot provision a clean database until the authoritative pre-Flyway core-schema baseline is recovered and reviewed.

## Module-by-module position

| Module | Position | Principal gap |
|---|---|---|
| Authentication, registration and role switch | Substantially implemented | Full browser E2E, MFA recovery controls and phone-verification requirement |
| KYC/OCR | Implemented foundation | Confidence review, resubmission SLA, retention and future IPRS integration |
| Subscription and trials | Strong functional flow | Commercial validation, revenue analytics and live renewal failure testing |
| Properties, units and rentals | Strong operational foundation | Work-order attachments/SLA and renewal automation |
| Documents and notices | Lifecycle implemented and unit-wired | Add delivery evidence and broader property vault actions |
| Payments | Multiple providers plus operational ledger implemented | Live settlement/refund ingestion and production certification |
| Estate and homeowners | Operational workflow implemented | Homeowner statement presentation and document attachments |
| Sales and buyers | Evidence-gated pipeline implemented | Detailed due-diligence tasks and buyer statement presentation |
| Wealth | Strong analytical module | More automatic transaction feeds, valuation provenance and scenario auditability |
| Visitors and smart gates | Strong access workflow | Hardware certification, offline operation and device-health telemetry |
| Service marketplace | Invoice-backed booking, payment gating, evidence completion, finance controls and dashboard actions implemented | Live provider reconciliation and richer verification workspace |
| Soko | Ordering, secure delivery and exception controls implemented | Live provider reconciliation and exception-queue UI |
| Affiliate | Operational foundation implemented | Fraud rules, tax treatment, adjustments and payout aging |
| Notifications | Email/SMS implemented | WhatsApp orchestration, preferences, consent, templates and delivery analytics |
| AI Help Desk | Implemented with human escalation | API quota, production deployment, content governance and response-quality evals |
| Reports | Expanded operational suite implemented | MRR/ARR cohorts, settlement aging, OCR quality and scheduled exports |
| Privacy and subject rights | Request, review, legal-hold and safe export workflow implemented | Approved record-class retention schedule and automated anonymisation/deletion executors |

## Verification performed

- Backend compile passed.
- Targeted ledger, payment-operation, rental-finance, estate-operations, Soko, maintenance and Reports tests passed.
- Full backend test suite passed (190 tests across 43 suites, zero failures/errors, one intentionally skipped).
- The MySQL Testcontainers fixture compiles and its fail-safe execution passed with one explicit skip because no Docker environment is installed. It did not connect to any existing database.
- Frontend Next.js production build passed with 65 routes, including `/dashboard/privacy`, `/dashboard/reports` and `/dashboard/payments`.
- Playwright production-browser suite passed (10 tests): authentication access/validation, registration policy acceptance and selected-role propagation, parameterized role-scoped trial activation, privacy erasure/export, marketplace invoice/payment gating, rejected Flutterwave verification, non-final Paystack browser returns, and verified payment receipt download.
- No production deployment or live-provider transaction was performed during this audit.

## Recommended execution order

1. Certify every live provider route and reconcile external marketplace/Soko settlement and refund confirmations into the immutable operation ledger.
2. Complete renewal automation, detailed sales due-diligence ownership and homeowner/buyer statement presentation.
3. Finalise the record-class retention schedule, then implement reviewed anonymisation/deletion executors and asynchronous scheduled reporting.
4. Promote the controlled lifecycle fixture into a migrated MySQL integration environment, then drive it through Playwright for every major role and certify provider sandbox settlement outcomes before production rollout.
