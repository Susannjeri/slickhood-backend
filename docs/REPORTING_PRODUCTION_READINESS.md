# Reporting production readiness

## Runtime controls

- Every reporting endpoint requires an authenticated session. Report definitions then enforce the active-role allow-list.
- Ordinary users only see records they own, occupy, bought, sold, requested, or manage. `FINANCE` and `SUPER_ADMIN` retain cross-workspace operational reporting where explicitly defined.
- KYC operations are restricted to `SUPER_ADMIN`; customer identifiers and contact details are not included in report rows.
- Interactive reports return at most 500 rows. CSV exports return at most 5,000 rows. Each query requests one extra row so truncation is detected without an unbounded count.
- Historical ranges cannot end in the future and are limited to 366 days. Lease expiry uses a forward-looking 90-day default. Snapshot reports ignore date filters.
- Currency amounts are grouped by currency; unlike currencies are never summed into one misleading total.
- CSV cells whose first non-whitespace character is `=`, `+`, `-`, or `@` are neutralized before download.
- CSV responses are non-cacheable, use `nosniff`, and expose `X-Report-Truncated` and `X-Report-Row-Limit` so the UI can warn about incomplete exports.
- Migration V52 adds report-specific date indexes to keep bounded queries efficient as operational tables grow.

## Staging release checks

1. Deploy the backend and confirm Flyway applies V52 successfully. On a production-sized clone, record migration duration and inspect the slow-query log during each index build.
2. For landlord, tenant, homeowner, property manager, sales agent, service provider, merchant, finance, and super-admin accounts, compare the catalogue with the expected active-role reports.
3. Seed two unrelated properties and businesses. Confirm each ordinary role cannot see the other party's invoices, payments, leases, visitors, sales, service bookings, orders, maintenance, or affiliate rows.
4. Confirm only the system owner can open `KYC_OPERATIONS`; direct requests from every other role must be rejected.
5. Generate historical, forward, and snapshot reports. Confirm future dates are available only for forward reports and that local calendar dates are unchanged around midnight in the Africa/Nairobi timezone.
6. Load more than 500 qualifying rows. Confirm the page stops at 500, displays the truncation notice, and does not attempt to render thousands of rows.
7. Load more than 5,000 qualifying rows. Confirm the export returns 5,000, sets `X-Report-Truncated: true`, and the browser warns the user to narrow the range.
8. Export references beginning with whitespace followed by `=`, `+`, `-`, and `@`. Open the CSV in the supported spreadsheet clients and confirm none execute as formulas.
9. Confirm CSV responses are not stored by browser, CDN, or reverse-proxy caches and that report downloads are absent from access-log query payloads.
10. Run the backend test suite, frontend type-check/lint/build, report browser tests, dependency audit, and the existing release regression suite before promotion.

## Observability and rollback

- Alert on report endpoint 5xx rate, p95 latency, database timeouts, and repeated truncated exports.
- Log report code, role, duration, row count, limit, and truncation status; never log row contents or CSV payloads.
- If V52 causes unacceptable migration locking, stop promotion and restore the previous application release. Do not remove a successfully built index during peak traffic; schedule any rollback migration separately.
- Frontend and backend are compatible only after the backend exposes `dateMode` and `rowLimit`. Deploy backend first, validate catalogue and one report per date mode, then deploy frontend.
