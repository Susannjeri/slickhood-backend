# Wealth journey audit — 7 September 2026

## Status and scope

Implemented in the existing consolidated backend/frontend release on D:. This improves SlickHood's current Wealth module; it does not introduce a separate system. No source was published, no deployment or restart was performed, and no live user's wealth, documents or balances were changed. Existing pending billing and notification edits were preserved.

Audit path: category administration → add/edit asset → property link → valuation/history → income/expense → liabilities/balance maintenance → deadlines → goals/projections → private vault → owner/role isolation and failure recovery.

Backend baseline: `28363d2a1abab9b6b1dc401947237a04b3355e3d`. Frontend checkout baseline: `8982d18a6c0d2958fdfdd43aa03ebd85a4c2a16e`. The corrections are uncommitted working-tree changes. No migration is introduced.

## Findings corrected

| Finding | Correction / guardrail |
| --- | --- |
| Multi-word asset categories and vault document categories submitted display text instead of canonical codes | Explicit option values; use administrator labels/descriptions without altering stored codes. |
| Static fallback categories bypassed the actual catalogue; market pricing was offered for every type | Load the configured catalogue, block creation when unavailable, preserve a retired category on existing records, and offer market mode only for eligible categories. Manual mode is the default. Eligibility does not promise provider instrument coverage. |
| Failed asset or property-link saves cleared user input | Reset only after a successful mutation; mutation lock suppresses repeated clicks while saving. |
| One failed dashboard request erased the whole page and displayed zero totals | Independent result handling, explicit partial-failure/retry states, no fabricated zero net worth, and stale-response protection. Vault fetch failures are not silently presented as an empty vault. |
| Wealth navigation was dominated by metrics without a clear starting point | Added a four-step roadmap, a currency-labelled category allocation view, searchable asset register, and direct asset-history action. |
| Labels were not programmatically associated with inputs | Stable label/input IDs; read-only users cannot use mutation controls. Backend permissions remain authoritative. |
| Users could add finances but could not inspect the existing ledger or maintain a debt balance | Added valuation history, cash activity, cash-entry archiving, debt register, balance/payment update and liability archiving. Asset switches reset entry forms to the selected asset. |
| Currency conversion silently dropped monthly debt payments | Carry converted monthly payments into analytics; debt service now reduces cash flow and projections. |
| Positive operating income could hide negative cash flow after debt payments | The negative-cash-flow signal uses post-debt cash flow. |
| Not-yet-due invoices, sale invoices and zero pending balances could inflate rental arrears | Count only past-due RENTAL invoices with positive pending balance. Missing legacy due dates are not guessed. |
| Linked property operations remained readable after property membership removal | Recheck current property access before querying live units/invoices. Personally recorded asset values are retained. |
| Adding an older valuation overwrote the current value/date | Keep historical entries; only same-date or newer entries change the current valuation. Manual overrides are marked. |
| Editing asset currency silently reinterpreted historical cash flows and valuations | Reject currency changes for an existing asset; explain the locked field. This is not a currency-conversion workflow. |
| A shared cached market quote could be applied in the wrong currency | Validate quote currency, price and timestamp before changing asset value. |
| A later expiry date hid an earlier due date | Analytics uses the earlier applicable deadline. Expiry-only forms omit empty due-date strings. Complete, reopen and archive actions are visible. |
| A debt-free goal could not target zero | Permit zero for debt-reduction goals only; retain positive targets for other goal types. Explain that this goal uses a remaining-debt ceiling, not historical repayment progress. |
| Goal displays assumed KES and could draw negative progress bars | Show goal input currency, display normalized progress in dashboard currency, clamp visual progress, and expose goal archiving. |
| Vault scanner outages were reported as unsupported media, and optional scanner configuration could allow unscanned storage | Require a CLEAN result for every Wealth vault upload. Infection and scan unavailability have distinct error codes. Neither writes to object storage. |
| Vault read files into memory before checking the size limit; malformed names/categories were poorly handled | Enforce 20 MB before reading, reject invalid/missing names, path separators and control characters, bound notes, and validate null categories safely. |
| Protected download UI trusted any returned URL scheme | Require HTTPS before opening a freshly requested owner-scoped download link. Lists still return metadata without pre-generated download links. |

## Categories

The existing V56 catalogue contains Property, Land, Shares, Funds, Government securities, Other investments, SACCO, Pension, Cash, Business, Vehicle, Digital assets and Other. No historical migration or category code was rewritten. Super Admin can maintain labels, descriptions, display order, active state and market-pricing eligibility. Public users see active catalogue entries; existing assets can retain a retired category.

## Customer walkthrough

1. Open **My Wealth**. Use **Add your first asset** or **Build your asset register**.
2. Choose the category, name, currency, acquisition cost and dated current value. For a market-eligible asset, either keep a manual value or supply a supported exchange, symbol and quantity. A catalogue flag is not proof of a live quote.
3. Save. If a request fails, correct/retry without re-entering the form. Find the saved asset by name or category.
4. Use **View history** or **Income & debt**. Add a dated valuation, record actual income/expenses and inspect the entries. Historical valuations do not displace newer ones.
5. Add a liability and maintain it through **Your debt register → Update balance**. These are record updates, not bank payments. Avoid entering the same loan repayment again as an operating expense when it is already represented by monthly debt service.
6. Use **Lifecycle** to track a due date and/or expiry, then complete or reopen the record. The lead time controls the in-app attention list; it is not a promise of email/SMS/WhatsApp delivery.
7. Set a goal and target date. For becoming debt-free, select debt reduction and target zero. Scenario values are estimates; balances and annual debt payments remain constant in the projection model.
8. Upload a supporting record in **Document vault**. Keep the file if scanning is temporarily unavailable and retry later. Open documents through the protected-record button; do not share the short-lived URL.
9. Archive mistaken entries deliberately. Asset archiving excludes the asset from active views and retains database history; there is not yet a self-service archived-asset restore screen.

Only record the user's own economic interest as wealth. Access to manage a property is not proof of ownership; the property connector currently accepts user-supplied valuations and does not calculate fractional ownership.

## Verification

- Initial focused backend run: 24 tests passed, zero skips.
- Final backend run after the stricter fail-closed scan correction: 723 discovered, 722 passed, zero failures/errors, one existing application-context skip.
- Frontend production build passed with local test configuration. **Do not deploy this loopback-API test artifact.**
- Frontend lint: zero errors, 463 warnings within the existing 478-warning budget.
- Focused browser run: nine passed, covering categories, failed-save retention, create/edit, history, debt update, upload category/failure, mobile layout, read-only controls, legacy records and aggregate-only administration. The suite includes one unrelated Soko admin guard test.
- Final combined browser run: all 22 passed, including the debt-free/expiry-only cases and the pending billing/notification regressions. This is a selected combined suite, not every application browser test.
- Protected-download follow-up: both tests passed, including opening the newly requested HTTPS link against a locally mocked document host (`wealth-protected-download-20260907.log`). No live storage object was accessed.
- Backend and frontend `git diff --check` passed. A mock-data visual preview was inspected; no customer data was used.
- Tests use synthetic local accounts and mocked HTTP/provider data. Service tests exercise ownership, calculations and scan rejection. These are not live customer, Alpha Vantage, S3 or ClamAV endpoint certifications.

Evidence under `D:\SlickHood-Codex\operations`: `wealth-focused-tests-20260907.log`, `wealth-full-backend-tests-20260907.log`, `wealth-final-backend-tests-20260907.log`, `wealth-final-build-20260907.log`, `wealth-final-browser-20260907.log`, `wealth-combined-browser-20260907.log`, `wealth-lint-20260907.log`.

## Remaining release checks and limitations

- Run authenticated live-host owner-isolation and clean/infected/unavailable-scan journeys with designated test assets/documents before promotion. Verify private storage and short-lived URLs using the deployed configuration; do not use real identity or estate-planning documents as test fixtures.
- Verify an actually supported market instrument, provider quota/credential health, quote currency and quote date. The cache checks do not prove provider coverage or eliminate scheduler starvation under a large failure backlog.
- FX uses the existing conversion service. Historical FX snapshots, transaction-level exchange-rate evidence and multi-currency return attribution are not implemented here.
- These are recorded-wealth estimates: no bank/broker reconciliation, amortisation engine, automatic realised sale accounting or fractional ownership model. SOLD/CLOSED legacy statuses need explicit disposal accounting before advertising realised returns; do not assume status alone records sale proceeds or settles debt.
- The vault lists the most recent 200 records; large-register server-side pagination and archived-record recovery remain follow-up work. The asset ledger is not certified for very large histories.
- Deadline notifications, beneficiary management and live investment advice are not added by this UI update. The existing advisor is rules-based record-keeping guidance, not a suitability assessment.
- Goal progress for debt reduction is a remaining-debt threshold ratio; there is no captured initial debt baseline. Do not describe it as a percentage of the original debt repaid.
- Combined release still includes separate pending billing and notification changes. Resolve their documented gates, build immutable production-configured artifacts, deploy backend first, verify health, then frontend and authenticated smoke tests. Do not publish/deploy the dirty checkout as an unreviewed bundle.
