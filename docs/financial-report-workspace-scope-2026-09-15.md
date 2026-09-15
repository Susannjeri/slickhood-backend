# Financial-report workspace isolation — 15 September 2026

## Authority and scope

The customer explicitly approved strict selected-workspace financial-report scoping without adding role permissions. This follow-up supersedes the financial-report approval gate in the earlier Reports, pending-audit and notification-completion documents. It does not authorise deployment or unrelated payment/refund changes.

Existing report catalogue role lists, controller authentication, customer team templates, permissions and subscription entitlements are unchanged. Unrelated pending changes are preserved.

## Findings and corrections

- Account statements previously accepted any active property-manager assignment, regardless of selected workspace or active role. Their platform-privileged branch could include all customer ledger lines.
- Existing invoice/payment reports had payer/payee checks but no additional selected-workspace boundary. Sale/estate financial report queries also accepted assignments from other workspaces.
- A read-only scope resolver now uses the existing `X-Slickhood-Workspace` selection mechanism for customer staff. Missing selection where multiple memberships exist, forged selection, inactive workspace, inactive/suspended membership, role mismatch and invalid resource scope fail closed. No workspace is created by a report request.
- The selected workspace owner's property IDs are intersected with active assignments bearing that exact membership's negative invitation ID and active role. Selected-resource restrictions are additionally intersected with those live assignments. An empty scope becomes an impossible property ID, never an unrestricted query.
- Invoice collections, payment reconciliation, account statement, property sales pipeline and estate service charges use dedicated scoped repository queries. Scope is applied before ordering/pagination, row limits, totals and CSV generation. Screen and export use the same scope resolver and predicates.
- Invoice/payment payer/payee eligibility is preserved as an additional check. Membership alone does not confer another person's invoice/payment access. Existing delegated invoice/payment visibility gaps are not repaired by inventing new financial permissions.
- Personal tenant/buyer/homeowner financial queries remain restricted to existing own-party criteria. Unrelated employee assignments cannot provide an alternative access path in a personal profile.
- Workspace owners use only their owned properties. Archived owned properties remain eligible for existing financial-history access. Staff require active properties and live assignments. Properties deliberately shared across an owner's business areas are not excluded by the legacy single-category property `managementMode`.
- Platform invoice/payment reports retain their subscription-only boundary. Platform account statements require an invoice-sourced journal linked to a subscription invoice; property/customer, lease and unlinked journals cannot pass the global branch. Platform roles do not receive a global customer-sale or estate-charge financial view through these report queries. No customer membership is inferred from platform status.
- Payment reconciliation excludes inactive source invoices. Stable ID tie-breakers were added to the scoped invoice/payment/sale/estate queries.

## Verification

- Final targeted backend checks: 34 tests passed across five reporting/workspace/controller/payment-journey suites, zero failures/errors/skips.
- Isolated MySQL checks on the final source: six passed, zero failures/errors/skips. Schema generation used Spring-compatible physical naming with fail-fast DDL; there were no schema-creation errors in the final run.
- Full source-frozen backend regression: 997 tests across 178 suites; 996 passed, one existing application-context test intentionally skipped, zero failures/errors. Counts include only results written by the final run and exclude separately executed MySQL integration XML. The targeted ordinary tests are included in this full-suite count, not additional passes.
- Normal Git whitespace checks passed, with only line-ending conversion warnings.

Coverage includes real workspace-header switching, missing/forged selections, inactive/suspended context, selected-resource intersection, cross-workspace own-party records, wrong-role/wrong-membership assignments, database filtering before row limits, archived owner history, platform subscription journal isolation, platform customer-finance exclusion, unchanged invoice/payment party rights, controller authentication, CSV parity, and the existing rental payment/reconciliation journey.

The MySQL test is opt-in and accepts only `jdbc:mysql://127.0.0.1:3416/report_scope_audit`. It executes the exact repository annotation HQL with real entities in a disposable local schema, using Spring-compatible physical naming and fail-fast schema generation. It is not a production Flyway-chain or authenticated production certificate.

The exact loopback fixture data directory was verified before startup/use/shutdown. The server was stopped gracefully after the passing targeted tests; fixture files were retained. No production data or user files were removed.

## Database, configuration and readiness

- Production migrations/configuration/provider changes: none for this follow-up. Existing workspace/membership/property-assignment records are reused.
- No production database access, role provisioning, emails, provider payment operations, commits, pushes or deployment were performed.
- No frontend contract or source change is required. Earlier report screen account/profile/workspace remounting and stale-request cancellation are retained.
- Release requires authenticated staging checks with staff assigned to two workspaces, selected-resource staff, owner/personal profiles and platform finance/Superadmin. Validate the broader bundle's pending V84/V85/V86 migrations separately.
- The approved financial-report scope change is complete locally and passed the checks above. It does not certify unrelated operational report queries, complete Soko refund/reversal cleanup, external provider delivery or whole-system production readiness. It has not been deployed.
