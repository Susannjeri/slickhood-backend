# Tenant onboarding production readiness

Date: 2026-09-08

## Certified journey

1. A landlord opens an available rental unit and selects **Assign tenant**.
2. The landlord enters the tenant email and lease start/end dates. The backend verifies that the unit is active, vacant, rental-only and linked to a valid rental template.
3. The invitation is email-bound, expiring and freezes the lease dates plus the current legally approved agreement-template version.
4. The tenant opens the link. Existing users sign in; new users register, complete tenant KYC and return to the same invitation.
5. The tenant sees the unit, charges and landlord-defined dates. The tenant does not edit the dates.
6. **Initialize lease** creates one active lease/tenancy for that unit and an immutable issued agreement in the same transaction.
7. The tenant opens the protected PDF and either rejects with a reason or signs. A rejection closes that journey and releases the unit for a corrected assignment.
8. The agreement then appears to the landlord as pending countersignature. The landlord cannot sign first.
9. Only after both parties sign does the lease become active for billing and the unit become occupied.

A tenant may hold active leases for more than one distinct unit. A row lock and active-lease check prevent two concurrent active leases for the same unit.

## Migration and rollback controls

Migration V74 adds landlord-defined dates, the frozen agreement-template reference and tenant rejection reason. It also retires active tenant links created before these required terms existed. Those links must be resent from the unit after deployment.

V74 was rehearsed against a schema-only export of the live V73 database in MySQL 8.4. Flyway migrated V73 to V74 successfully and reported no failed or pending migrations. The schema export was deleted after the rehearsal.

Do not reverse V74 after invitations or lease agreements are created. If the application must be rolled back, keep the schema and prefer a forward-compatible backend fix.

## Automated evidence

- Backend unit suite: 786 tests, 0 failures, 0 errors, 1 pre-existing skipped context test.
- Backend integration suite: 10 runnable tests passed, including 8 rental/payment journeys on MySQL 8.4; 1 environment-gated migration test skipped in the normal run.
- Dedicated production-baseline migration rehearsal: 1 passed, V73 to V74.
- Frontend TypeScript: passed.
- Frontend lint: 0 errors, within the established warning budget.
- Frontend optimized build: passed, 93 routes.
- Browser regression against the optimized build: 167 passed, 0 failed, 1 skipped. The skipped test is the Google Maps restricted-production-key check, which must run after the production-domain build.

## Deployment gates

- Take a fresh database and artifact backup.
- Deploy the backend first and verify Flyway V74, health, readiness and deployed hash.
- Confirm approved residential and commercial agreement templates remain available.
- Deploy the production-key frontend build and verify its commit hash.
- Create a new tenant assignment; do not reuse a pre-V74 link.
- Smoke-test registration/KYC return, initialization, protected PDF, tenant signature, landlord countersignature and occupied status.
- Repeat with the same tenant on a second distinct vacant unit; confirm both leases are visible.
- Confirm another tenant cannot initialize either occupied unit and that cross-account PDFs are denied.
