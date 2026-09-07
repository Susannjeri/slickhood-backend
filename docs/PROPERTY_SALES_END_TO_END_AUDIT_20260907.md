# Property sales and letter-of-offer audit — 7 September 2026

## Release status and scope

Reviewed listing availability, scoped sale properties/units, buyer invitation and continuation, sale stages, offer drafting/issue/signing, PDF viewing, escrow invoicing/reconciliation, milestone evidence, completion and ownership handover.

Corrections are in the consolidated D-drive checkouts based on backend `37c005d9516589bdce854c34e3d4e7286e2a6edc` and frontend `ea2f1e1506824c38b91b432aa13f231c8f81f9e5`. The preceding rental audit corrections are preserved. These are uncommitted working-tree changes, **not deployed release commits**. No live buyer, offer, signed document, payment account or transaction was modified.

No database migration or new runtime secret is required by these corrections. The frontend verification build uses loopback endpoints, not production configuration.

## Confirmed gaps corrected

| Gap | Correction |
| --- | --- |
| The sales manager's letter shortcut disappears after reservation; generic document access was buyer-only. | Persistent sale-scoped letters/agreement/signing-status link for viewers, including managers and buyers, across subsequent stages. |
| Sales load failures look like an empty pipeline; pagination can leave old rows visible and late requests can overwrite newer results. | Explicit failure/retry state, loading reset, stale-response protection and proper page transition handling. Existing identity/workspace remount stays in place. |
| Escrow creation reads a single object while the real backend `ResponseDTO` wraps it in a one-element list. Tests incorrectly modeled only an object. | Accept the actual list envelope and the compatible object variant; validate the returned invoice identifier/reference before reporting success. The regression fixture now uses the real list shape. |
| Buyer has no contextual billing shortcut for the linked sale invoice. | Show a Billing shortcut with the linked invoice number; the invoice screen retains server-side participant/payment authorization. |
| An overdue offer displays an actionable issued/partially signed state until another draft triggers expiry processing. | Derive expired status consistently for API and PDF execution record using the Nairobi date. Signed/cancelled history and the stored HTML snapshot/terms remain unchanged. Existing backend deadline checks still reject signing expired offers. |
| Buyer acceptance serializes the internal sale entity, bypassing the list endpoint's internal-notes redaction. | Return a dedicated acceptance DTO containing only sale ID, status, offer amount/currency and acceptance time. Test confirms no internal notes are serialized or deleted. |
| Escrow completion checks invoice property, unit, buyer, amount/currency and paid state but not its payee. | Also require the invoice payee to match the property's owner; a settled invoice for another payee cannot complete the sale. |
| Offer pages do not explain draft, expiry, partial signing and title/payment boundaries clearly. | Add sale-specific status guidance and avoid advertising automatic reservation for expired/cancelled offers. Pending buyer registration is explained on the sales page. |
| Invitation success text claims delivery when only queued. | Say the email invitation was queued; real inbox delivery is a separate operational check. |
| Escrow due dates use host-local date. | Use the existing Nairobi timezone consistently for the seven-day due date. |

## Letter viewing

The shared protected PDF dialog and CSP fix from the rental audit are also used for sale letters and agreements. It downloads through the authenticated API, opens in-page, offers a download fallback, rejects non-PDF error payloads, cleans up blob URLs and resets on identity/role/workspace changes. No access token is put in the viewer URL.

Sale-specific tests exercise the manager and buyer paths from a reserved transaction to its signed letter on a mobile viewport. A backend test creates and parses an actual PDF and asserts the letter title, unit, frozen price and both electronic signature times. Browser fixtures test navigation/viewer behaviour; they are not a substitute for a real-browser inspection of a customer PDF.

## Intended customer journey and retained safeguards

1. **Set up sale inventory:** the property and unit must belong to the sale workflow and the subscribed/authorized workspace. Public listings exclude reserved/completed inventory. Duplicate active sales for a unit are rejected while the unit is locked.
2. **Invite the buyer:** the manager enters the buyer's email. The invitation binds to that email and sale. An unregistered buyer completes the existing registration/KYC flow; the system does not substitute another buyer or bypass verification.
3. **Record the offer:** progress from lead/viewing to offered with a positive amount. The buyer must finish the invitation before a buyer-addressed letter can be generated.
4. **Prepare the letter:** choose the sale, exact offer amount/currency and a future response deadline. Generate a versioned draft from a template whose approval is tracked. Draft review does not reserve the property.
5. **Issue:** authorized issuer issues the document. Unreviewed legal templates remain blocked. Existing email attachment/in-app availability is retained.
6. **Review and sign:** each original party can view the PDF and its signature status. Both signatures reserve the sale automatically; no separate acceptance click is needed in the normal UI. Unlike rentals, the existing offer process allows either party to sign first. Deadline, buyer, unit, sale stage, price and currency checks remain server-side.
7. **Due diligence and agreement:** reservation progresses through evidence-backed due diligence and then a separate sale agreement. A signed letter is not accepted as a signed sale agreement milestone.
8. **Payment:** an authorized operator creates the linked buyer invoice against a verified sales payee account. Provider-confirmed payment is required; a typed reference or a browser return is not proof of payment. The corrected UI handles the actual invoice response.
9. **Completion:** agreement, funding, transfer registration and handover milestones gate progression. Funding is rechecked at completion so refunded/unpaid invoices cannot rely on an old funded milestone. Existing ownership-transfer logic preserves previous ownership records and is idempotent by sale ID.
10. **Document history:** letters and agreements remain accessible to their original parties after reservation/completion. Expired letters remain readable, but cannot be signed or used to advance the sale.

## Verification

- Backend `mvn verify`: **BUILD SUCCESS**. Unit tests: **671 passed, 0 failures/errors, 1 skipped** (672 discovered). Integration tests: **5 passed, 0 failures/errors, 1 skipped** (6 discovered).
- Real Docker-backed MySQL 8.4: all **3 `RentalPaymentMySqlIT` tests passed**, covering repository queries and participant/document filtering; `PaymentControllerIT`: **2 passed**.
- Skips are explicit: the existing application-context test and the production-baseline migration rehearsal. The latter requires a supplied baseline schema; this run is not evidence that production migrations were rehearsed.
- Frontend `npm run lint:ci`: **0 errors; 461 warnings within the unchanged 478-warning budget**.
- Frontend `npm run build`: **passed**, including TypeScript and generation of **93 pages**. This was a loopback-configured verification build, not a production deployment artifact.
- Full browser regression: **122 passed, 1 skipped, 0 failed** (123 discovered; 6.3 minutes). The skipped case requires a configured Google Maps key. This is a local built-frontend suite with mocked API/provider responses, not an authenticated production test.
- After removing a redundant buyer document link, final lint and build passed again and the focused sales/offer/agreement/rental browser rerun passed **21/21 tests, no skips or failures** (56.3 seconds). The duplicate-link absence is asserted for both buyer and sales-manager journeys.
- Both repositories passed `git diff --check`.

## Before deployment / live acceptance

- Review and commit the combined corrections; rebuild with immutable release hashes and correct production endpoints. Preserve rollback artifacts. Deploy backend before frontend.
- On dedicated test records, verify the actual buyer invitation reaches its mailbox and continues through registration/sign-in into the correct sale. Do not mark an SMTP queue operation as delivered.
- Generate and issue an approved offer; inspect both the attached PDF and in-app PDF as buyer and issuer. Confirm property, unit, parties, amount/currency, deadline and signature times.
- Sign as each party, verify reservation happens once, then reopen the same letter after reservation. Verify an unrelated account cannot list, download or sign it.
- Test expired/replaced/cancelled offer cases and retain their history. Do not modify signed snapshots or relax template approval to force a test through.
- Exercise buyer invoice creation with the true backend response, sandbox payment callback and receipt. Confirm the configured payee is the intended recipient, not SlickHood's subscription account.
- Complete the evidence-backed agreement/transfer/handover path using dedicated fixtures. Real legal transfer/escrow arrangements and external registry verification are not established by an application status change.

## Remaining boundaries

These code corrections do not certify real SMTP delivery, live payment callbacks, document-template legal approval or registry/title verification. Those are explicit live acceptance checks. The existing evidence workflow still asks operators for a signed evidence document ID and supporting reference/notes; this audit does not replace it with a new document-upload or legal due-diligence system.
