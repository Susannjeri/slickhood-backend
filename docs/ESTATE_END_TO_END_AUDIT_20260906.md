# Estate management audit — 6 September 2026

## Scope and release status

Audit of the existing Estate Management and Homeowner journeys, not a replacement system. Changes are in the D-drive backend/frontend working copies on `release/helpdesk-listings-tax-integrated-20260902`. Existing unrelated changes were preserved. No hosted records, credentials, property classifications or payment accounts were changed. No deployment was performed by this audit.

The local browser journeys use mocked API responses and synthetic authentication. MySQL persistence tests use a disposable MySQL 8.4 container, not the hosted database. These are regression evidence, not proof of live SMTP delivery, registration/KYC completion or provider settlement.

## Findings corrected

| Finding | Correction / guardrail |
| --- | --- |
| Estate owners could receive empty ownership and charge lists because queries required a staff assignment. | Owner-aware queries use property ownership directly; delegates use their active role and selected workspace assignment. |
| Generic staff/owner checks could admit the wrong active business role or another workspace. | Shared `EstateAccessService` requires active SERVICE_CHARGE properties, the requested permission, and an exact selected membership/role assignment. Dormant roles do not grant estate access. |
| Authorized delegated estate managers could reach a home but could not email a homeowner invitation. | HOMEOWNER invitations use scoped unit lookup and MANAGE_ESTATE checks. The controller permits only this invitation type through that permission. Tenant and internal-staff invitation permissions were not broadened. The UI exposes the matching action. |
| Invitation acceptance accepted any lingering staff assignment as inviter authority. | Acceptance revalidates an active inviter: property owner, current Superadmin, or active estate-operations membership in the property's owning estate workspace. Suspended membership is rejected even with a stale property assignment. |
| Ownership could activate immediately with a future start date or a rental/sale unit. | Reject future activation and non-SERVICE_CHARGE/mismatched units. Lock current ownership during transfer/end/charge creation. Preserve historical ownership and its bills. |
| Charge authorization incorrectly depended on MANAGE_ESTATE. | Charge creation checks CREATE_SERVICE_CHARGE, current ownership, an active estate home and matching currency. Validate monetary precision and currency format. No fabricated paid state. |
| A failed billing call could hide valid ownership data. | Independent, retryable feeds for ownership, active homeowners and service charges. Old responses cannot replace a newly selected estate's data. |
| First-page-only results hid owners and operations; old form state could remain selected after changing estates. | Load-more pagination for registry, current homes, charges, meetings, budgets and work orders. Reset invoice recipient, assignment and operations forms when the estate changes. Preserve the selected operations tab during pagination. |
| Paid charges appeared as zero; missing billing data could look like a zero balance. | Display the paid amount, show unavailable/loading for failed/pending totals, and explicitly label totals as covering loaded charges. Currency groups stay separate. Invoice links respect invoice permissions. |
| Draft estate budgets were returned to homeowners. | Homeowner budget queries return APPROVED/CLOSED only; managers retain draft access. |
| Setup accepted unverified or inactive payment accounts and draft budgets. | Readiness counts only verified active operating accounts owned by the property's owner in the correct business area, and an approved budget for the current Nairobi year. |
| Read-only estate staff could be admitted to the page but not see its sidebar entry. | Align role visibility while retaining permission and subscription-feature checks. No extra finance or management permissions were granted. |

## Intended journey and checks

1. **Manager access:** select Estate Management and, for delegated staff, the correct workspace. Its owner's estate subscription remains required by the existing server-side subscription interceptor. A rental subscription must not create an estate or unlock estate management.
2. **Setup:** create/select a SERVICE_CHARGE property, create its homes, attach and verify the appropriate collection account, configure an agreement, approve the annual budget and complete the setup checklist. Existing AWS properties must not be relabelled solely to populate a dropdown.
3. **Invite:** select a home and enter the homeowner's email. The system queues an email-bound invitation. New recipients register and complete the required approval process; existing recipients sign in. Acceptance checks the recipient, token validity and current inviter authority. This creates ownership, not a staff assignment or rental lease.
4. **Resident view:** homeowner sees their current and historical ownership, their invoices/charges and current-estate operations. They cannot manage other residents, budgets or common-area work. Ending ownership removes current-estate access without deleting their historical bills.
5. **Billing:** select a current homeowner/home, enter the amount, matching currency, due date and description. Create an invoice, then use Invoices for checkout/payment documents. Payment state comes from invoice reconciliation, not a client success message.
6. **Operations:** schedule a meeting, record attendance/minutes, record resolutions and votes; create budget lines, approve budget, record actuals and close it; move work through acknowledgement, progress and completion with resolution notes. Existing quorum, vote-limit and state-transition checks remain in force.
7. **Termination/transfer:** require a valid date and reason, preserve ownership history and invoice references, and queue the notification. A transfer must not overlap or predate the recorded ownership interval.

## Automated verification

Logs are under `D:/SlickHood-Codex/validation/`.

| Gate | Result |
| --- | --- |
| Full Maven verify | BUILD SUCCESS; 616 unit/service/controller tests: 615 passed, 1 existing skipped application-context test. Integration phase: 4 passed, 1 skipped historical-schema rehearsal. |
| MySQL estate and rental persistence | Final rerun: 2 passed, 0 failed, 0 skipped against MySQL 8.4.11, including current inviter authority and suspension. Cleanup now closes database resources before the disposable container, preserving Spring's test context through its after-class listeners. Final run has clean shutdown and BUILD SUCCESS. |
| Optimized frontend build | Passed compilation, TypeScript and route generation using localhost validation configuration. |
| Optimized-build browser tests | 17/17 passed; estate setup, homeowner invitations, ownership end, financial visibility, operations, pagination, partial failures and stale-response protection. |
| Full frontend lint | 0 errors; 454/478 warning budget. Targeted lint also had no errors, with an existing effect warning in EstateSetupChecklist. |

- `estate-complete-verify.log`: full backend unit/service/controller regression plus integration checks.
- `estate-final-focused.log`: focused estate, setup, invitation and actual method-security tests (44 passed before final acceptance/budget-query changes).
- `estate-playwright-complete.log`: 15 local browser tests passed, including delegated staff submitting the HOMEOWNER invitation payload.
- `estate-lint-ci.log`: complete frontend lint — 0 errors, 454 warnings within the unchanged 478-warning budget.
- `estate-build.log`: optimized local validation build, not a production-configured release artifact.
- `estate-production-browser.log`: 17 fresh optimized-build browser regressions passed, including failed-billing and budget-pagination guards.
- `estate-mysql-clean.log`: final successful disposable-MySQL rerun after correcting test-harness cleanup order (the earlier `estate-mysql-final.log` records the failed context-cleanup attempt, not final evidence).

Commands used, with Maven cache/temp and frontend temp/cache redirected to D:

```powershell
# Backend (D-drive JDK 21 and Maven repository)
./mvnw.cmd -B -Dmaven.repo.local=D:/SlickHood-Codex/maven-repository verify
./mvnw.cmd -B -Dmaven.repo.local=D:/SlickHood-Codex/maven-repository -Dit.test=RentalPaymentMySqlIT test-compile failsafe:integration-test failsafe:verify

# Frontend: local validation configuration, not production credentials
npm run lint:ci
npm run build
$env:E2E_USE_STANDALONE='true'
node_modules/.bin/playwright.cmd test e2e/estate-regression.spec.ts e2e/homeowner-financials.spec.ts e2e/homeowner-operations.spec.ts e2e/estate-setup.spec.ts
```

New database assertions exercise owner visibility without a staff row, exact delegate role/membership isolation, invoice-derived balances, preserved historical bills, verified-account readiness, and suspended-inviter rejection. The database test creates schema with Hibernate; it does **not** certify Flyway upgrades against historical production data.

## Remaining hosted validation / limitations

- On the existing test deployment, run real email invitation → registration/sign-in → KYC approval → homeowner attachment → dashboard. Confirm both recipient delivery and admin visibility; local notification mocks cannot prove email delivery.
- Attach the intended payee account and complete an authorized M-Pesa/Paystack invoice/callback/receipt journey. Repository reconciliation tests do not call a payment provider or move money.
- Read and confirm existing AWS property classifications before any data correction. Rental/sale properties intentionally remain excluded from estate mutation paths.
- The opt-in historical-schema MySQL migration rehearsal remains separate from disposable repository tests. This estate change adds no migration; unrelated pending migrations in the dirty working tree still need their own release validation.
- Capture the exact integrated commits and rebuild with the restricted production Maps key and deployment configuration before release. This audit's localhost build must not be deployed.
- Meeting resolution detail currently remains bounded to its existing 50-row first page; it does not yet have the new load-more interface. Aggregate dashboard totals here deliberately describe loaded charges rather than pretending to be all-record server aggregates.
- Manual service-charge creation uses the existing invoice service. Recurring charge scheduling and durable exactly-once invoice creation/dispatch were not introduced or certified by this patch.

Do not mark the module fully live-verified based only on these local results. Deploy backend before frontend after the integrated release and hosted checks are ready; retain the previous artifacts and do not delete or reclassify resident data as a shortcut.
