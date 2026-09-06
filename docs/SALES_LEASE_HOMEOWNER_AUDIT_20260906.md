# Property sales, rental leases and homeowner agreements — 6–7 September 2026

## Scope and release status

Audited the existing SlickHood workflows, not a replacement system. All edits, generated builds and validation evidence are on D:. Existing unrelated changes were preserved. No live customer records were altered and no deployment was performed in this audit.

This is a code and local regression audit. Hosted email delivery, buyer/tenant registration and KYC, live payment callbacks, and production document signing have not been certified by these tests. Do not describe the entire platform as production-ready on this evidence alone.

## Property sales journey

1. Select the subscribed Property Sales role/workspace. Create a SALE property and SALE unit; rental and estate records must not enter the sales pipeline.
2. Start a sale using the buyer's email. The existing email-bound invitation flow supports an unregistered buyer; registration/KYC and acceptance attach the buyer to the transaction.
3. Record viewing and the proposed offer amount. An authorised document issuer creates the sale-linked Letter of Offer with the same currency/amount and a future response deadline.
4. An approved template is required for issue. Buyer and issuer review the immutable PDF and sign; both signatures reserve the sale automatically. A separate acceptance click is unnecessary. The compatibility acceptance endpoint is retry-safe once reserved.
5. Conduct due diligence, record its reference and verification notes, then move to Agreement. Create and sign the Property Sale Agreement against the same sale and amount.
6. Create the buyer's contractual payment invoice against the property owner's verified PROPERTY_SALES account, including when an employee started the sale. Escrow funding is accepted only from the linked fully reconciled invoice, not a typed payment reference.
7. Agreement-signed evidence must actually be a signed sale agreement for that buyer/unit/sale. Transfer and handover remain explicit evidence-backed manager attestations. Completion transfers ownership in the same transaction and retains previous ownership history.

### Corrections

- Sales queries and mutations now enforce the active role and exact selected workspace assignment. A dormant sales role or another workspace's assignment is not sufficient.
- A rental-only owner cannot operate a sale property through this path. Self-sales to the property owner are rejected.
- Employee-created sales no longer make the employee the payment beneficiary by default.
- Unsigned documents cannot complete milestones; a letter of offer cannot stand in for a signed sale agreement. Other manual checks require a supporting record reference and verification notes as well as a signed sale-linked document.
- A sale with an active escrow invoice cannot simply be cancelled and leave a collectible/paid invoice behind. Finance must resolve that invoice first.
- Completion now locks and rechecks the linked invoice. A historical funded milestone cannot hide a subsequently unsettled or inactive invoice.
- Public search, listing pages, images/enquiries and filter options exclude reserved/completed sales. Republishing those units is rejected. Estate homes and mismatched property/unit modes cannot be published as rental inventory.
- Expired letters cannot be signed. A replacement generation expires outstanding past-deadline letters while retaining their snapshots.
- Sales has a link to prepare/review the sale agreement. Buyer document links are transaction-filtered; there is no redundant acceptance button.

## Rental lease journey

1. Landlord captures the tenant's email and issues a tenant invitation for a rental unit.
2. Tenant follows the link, registers/completes the required onboarding if necessary, reviews the unit and initializes the lease draft.
3. Initialization locks the unit and invitation, validates RENT mode and an available unit, and consumes the invitation. A non-rental template cannot initialize the tenancy.
4. Landlord prepares a Residential or Commercial Lease Agreement. The form loads the lease's recorded move-in date, rent and currency. The backend verifies those values and prevents parallel residential/commercial agreements for the same lease.
5. Creating an agreement freezes underlying lease terms. To correct an unissued draft, cancel that draft, edit the lease and generate a replacement; the old snapshot remains available. Signed/issued terms must not be silently overwritten.
6. Tenant reviews/signs first. The pending tenant signature is recorded on the lease as well as the document, keeping both screens consistent. Landlord/authorised issuer countersigns last.
7. Only both signatures activate the tenancy, set the unit occupied and schedule billing. Unit locking prevents a competing lease from taking an occupied unit. Legacy direct signing cannot bypass governed documents or use multiple dormant roles to sign both sides.
8. Both parties can use transaction-filtered document links to view signing status and the signed PDF. The existing notice, self-renewal and scheduled termination flows remain; lease reads/messages/mutations now apply current role/workspace checks.

### Additional safeguards

- Signing/issue/acknowledgement lock the document row; repeat signatures retain their original timestamps.
- A GET request no longer signs a legacy lease. Current frontend uses POST.
- PDFs retain the original rendered terms and append the recorded electronic execution status; downloads use private/no-store and nosniff headers.
- Tenant/landlord lease lists are role-scoped instead of using dormant super-admin or unrelated staff assignments.

## Homeowner agreement journey

1. Establish a current homeowner ownership record on a SERVICE_CHARGE property/unit. Historical ownership remains readable but cannot be used to execute a new agreement.
2. Estate Manager selects the particular ownership record, not an ambiguous property/homeowner pair. The unit is included in the generated agreement snapshot. Multiple units owned by the same person are distinct choices.
3. The manager prepares the Estate Residential Agreement with an effective date no earlier than ownership began. Duplicate current agreements for the same property/unit/homeowner are rejected.
4. Issue requires approved wording. The issuer and homeowner review and sign. An ended ownership cannot sign its old outstanding agreement.
5. Agreement signing does not create a tenancy or transfer title. Ownership and service-charge billing remain separate governed workflows. Current and historical agreement access is exposed from the ownership registry.

Current ownership is locked for execution, and the agreement's effective date and creation timestamp must belong to the current ownership record. Reacquiring a unit does not revive a previous unsigned agreement, even one with a future effective date; previous-period agreements do not prevent creating the new period's agreement.

The browser audit also found the estate unit page still used rental controls. It now exposes the email homeowner invitation independently of tenant-list permission, links to ownership/agreements, hides rental tenant/lease tabs for estate/sale units and labels their price correctly. A sale unit links to the sales pipeline instead of suggesting a tenancy.

The MySQL rerun crossed Nairobi midnight and exposed an overdue-status discrepancy caused by the database's UTC date. Service-charge views now calculate status using the same Nairobi business date as estate billing/reminders, with deterministic midnight-boundary regression coverage.

## Shared UI and permissions

- Document types are limited to the active business role; backend role/workspace checks remain authoritative.
- Issuer/recipient-specific actions prevent signing twice, acknowledging one's own issue, or displaying an Issue action to a recipient.
- Legal-review drafts visibly explain why issue is blocked and offer issuer-only draft cancellation/replacement.
- Read failures show retry/error state rather than a false empty list.
- Document choices support loading beyond 100 records. Related documents use server-side transaction filters.
- Workspace/session changes remount sales/document state, and document list requests reject stale responses.
- Super Admin can read and version templates using the template-management permission, without requiring the document-creation permission.

## Validation

Validation completed against the local integration working copies. Logs are in `D:/SlickHood-Codex/validation/`:

- Full backend regression: 642 tests discovered, **641 passed, 1 existing application-context test skipped**, no failures or errors. Integration: **5 passed, 1 existing production-baseline migration rehearsal skipped**, including all **3 Docker-backed MySQL tests**. Maven reported `BUILD SUCCESS`.
- That full run preceded the final ownership-creation-time safeguard. Final-source verification at **00:29 EAT on 7 September 2026** reran all six affected test classes: **52 passed, none skipped**. The integration rerun again had **5 passed, 1 existing migration rehearsal skipped**, with all **3 MySQL tests executed and passed**. Maven reported `BUILD SUCCESS`. The focused run overlaps the full suite; its 52 tests must not be added to the full count as independent coverage.
- Full frontend browser regression: **114 passed, 1 skipped**, no failures. All six new agreement journeys passed. The skipped test requires the restricted production Google Maps key and actual-domain verification.
- Optimized frontend build: **passed**, including TypeScript validation and generation of 93 static pages. This used localhost configuration for testing, not production deployment settings.
- Frontend lint: **0 errors**, 456 warnings within the existing 478-warning budget. This is not a warning-free codebase.
- `git diff --check`: passed in both repositories. Existing unrelated dirty changes remain preserved.

Evidence files:

- `sales-release-verify-final.log`: full Maven regression and disposable MySQL checks, including sales workspace isolation, public inventory withdrawal, document parties/expiry and rental scope alongside estate/payment checks.
- `sales-agreement-final-verify.log`: final ownership-period safeguard verification, affected service tests and repeated MySQL/payment integration checks.
- `sales-complete-verify.log`: earlier successful full regression; superseded by the final run.
- `sales-mysql-final.log`: intermediate MySQL run that exposed the Nairobi-midnight defect; retained as diagnostic evidence, not a passing certification.
- `sales-lint-final.log`: frontend lint budget.
- `sales-build-final.log`: final optimized local-validation build.
- `sales-browser-complete-final.log`: full browser regression, including offers, lease signing status, homeowner selection and estate regressions.
- `sales-browser-final.log`: intermediate focused browser run; diagnostic failures are superseded by the complete final run.

The first focused browser run found a missing estate invitation control (fixed), one expected link changed to a transaction-filtered destination (assertion updated), and a sign-request mock that did not match nested URLs (mock corrected; production signing was not bypassed).

The browser journeys use mocked API/authentication responses. The MySQL tests execute real repository queries and payment reconciliation in a disposable MySQL 8.4 container, with Hibernate-created test tables. They do **not** certify production Flyway migration history. `SilveroceanApplicationTests` remains disabled because it requires configured PMS and audit databases; `ProductionBaselineMigrationMySqlIT` remains opt-in without its external database configuration. Neither skip is counted as a pass.

## Remaining release gates and deliberate limits

- Have the authorised Super Admin record legal review of the exact agreement/template version. This audit does not approve legal wording or establish enforceability.
- Use designated test accounts on the hosted application for invitation email → registration/KYC → buyer/tenant/homeowner access → issue/sign → status/view checks. Do not alter real customer identities or signatures to make a test pass.
- Complete a real approved payee invoice/callback test and confirm SMTP delivery. Local mock browser tests and simulated payment reconciliation are not evidence of provider delivery or real money movement.
- “Escrow” here is the existing contractual invoice workflow, not a new bank-custody or regulated escrow service.
- Due-diligence, registry transfer and handover references remain manual attestations. There is no new land-registry verification integration or supporting-file upload repository in this change. Do not treat a signed offer alone as proof that those external events occurred.
- The production-baseline migration rehearsal and application-context tests were skipped, as recorded above. Their configured environment validation remains outstanding. This audit adds no database migration.
- Deploy a reviewed integration commit, backend first and then frontend (viewer-party fields and ownership selection depend on the backend changes). Rebuild with the restricted production Maps key and production API/domain settings; the local localhost build is not a production artifact. Preserve rollback artifacts and data before any deployment.
