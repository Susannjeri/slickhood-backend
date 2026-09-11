# Mixed-use property and multi-role security audit

Date: 2026-09-11

## Outcome

One SlickHood identity can retain several legitimate roles at the same time. Owner/operator roles are additive, and invitation-bound participant roles are attached without removing existing roles. A physical property is now shared across the owner's Landlord, Estate Manager and Property Sale workspaces, while each unit carries its own operational purpose: `RENT`, `SERVICE_CHARGE` or `SALE`.

This flexibility does not combine permissions. Each request is authorized against one selected active role and, where applicable, one explicitly selected staff workspace.

## Corrected behaviour

- Landlord, Estate Manager and Sales Agent roles owned by the same identity see the same physical property portfolio.
- One property may contain rental units, homeowner/service-charge units and sale units.
- New units require the matching active role, subscription entitlement and destination-mode quota.
- Estate, rental and sale workflows select units by `Unit.leaseMode`; the legacy property-level management mode is no longer an authorization boundary.
- Tenant, Homeowner and Buyer access remains relationship-scoped to their leases, ownership records and sale transactions.
- Existing accounts retain earlier roles when another role is added. Tenant and Homeowner remain invitation/relationship driven; owner business roles remain subject to activation, KYC and subscription rules.
- Staff who belong to more than one workspace with the same role must select the workspace explicitly; requests carry that workspace boundary.
- Payment-account choices are filtered to the invoice's business category, with the backend remaining authoritative for legacy response compatibility.
- Active sidebar groups open predictably, so the selected workflow is visible without exposing unrelated functions.

## Unit-category transition guardrails

A unit category can change only while it has no operational commitments. The backend rejects a transition when the unit has any of the following:

- an occupant or tenant assignment;
- lease history;
- a sale transaction or active property listing;
- homeowner ownership history or service-charge records;
- a property payment-account attachment;
- an incomplete bulk-duplication job; or
- an unpaid invoice.

An edit cannot silently move a unit to a different physical property. A permitted category change also rechecks the destination subscription entitlement and quota.

## Validation evidence

- Backend Maven verification: 808 tests passed, 0 failed, 1 existing application-context test skipped.
- Backend integration phase: 2 payment-controller tests passed. Nine Docker/MySQL cases were skipped because Docker was unavailable on the validation PC.
- Frontend Playwright regression: 170 passed, 0 failed, 1 configured-key Google Maps case skipped in the local no-secret build. The no-key fallback and Maps-enabled mocked journey passed.
- TypeScript: passed.
- ESLint: 0 errors, 454 warnings within the 478-warning budget.
- Optimized frontend build: passed; 93 routes generated.
- No database migration is introduced by this release.

The production workflow must still build with the protected production environment, deploy the backend first, require healthy readiness and the expected deployed hash, then deploy and smoke-test the frontend against `https://app.slickhood.com`.
