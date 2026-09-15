# Wealth audit — 15 September 2026

The later owner-scoped, original-currency goal-edit journey is recorded in [pending audit follow-up](pending-audit-completion-2026-09-15.md), superseding the goal-edit follow-up below.

Status: local changes for the pending deployment bundle. Not pushed or deployed. Earlier Marketplace and team-role work is preserved.

## Findings and corrections

- Summary cards, advisor recommendations, attention items and asset-performance names did not open their working sections. They now navigate to the relevant Wealth section; asset-specific actions carry the selected asset into finance or lifecycle controls.
- Tabs did not provide persistent section navigation. Added whitelisted section hashes, scroll-to-workspace behaviour, refresh/back handling and six sidebar subsection links: Advisor, Assets, Income & debt, Lifecycle, Document vault, and Goals & projections.
- Category shortcuts did not filter the register. They now select the category, filter assets and prefill the category for a new asset where creation is permitted.
- Admin summary and private-document downloads interpreted singleton-array API payloads as objects. Both now use the shared response-envelope helpers, retaining legacy object compatibility.
- The vault opened a new window after an asynchronous request, which browsers can block. A user-click window is now opened synchronously, disowned, then navigated to the owned HTTPS download URL. Failed requests close the window and display guidance. Links are fetched only on request, not exposed in directory responses.
- Admin catalogue controls had no edit path. Added label/description/order/pricing edits, stable non-editable reporting codes, accessible labels, section anchors, validation and duplicate-submit protection. Hide/restore preserves customer assets and history.
- Administration load failures appeared as misleading zero totals. Independent load errors are now visible and retryable; unavailable totals are not rendered as zero.
- Read-only users fetched the write-restricted property-linking endpoint. That request is now made only with asset-management permission; no permission was broadened.
- Successful cash-flow, debt and goal submissions retained financial input values. Those fields now reset after confirmed success; failed operations retain entries. Added debt/currency input constraints.
- Six archive endpoints persisted changes then attempted to wrap a null result with `List.of`, producing an error after a successful save. They now return the normal no-data success envelope.
- Editing an asset could replace a newer current valuation with an older one. Backdated asset edits are rejected; earlier observations can still be added through valuation history without replacing the latest value.
- The advisor ignored the legacy `TRUST` vault category and asked for documents already stored. It now recognises both `TRUST` and `TRUST_DEED`.

## Security and data

Existing owner checks, permission annotations, protected-document storage and archive/history behaviour remain in place. Administration exposes aggregate platform health and catalogue controls, not private portfolios or vault files. No public document access, external messages or automated financial transactions were added.

This Wealth batch requires no database migration or configuration change. Separate pending Marketplace migrations are documented in the Marketplace audit, not applied by this task. No production records were changed.

## Verification

- Backend full regression suite: 933 tests, 932 passed, 1 skipped, zero failures/errors. The first run hit the existing two-second performance timeout during concurrent checks; its isolated rerun passed, followed by a passing complete suite. The timeout was not relaxed.
- Final frontend production-mode build: passed compilation, TypeScript and generation of all 103 pages. This local artifact uses a loopback API endpoint; it is not a configured production release. An earlier rebuild correctly failed its configuration guard when the API URL was omitted; no guard was bypassed.
- Browser regression suite: all 54 tests passed against the final rebuilt artifact, including Wealth journeys/navigation, administration, Marketplace and team access. Coverage includes mobile deep links/refresh/back, real sidebar clicks and active highlighting, asset context, category filtering, private-document link envelopes, admin catalogue edits/load recovery and read-only access.
- Browser tests use mocked API responses against the production frontend build; backend service/controller tests exercise implementation separately. This is not a live production or storage-provider end-to-end test.
- Final full lint and warning-budget check: passed with zero errors and 470/478 warnings. The warnings are existing lint debt; this batch does not suppress the checks. Git whitespace checks passed in both worktrees.
- The new browser suite caught and corrected a genuine Next.js sidebar hash-navigation handoff bug. Two other initial failures were invalid heading selectors for shared card-title components; those selectors now check the actual content and tab state.

## Remaining release checks and decisions

- Run authenticated staging checks against the actual backend and protected-document storage before releasing the combined bundle; confirm popup/download behaviour on target mobile browsers.
- Live market-quote providers, currency conversion, document scanning and signed-link expiry require environment-backed checks; this task did not call production providers.
- Clarify accounting semantics for sold/closed holdings before changing whether such records contribute to net worth. No liquidation, proceeds, tax or settlement policy was inferred.
- Conditional estate-planning recommendations remain informational, not legal or investment advice. Advisor next-action routing uses the existing English recommendation strings; a future structured action-code contract would make this robust to translation.
- Existing goal edit APIs do not yet have a complete user-facing edit journey; converted dashboard totals must not be reused as original-currency edit values without an owned detail contract.

Production-readiness: navigation and the identified defects are corrected locally and automated checks passed. Authenticated staging/provider validation is still required before release. Deployment remains deferred as requested.
