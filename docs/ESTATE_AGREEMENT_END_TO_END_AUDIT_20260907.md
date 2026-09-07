# Estate management and homeowner agreement audit — 7 September 2026

## Status

Code audit and corrections in the consolidated D-drive checkouts, based on backend `37c005d` and frontend `ea2f1e1`. Rental and sales audit changes are preserved. These are uncommitted working-tree corrections, not deployed commits. No live ownership, customer document, payment or account was modified. No new migration or secret is required.

## Confirmed corrections

| Finding | Correction |
| --- | --- |
| A home's View agreements link only filters by estate, mixing documents from other homes visible to the same participant. | Carry property and unit IDs through to the existing participant-scoped, server-paginated document query. Residential-agreement creation carries the same home context. |
| A linked ownership outside the first options page is replaced by an option whose value is a property/customer pair, not the actual ownership ID. This also breaks for someone owning several homes. | Preserve the exact ownership ID, carry its start date from the registry, scope choices to the linked estate and validate required ownership context before submission. A registry-linked form locks the selected ownership so a change cannot create a different home's agreement under the old view filter; standalone document creation still allows choosing a homeowner. Backend validation remains authoritative; URL parameters do not grant access. |
| A template-service failure is treated as a document-list failure. | Separate template errors and retry from document availability. Existing agreements remain viewable without a successful template request. |
| Document reloads leave stale rows/action buttons visible. | Clear old rows during reload, suppress stale results and invalidate outstanding requests on unmount. |
| Estate signing status is generic and does not explain its distinction from rentals or sales. | Estate-specific draft, partial-signature, completed and historical guidance; identify the home/unit and explain that signing does not create a tenancy, transfer ownership or confirm payment. |
| Current ownership checks at issue/signing do not recheck active estate inventory. | Require an active SERVICE_CHARGE property and, where linked, an active SERVICE_CHARGE unit belonging to that property before progressing an estate agreement. Original parties retain read access to their historical PDFs. |
| Ownership termination success text claims notification delivery. | Say the notification was queued. Actual inbox delivery is a separate check. |
| Generated-document dates and sale-to-ownership handover dates use the host date. | Use the existing Nairobi timezone. |

## End-to-end journey reviewed

1. Estate manager selects an authorized estate workspace and creates SERVICE_CHARGE inventory. Rentals and sale inventory remain separate. Setup readiness checks homes, a verified operating account, homeowners and an approved current-year budget.
2. The manager sends an email-bound homeowner invitation from the home. The invite creates ownership rather than staff access; repeats are idempotent. Ownership changes preserve the previous record and cannot be future-dated to remove an existing owner's access early.
3. From the exact ownership record, prepare an Estate Residential Agreement with its effective date and optional recorded charge/schedule. A homeowner with multiple homes is selected by ownership ID, not name alone.
4. Review the frozen draft and approved template. Unreviewed templates cannot issue. Draft cancellation retains the snapshot; replacing wording requires a new controlled version.
5. The issuing manager issues the agreement. Email attachment and in-app availability use the existing issue workflow; SMTP delivery has not been certified by this local audit.
6. Original manager and homeowner view the protected PDF, acknowledge where applicable and sign. Either party may sign first for estate agreements. Both signatures complete the agreement; there is no tenancy activation or sales reservation side effect.
7. Service-charge creation is tied to the current ownership, correct unit currency and estate inventory. The existing invoice/payment-account controls remain in place. Homeowner balances use reconciled invoice state and separate currencies; failures are not displayed as zero balances.
8. Budgets progress from draft through approval/closure; homeowners see approved/closed budgets. Meetings require quorum and minutes before HELD; resolution votes are bounded by attendance. Work-order transitions require completion evidence and do not reopen completed work implicitly.
9. Homeowner views are participant-scoped; employee operations use selected workspace permissions. Dormant landlord/super-admin roles do not confer estate access in the active homeowner role.
10. End or transfer ownership with a recorded reason and retained history. Old unsigned agreements cannot be revived by reacquiring the same unit. Original parties may still read their historical signed document; ending ownership does not erase evidence or historical financial obligations.

## Regression evidence

- Backend `mvn verify`: **BUILD SUCCESS; 675 unit tests passed, 0 failures/errors, 1 existing application-context test skipped** (676 discovered).
- Integration: **5 passed, 0 failures/errors, 1 skipped**. All 3 Docker-backed MySQL 8.4 repository tests and both payment-controller integration tests passed. The skipped production-baseline migration rehearsal requires a baseline schema; this is not a migration certification run.
- Frontend lint: **0 errors, 462 warnings within the unchanged 478-warning budget**. Build passed TypeScript and generated **93 pages**, using loopback verification configuration.
- Full browser regression: **126 passed, 0 failed, 1 skipped** (127 discovered, 5.6 minutes). The skipped case requires a configured Google Maps key.
- Final selected-home lock verification: fresh lint/build passed with the same warning budget and 93 generated pages; **21 focused agreement, estate and homeowner browser tests passed, 0 failed/skipped** (1.4 minutes). Both repositories passed `git diff --check`.

New cases cover mobile manager/homeowner PDF navigation, exact selection beyond the first page, template failure recovery, retired/reclassified inventory rejection, both-party estate signatures and actual generated PDF contents. Existing rental, sale, estate billing, setup, ownership, role isolation and operations cases are included in the combined regression.

## Release and live acceptance boundaries

- This is a source/service audit plus local automated testing, not a completed authenticated production journey. Browser tests use mocked API/provider responses; PDF content tests use the actual renderer and PDF parser.
- Build immutable combined release commits with production configuration before deployment. The verification frontend uses loopback endpoints. Preserve rollback artifacts and deploy backend before frontend.
- On dedicated records, confirm homeowner email delivery, registration/KYC continuation, exact home assignment, approved agreement issue, both-party PDF viewing/signing, unauthorized-user denial, service-charge invoice/payment callback/receipt, and ended-ownership read-versus-mutation behaviour.
- Review the existing legal template and estate-specific charge basis with the responsible estate administrator. This audit does not provide legal approval or infer that every billed charge is authorized by a budget/resolution.
- Agreement history remains limited by the existing original-party authorization. This release does not introduce staff reassignment of an issued agreement, amended-agreement workflows or an automatic cancellation policy for signed documents.
- Current estate agreement lookup still examines active ownerships in the estate; this audit does not certify high-volume/concurrent production load or turn manually recorded budget actuals into an expense ledger.
