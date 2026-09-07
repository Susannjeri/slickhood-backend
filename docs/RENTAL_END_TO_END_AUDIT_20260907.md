# Rental lifecycle and lease document audit — 7 September 2026

## Scope and release status

Audited the consolidated release based on backend `37c005d9516589bdce854c34e3d4e7286e2a6edc` and frontend `ea2f1e1506824c38b91b432aa13f231c8f81f9e5`. Corrections are in the corresponding `D:/SlickHood-Codex/releases/consolidated-20260907` checkouts. These are working-tree corrections, not new immutable release commits.

**These changes have not been deployed.** No production customer records, payment destinations, existing signed agreements, or secrets were changed. No database migration is required by this patch. The frontend verification build uses loopback API/site URLs; it is not a production deployment artifact.

Read-only live checks returned health `UP` and backend hash `37c005d`. The live frontend's Content Security Policy contained `frame-src https://accounts.google.com` without `blob:`. This independently confirms that the deployed policy blocks the application's embedded blob PDF previews.

## Confirmed defects and corrections

| Finding | Correction / protection |
| --- | --- |
| Security policy blocks lease PDF previews. | Permit only `blob:` frames in addition to the existing Google sign-in frame origin. Preserve the remaining CSP protections. Browser tests assert the header. |
| Authenticated PDF viewing relies on a popup opened after an asynchronous download. This is vulnerable to browser popup blocking and gives poor mobile recovery. | Shared in-page PDF dialog, loading/error/retry states, download fallback, PDF signature validation, stale-request protection, object-URL cleanup and viewer reset on identity/role/workspace changes. No credentials in document URLs. |
| Unit page labels the current template as the lease agreement. | Explicitly label it as an unsigned template preview; separately link to the unit's draft and signed agreements. |
| Unit document history fetches a page of all documents then filters locally, hiding matching older documents. | Backend party-scoped `unitId` filtering before pagination; unit document pagination and retry controls. |
| Lease operations shows only an initial page and can confuse load failure with no leases. | Server-side pagination, explicit retryable failure, stale-response guard and role/workspace reset. Hide termination actions for non-active states. |
| The legacy lease-view route can render current template data instead of the versioned agreement and denies ended tenancies. | Prefer the original parties' stored agreement snapshot, including after termination. Never substitute an unsigned template for a governed lease. Share electronic execution-record rendering across both PDF endpoints. |
| Recorded pet policy and extra charges are missing from the generated governed lease snapshot. | Include a factual recorded schedule when generating a new tenancy agreement. Existing frozen documents are not regenerated or modified. Approved-template checks remain in force. |
| An ended lease term can reach occupancy/billing activation. | Reject activation when its end date is absent or is not after today's Nairobi date; regression covers no occupancy/billing changes. |
| Rental invitations can be generated for sale, service-charge or occupied units. | Validate an available rental unit before creating/queuing a tenant invitation. Keep recipient-email binding and template requirement. |
| Legacy PDF endpoints lack explicit sensitive-document cache headers. | Add private/no-store and nosniff to agreement and template PDF responses. |

## Journey coverage

| Step | Expected behaviour / evidence |
| --- | --- |
| Landlord setup | Existing rental management-mode and subscription guards retained. Property/unit and batch-unit browser regressions included in full suite. |
| Tenant invitation | Landlord enters recipient email; backend normalizes and binds it to a rental-unit invitation and queues delivery. New positive and negative service tests verify this boundary. Queue acceptance is not proof of actual mailbox delivery. |
| Invitation and onboarding | Existing invitation/registration continuation and tenant-only navigation audited. Tenant must not receive owner catalogue or template-management menus. No real customer's registration/KYC status was bypassed. |
| Initialize lease | Existing invitation-bound draft process and date validation retained; draft does not occupy the unit. Terms cannot be edited after a governed snapshot exists. |
| Prepare and review | Landlord generates a versioned residential/commercial agreement. Tenant can view its draft. A legally unreviewed starter template cannot be issued. |
| Issue and sign | Tenant signs first; landlord countersigns. Original timestamps survive repeated signing, stale terms are rejected, and one signature cannot activate a tenancy. Browser and service tests cover these controls. |
| Occupancy | Both signatures activate the rental and mark the unit occupied. Unit locking rejects a competing lease. Ended terms cannot start occupancy/billing. |
| PDF and history | Both document entry points render frozen terms and execution status. Unit history is paginated. Original parties can retrieve a signed snapshot after termination; unrelated users cannot. Actual PDF generation/text extraction is tested with OpenHTMLToPDF/PDFBox. |
| Rent and reconciliation | Existing rental reconciliation, callback destination/authentication, duplicate-payment, receipt, deposit and late-fee tests included in full backend regression. Real provider calls were not made. |
| Notice and end of term | Existing notice, bounded expiry/renewal and termination processing reviewed. Browser test verifies notice payload. Termination ends future billing and releases tenancy/unit occupancy; historical documents remain available through original-party access. |

## Verification evidence

- Full backend `mvn verify`: **BUILD SUCCESS**. Surefire: 666 discovered, 665 passed, zero failures/errors, one skipped application-context test (`SilveroceanApplicationTests`).
- Follow-up `LeaseJourneyTest` after adding explicit termination/notice-period guards: **11/11 passed**, including the two added tests. Production source did not change after the full `verify` run.
- Failsafe: six discovered, five passed, zero failures/errors, one skipped production-baseline migration test. `RentalPaymentMySqlIT` ran against a real disposable Docker MySQL 8.4 database: **3/3 passed**. `PaymentControllerIT`: **2/2 passed**.
- The production-baseline migration test was not configured in this run. Hibernate-created MySQL integration schema is **not** evidence of a production Flyway migration rehearsal. This patch has no migration.
- Focused browser suite: **14/14 passed**, including desktop/mobile PDF UI, retry, document filtering, tenant status and landlord signing restrictions.
- Frontend `lint:ci`: zero errors; 460 warnings within the unchanged 478-warning budget. This is not a zero-warning codebase.
- Frontend optimized build and TypeScript checks: passed; all 93 static pages generated.
- Full browser regression against that optimized build: **118 passed, zero failed, one skipped (119 discovered; 6.5 minutes)**. The skipped test requires a configured Google Maps production key; this local verification build intentionally does not contain one.
- Browser tests use controlled API fixtures. The PDF browser fixture tests loading and viewer behaviour; the separate backend PDF test generates and parses a real PDF. Neither substitutes for a live authenticated customer journey.

## Required release acceptance (not claimed as completed)

1. Review/commit these working-tree changes; run normal protected release checks. Build fresh artifacts with the correct release hashes and production configuration.
2. Preserve backend/frontend rollback artifacts and deploy backend before frontend, because the frontend now relies on server-side unit filtering.
3. With dedicated test landlord and tenant accounts on the deployed site, send a rental invitation and confirm actual email delivery, recipient binding and registration continuation.
4. Initialize a lease for a dedicated test unit; generate and review an approved draft. Open/download it as each party on desktop and a mobile browser.
5. Sign as tenant, confirm it remains unoccupied, countersign as landlord and confirm occupancy/lease status and the agreed billing schedule. Verify another account cannot read/sign it. Repeat a signature to verify no duplicate activation.
6. Open/download the signed PDF through both the unit and Documents & Notices; verify terms/signature dates match. Inspect the actual deployed CSP.
7. Complete an authorized provider-sandbox invoice/callback/receipt journey using that landlord's payment account, not SlickHood's subscription account. No real customer charge without explicit scope.
8. Exercise notice/end-of-term on dedicated fixtures, confirm future billing stops and the archived signed PDF stays accessible to its parties. Do not alter a real tenancy merely to simulate expiry.

## Operational and policy boundaries

- Signing approval and legal-clause review were not bypassed. Confirm approved tenancy template wording matches the offered lease duration and configured renewal terms before issuing real agreements.
- The existing first-rent-due-date policy and any partial-period/prorated rent expectations need acceptance against the landlord's agreed commercial terms; this patch deliberately does not invent new billing amounts or proration rules.
- Existing pre-governed historical leases without frozen snapshots are not reconstructed into supposedly original signed documents.
- The two-party document model grants access to the recorded issuer and recipient. Delegating issuance/signing to staff needs an explicit authority model if the owner also needs independent access; this audit does not broaden document access to all staff.
- This is a verified code correction with local regression evidence, not a declaration that live email, payments, all historical records or every end-to-end customer journey have passed production acceptance.
