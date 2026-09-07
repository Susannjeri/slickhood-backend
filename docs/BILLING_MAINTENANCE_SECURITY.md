# Billing organization and payment-configuration security

Date: 2026-09-07. Status: local correction batch, not deployed; not certified for unrestricted real-money use.

## Customer journeys

1. **Bills & invoices:** see what is owed, who issued the invoice, currency, due date and payment state. Open the invoice and Pay Now using only its eligible receiving account. A provider redirect or request submission is not payment success.
2. **Payment history:** view role-scoped transactions and receipts. Distinguish completed, pending and failed attempts. A manually recorded payment is a recorded receipt, not proof of a provider transfer.
3. **Receiving accounts:** authorized business owners configure their own collections. Configure details, request review, then attach the verified destination to the relevant property/store/service. Payers must not be able to substitute the beneficiary.

Subscription collection belongs to SlickHood. Rental, estate, sale, Soko and Services collection belongs to the respective recipient. Do not use SlickHood subscription credentials as a fallback for unconfigured recipients. Multiple roles must retain category/workspace boundaries.

Owner settings routes: landlord `/dashboard/accounts`; estate `/dashboard/estate/accounts`; sales `/dashboard/sales/accounts`; merchant `/dashboard/merchant-accounts`; platform `/dashboard/slickhood-accounts`. `/dashboard/landlord-accounts` is administrative oversight, not landlord self-service.

## Implemented in this batch

- Correct backend array-envelope handling in invoice checkout, with active/verified filtering and stale-load cancellation.
- Shared permission-aware billing navigation, setup guidance and persistent bank/PesaLink instructions; no claim that showing instructions confirms payment.
- Account edit, verification and deactivation share a pessimistic account lock and PMS transaction. Verification revocation and credential persistence are atomic. Cache invalidation occurs after transaction commit.
- Ownership checks retained for mutations; verification checks the review permission at the service boundary as well as the controller.
- Verification validates stored values, decrypting internally without logging. Blank/masked/oversized replacements cannot overwrite a credential. Property keys are canonicalized.
- Existing encrypted storage is retained. The UI starts encrypted replacement fields empty and masks them after saving. The drawer immediately shows that verification is required again.
- Credential reads no longer rewrite old-key values behind the scenes, preventing a stale read from overwriting a newer replacement. Old-key decryption still works; re-encryption requires explicit locked maintenance.
- Full API responses/errors and credential/configuration payload debug statements removed from the shared API hook and account components.
- A disabled provider remains maintainable without becoming eligible for new payment initiation.
- New manual payment records no longer remain in progress after being recorded successfully; receipt eligibility is restored. No historical rows were rewritten.

## Security controls still required before real-money certification

These are requirements, not claims of implemented protection:

1. Require recent re-authentication/MFA before recipient, credential, attachment, verification or payout changes. One active login session alone is not sufficient.
2. Persist a configuration revision. Approval must include the revision actually reviewed; reject stale approval. A row lock alone does not solve a reviewer looking at an older screen.
3. Use independent maker/checker approval for beneficiary changes, including administrator-operated accounts, with a documented recovery path that cannot silently bypass review.
4. Record actor, workspace, account, changed field names, revision, reason and review decision in a transactionally durable journal/outbox. Never persist plaintext secrets in audit payloads. Current shared audit logging buffers records in memory and can lose events on restart; it is not sufficient for this guarantee.
5. Notify the account owner through an already verified contact on sensitive changes and approval. Apply rate limits and alert on repeated denied edits.
6. Pin recipient/integration configuration to payment attempts and preserve the snapshot for late callbacks. Do not edit or deactivate a destination with unresolved attempts without an explicit rotation/recovery procedure.
7. Verify provider beneficiary ownership and actual routing. Paystack subaccounts under a platform integration are not equivalent to independent merchant API keys. Agree fees and platform shares explicitly.
8. Strengthen callback status/destination/currency checks, replay protection, atomic payment transitions, durable re-query and reconciliation; resolve the critical findings in the full audit before enabling money movement.

Do not disable signature checks, recipient ownership checks, account verification, TLS or IP restrictions to make a test pass. Never accept customer M-Pesa PINs or card security codes in configuration forms.

## Verification evidence

- `billing-full-backend-tests-20260907.log`: 686 passed, 0 failures/errors, 1 existing application-context skip (687 discovered).
- `billing-maintenance-lint-20260907.log`: 0 errors, 463 warnings within the existing 478-warning budget.
- `billing-maintenance-build-20260907.log`: optimized local test build and TypeScript passed. API URL is loopback for mocked browser tests; this is **not** a deployable production artifact.
- `billing-maintenance-browser-20260907.log`: 12 passed, including real array-envelope checkout, unverified-account rejection, bank instructions, credential replacement/masking, role-specific settings links and hiding account creation from read-only users. Local authenticated mocks, not live provider acceptance.
- `billing-mysql-tests-20260907.log`: **4 passed, 0 failures/errors/skips**, Maven exit 0, disposable MySQL 8.4.11. Includes the active-account locked query and existing rental, estate and sale repository journeys. External database settings were cleared. This is a persistence/query test, not live provider or full concurrent approval certification.
- The initial MySQL run was invalidated by overlapping recompilation of the shared `target/classes` directory (`ClassNotFoundException: UnitTenantProjection`). The failed log is preserved as `billing-mysql-initial-build-interference-20260907.log`. The successful rerun used completed compilation with no overlapping backend build. Do not run Maven compilation and integration tests concurrently in this checkout.
- Final changed-file lint: zero errors, two existing account-page warnings. Browser screenshots confirm the navigation layout and absence of account creation for read-only roles.

Evidence files are under `D:/SlickHood-Codex/operations/`. No live credentials, customer payments or production configuration were changed by this correction batch.

Full audit: `D:/SlickHood-Codex/operations/BILLING_END_TO_END_AUDIT_20260907.md`.
