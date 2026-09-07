# Subscriptions, Community Funds, Tax Assist, Visitors and Smart Gate audit

Date: 7 September 2026. Local candidate only; not deployed.

Baseline: consolidated backend `28363d2a1abab9b6b1dc401947237a04b3355e3d` and frontend
`8982d18a6c0d2958fdfdd43aa03ebd85a4c2a16e`, with earlier uncommitted Billing,
Notifications, Wealth and Marketplace audit changes preserved. This is an improvement
of the existing application, not a replacement system. No hosted records or secrets were changed.

## Subscription catalogue and administration

Reviewed catalogue seeding, plan maintenance, role/product selection, trial/free activation,
paid-invoice activation, renewal, cancellation, period boundaries, primary/add-on entitlements
and administration UI.

Corrected:

- Editing Soko previously inferred SERVICES from its shared category. Preserve the stored product.
- Editing an unpriced add-on could turn SALES_MANAGED into FREE. Preserve sales-managed mode;
  reject changing an existing free/paid purchase mode through a price edit.
- Reject changes to plan code, role, category, currency and billing cycle after creation.
  Invoice/subscription references must not silently acquire a different identity or term.
- Validate role/category consistency on creation and updates.
- Active subscribers keep feature/quota access when their plan is retired from new sales.
  Existing settled invoices can still activate the purchased term after retirement. New
  checkout continues to require an active plan. Term expiry checks remain in place.
- Recognize `-1` as the existing unlimited-quota convention in backend validation and the UI.
  The previous UI required obsolete MAX_PROPERTIES/MAX_UNITS fields, rejected free plans
  and rejected unlimited quotas, preventing canonical plan maintenance.
- Preserve an existing empty quota list on edit. A free merchant plan must not acquire
  rental-unit/staff-seat allowances merely because an administrator saves its form.
- Admin catalogue now uses bounded server-side pagination and name/code search; defaults to
  active offers, exposes retired records deliberately, distinguishes custom quotes from free
  plans, displays UNITS/TEAM_SEATS, and retains edits with visible save errors.
- Remove the empty/nonfunctional Subscriptions tab. This does not implement a customer-wide
  subscription operations workbench; the remaining admin screen is explicitly a catalogue.
- Product identity fields are read-only on edit; View is a disabled fieldset; repeated saves
  and status changes are guarded. Mutating buttons respect plan permissions.

### Plan cleanup decision

`STARTER`, `STANDARD`, and `STANDARD_AFFILIATE` are the known legacy aliases. Existing seed
logic retires STARTER, and existing migration V66 normalizes the latter two. This patch
prevents reactivation of those aliases and hides retired plans from normal catalogue browsing.
**No hard deletion and no rewrite of old migration files.** This turn did not inspect or
change the live catalogue; its actual migration status still needs verification at release.

Retain the distinct Landlord, Estate, Sales and Wealth Bronze/Silver/Gold packages, monthly
and annual billing options, separate Services/Soko/Affiliate identities, and explicitly
sales-managed quote entries. Different billing periods or quota tiers are not duplicates.
No unsupported pricing changes or contractual tier-feature decisions were invented.

Still open:

- A real subscriber administration workbench for customer lookup, payment exceptions,
  scheduled changes and sales-managed provisioning is not completed here.
- Lifecycle processing expires access safely but does not itself perform provider recurring
  charges. Live renewal/mandate, failed-payment recovery and cancellation journeys require
  provider verification. Scheduled processing is not yet comprehensively batched/locked.
- Catalogue features/quotas remain mutable for existing subscribers; immutable purchased-term
  entitlement snapshots and approval/audit controls for commercial changes are a follow-up.
- Some seeded support/SLA/white-label feature claims need operational confirmation before
  being advertised as contractual capabilities. No spreadsheet revision was supplied this turn.
- Verify legacy alias references and any outstanding invoices against the actual deployment
  before retiring additional plans. Do not automatically merge merely similar names.

## Community Funds

Reviewed creation/account binding, opening/enrolment, pledges/invoices, paid-event posting,
dashboard visibility, expenditure request/approval/rejection and recorded disbursements.

Corrected:

- Add pessimistic fund/expenditure/contribution locks so monetary state checks and mutations
  serialize. Approval availability previously had no fund-level lock.
- Revalidate the recipient's active verified Community Fund account when a pledge is invoiced.
- Fund invoices explicitly use fund currency, rather than inheriting a rental unit's currency.
  Escape invoice line descriptions before putting them in HTML.
- A contribution receipt must match its assessed amount and fund currency; mismatched or
  partial events cannot mark the contribution PAID. Repeat completed receipts remain idempotent.
- Member transaction responses omit other contributors' receipt references and internal
  contributor/source identifiers; management and the payer retain appropriate receipt access.
- Failed draft/expense submissions retain inputs; stale selected-fund responses are ignored.
  Repeated actions are guarded. Recording a disbursement is described as recording an
  external payment, not transferring money automatically.

Still open:

- Live account verification, payment callback/reconciliation and contribution refund journeys.
- Complete member/manager authorization smoke tests with multiple real workspaces and officers.
- Searchable property selection instead of entering IDs; scalable fund/ledger pagination;
  closing/reopening, new-member enrolment and contributor adjustments.
- Evidence references are not a complete authorized upload/download evidence workflow.
- A complete disbursement approval, provider execution and reversal workflow is not supplied
  by the existing 'record external payment' action.

## Tax Assist

Reviewed MRI/CGT calculations, rule versions, history, admin controls, masked-PIN connection
requests, ownership checks and paused-service states.

Corrected:

- Do not record withholding credit against an MRI outcome for which no tax was assessed.
- Read the saved calculation's original rule snapshot; later admin edits must not rewrite
  the rule displayed against historic calculations. Corrupt snapshots fail explicitly;
  legacy empty snapshots retain the existing repository fallback.
- Remove the blanket CGT '20th of next month' date. The form lacks full payment and
  registration/lodgement dates, so it cannot safely determine this deadline. Show a
  specific warning to confirm timing before transfer, without fabricating a due date.

Official sources checked on the audit date:

- [KRA rental income guidance](https://www.kra.go.ke/individual/filing-paying/types-of-taxes/residential-rental-income)
- [KRA capital gains guidance](https://www.kra.go.ke/individual/filing-paying/types-of-taxes/capital-gains-tax)
- [KRA CGT FAQs](https://kra.go.ke/images/publications/Capital-Gains-Tax-FAQs_8112023.pdf)
- [KRA 2026 service charter](https://www.kra.go.ke/images/publications/English-Service-charter-April-2026.pdf)

The CGT page/FAQs discuss the earlier of full purchase-price receipt and transfer
registration, while the charter references lodgement. Older public notices also differ.
Do not present one guessed deadline as certified tax advice. Tax Assist remains an estimate,
not filing confirmation; no live KRA transmission was enabled or attempted. Legal review of
effective-date rules, exemptions, current legislation and historical outcomes remains required.
Concurrent rule-version administration and independent approval of tax-rule changes need
further strengthening before automated compliance decisions.

## Visitors and Smart Gate

Reviewed expected/walk-in registration, host approval/denial, staffed check-in/out,
visitor cancellation/deletion, signed device decisions, nonce replay, entry counts,
plate checks, device enable/disable, ownership boundaries and access logs.

Corrected:

- Allow active homeowners through the same authorized-unit fallback already used elsewhere
  in the visitor workflow.
- Reject a visit whose validity ends before arrival or is already expired; refuse approval
  of an expired pending visit.
- Compare device IDs by value (not Java boxed-Long reference identity).
- A correlation replay must retain device/property, direction and vehicle. Granted replays
  also require the original credential/visitor and a recent decision.
- Denied decisions expose no visitor ID/name/unit/type. Wrong-property credentials do not
  link another property's visitor into the requesting property's access log.
- Explicitly deny inactive credentials; cap access-log requests at 100 rows.
- Controller UI handles property hydration/switching and stale responses, exposes load and
  mutation failures, prevents repeated writes, and explains that only public keys belong
  in this form. Existing Ed25519 signatures and database nonce protection remain enabled.

Still open:

- Physical-controller integration and real-domain host/guard/homeowner tests, including
  signed replay under concurrency, key rotation, controller revocation and network outages.
- Validate safe physical egress/emergency operation independently of app credential expiry.
  This audit does not certify a physical barrier as life-safety compliant.
- Long histories need navigation beyond the recent activity screen. Some visitor counters
  still describe the loaded page rather than an estate-wide aggregate.
- Real SMS delivery and expiry notifications remain external-service gates; WhatsApp remains
  parked because its webhook app-secret prerequisite is unresolved.

## Verification and release boundary

Verified local results:

- Backend `mvn test`: 759 discovered, 758 passed, 0 failures/errors, 1 existing application-context
  test skipped. Log: `D:/SlickHood-Codex/operations/modules-final-backend-20260907.log`.
- Docker-backed MySQL 8.4: 6 integration tests passed, none skipped, including the new
  competing Community Fund transaction lock test. Log:
  `D:/SlickHood-Codex/operations/modules-mysql-20260907.log`.
  This fixture builds an isolated schema with Hibernate; it is **not Flyway migration
  certification** and does not prove all service-level concurrency combinations.
- Final frontend optimized build and TypeScript: passed. Log:
  `D:/SlickHood-Codex/operations/modules-final-build-20260907.log`.
- Final ESLint: 0 errors, 466 warnings within the existing 478-warning budget. Log:
  `D:/SlickHood-Codex/operations/modules-final-lint-20260907.log`.
- Focused Playwright regression: **22/22 passed** across registration/subscription,
  plan administration, Community Funds, Tax Assist and Visitors/Smart Gate. Includes empty
  quota preservation, custom/unlimited plan editing, retained failed drafts and controller
  failure feedback. Log: `D:/SlickHood-Codex/operations/modules-browser-confirmed-20260907.log`.
  These use mocked API responses and synthetic sessions, not live identity/provider accounts.
- Both repositories passed `git diff --check` using their normal line-ending configuration.

All logs, test output and build files are on D. Frontend builds used the loopback API for mocked-browser testing; they are
not production deployment artifacts. No production deployment, Git push or database
migration was performed in this audit.

Before release: review the complete combined dirty changeset; build immutable commits with
production configuration; verify migrations/baseline compatibility, backup/rollback and
authenticated role isolation; exercise real subscription/fund payments and visitor/gate
notifications. Do not interpret passing mocked browser tests as external-service certification.
