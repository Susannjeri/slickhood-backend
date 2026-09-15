# Reports wiring and validation — 15 September 2026

## Scope

Inspected `/dashboard/reports`, the sidebar link, report API functions/interceptors, `/reports` controller, all 16 report definitions and their data/query paths, export handling, existing tests, and relevant workspace-access implementation. Preserved unrelated pending Marketplace and Wealth changes.

## Corrections

- Fixed the central frontend/backend contract mismatch: `ResponseDTO` returns `data: [report]`, while the screen previously expected `data: report`. The screen now supports the actual singleton-list envelope and the legacy object shape.
- Added persistent generation errors and a Retry report action. Catalogue failure/malformed responses are not presented as an empty/no-access catalogue.
- Invalidated in-flight report requests after date-filter changes, unmount, and account/profile/workspace changes; a previous response cannot restore stale report totals.
- Report workspace UI remounts at the authenticated email/role/selected-workspace boundary so previously displayed data is cleared.
- CSV downloads verify the response content type. Failed JSON/blob responses display the server explanation and are not downloaded as spreadsheets.
- JSON report/catalogue responses now use no-store/no-cache headers, matching the existing private CSV export policy.
- CSV generation now runs inside a read-only transaction, including lazy-loaded entity details.
- Added stable headers for all empty report periods; an empty export still contains its schema.
- Bounded forward-looking lease queries to the same one-year horizon advertised by the UI. Existing historical range limits, row caps and formula-injection protection remain intact.

## Existing catalogue wired

Invoice collections/arrears; payment reconciliation; account statement; lease expiry/renewal; occupancy/rent roll; visitor/gate activity; property sales pipeline; estate service charges; service marketplace operations; Soko orders/delivery; subscription lifecycle; affiliate earnings; KYC operations; notification delivery; smart-gate health; maintenance operations.

No new role grants, permission definitions, subscription entitlements or data-source configuration were introduced. The existing role-filtered catalogue remains authoritative.

## Access-scope finding — approval required

Follow-up: the customer subsequently approved scope-only financial-report isolation, without new role permissions. See [financial-report workspace isolation](financial-report-workspace-scope-2026-09-15.md) for the implementation and latest verification/readiness status. The finding and approval history below describe the earlier snapshot.

Several property report repository queries use any active property-manager assignment for the user, without constraining the active assignment to the selected customer workspace. Delegated finance queries also do not provide a complete selected-workspace reporting path. The account-statement query's platform-privileged branch is broader than the subscription-only platform invoice/payment queries. These require a dedicated access-scope correction and database integration coverage before unrestricted production sign-off.

A combined access-scope/role-access proposal was blocked by the safety review because it changed sensitive financial-report visibility and included additional role eligibility. The partial preparatory query changes were reverted; no access expansion or new workspace-scope query is active. Proceed only after explicit approval for strict workspace scoping, with existing role permissions preserved unless separately approved.

## Validation

Added regression coverage for the real HTTP singleton-list contract, catalogue generation/export across all 16 empty report types, role denial/unknown report codes, bounded forward dates, snapshots, export caps, sidebar navigation, retries, malformed catalogue handling, stale responses and CSV failures.

- Targeted backend reporting/reconciliation tests: 16 passed, no failures, errors or skips.
- Full backend suite: 939 tests across 172 suites; 938 passed, 1 skipped, no failures or errors. Counts exclude one stale XML result from an earlier MySQL integration run.
- Browser regression: all 15 passed (8 reporting workflows and 7 existing notification checks). Two initially ambiguous test selectors were corrected before the successful rerun.
- Fresh frontend production-mode build: passed, including TypeScript and static generation. This local test artifact uses a loopback API URL, not production deployment configuration.
- Targeted lint: no errors; two exhaustive-dependency warnings concerning request-generation refs in effect cleanup remain.
- Both worktrees passed `git diff --check` (line-ending warnings only).

Browser tests use a local server and mocked API fixtures matching the actual controller envelope; they do not certify live production data or external provider behavior.

## Database, deployment and readiness

- Database migrations/configuration changes: none for reporting.
- Production deployment: not performed.
- Working functionality and unrelated pending changes: preserved.
- Release status: display/export fixes passed local validation; selected-workspace financial-report scoping remains an approval/integration-testing gate. The entire module is not yet signed off for unrestricted production use.
