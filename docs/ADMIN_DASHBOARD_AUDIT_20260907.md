# Admin dashboard wiring audit — 7 September 2026

Status: local candidate, not deployed. Existing Billing, Notifications, Wealth, Marketplace
and five-module audit changes are preserved. No live records, credentials or server settings
were changed. Local outputs remain on D.

## Corrections

1. **Admin totals:** `Superadmin` was serialized as `SUPERADMIN`, but the backend enum is
   `SUPER_ADMIN`. The dashboard now sends the correct enum. The backend continues validating
   the requested role against the authenticated active role.
2. **Response contracts:** dashboard totals accept the supported object and array envelopes.
   Missing/error payloads produce an error, not fabricated zero metrics.
3. **Scope changes:** totals and operational reports reload for session/token, role, selected
   business area and workspace changes. Old totals are cleared and obsolete requests ignored.
4. **Admin navigation:** Admin Panel previously duplicated Users & Staff. It now opens the
   Administration directory on the dashboard. The directory uses existing sidebar definitions,
   filters by active-role permissions, and is not rendered for ordinary roles.
5. **Discoverability:** add Insurance Operations to navigation and the admin directory;
   expose catalogue, payment, review, security and settings destinations in grouped links.
   Settings navigation requires the same view permission as its API.
6. **Role-appropriate actions:** remove Super Admin from Upgrade Plan and replace its
   rental-style creation/quick-action prompts with administration functions.
7. **Honest insights:** report samples are labeled Operational highlights, not a chronological
   event log. Lease-action panels require a successfully loaded lease report, rather than
   saying there are no pending leases when no report was loaded.
8. **User metric:** `getInActiveUserPercentage` previously returned the active percentage;
   the UI inverted it again. Correct the inversion, and make the empty-user query safe.
9. **Subscription metric:** aggregate paid active invoices per currency in SQL, rather than
   returning individual amount/currency projections in a Set. Exclude inactive invoices.
   The UI accurately labels the measure as paid subscription invoices *issued this month*;
   it is not a cash-receipts-by-settlement-date or net-refunds metric.
10. **Configuration maintenance:** remove the unscoped persistent browser cache and delete
    its legacy entry; gate reads and edits separately; load/refresh individual values with
    explicit errors; retain drafts after failed saves; guard repeated actions; handle a
    successful save followed by a failed refresh without saying the save failed.
11. **Secret handling:** encrypted settings are replacement-only in the UI, with empty
    password inputs. No automatic decryption, persistent storage, or raw request-error logs.
    The legacy decrypt endpoint now requires EDIT_CONFIG, not VIEW_CONFIG. Value/decrypt
    responses carry `Cache-Control: no-store`. Recipient credentials remain in Payment Setup.
12. **Numeric settings/audit:** handle integer settings with null string storage, display
    zero correctly, reject malformed/negative integers and validate missing request fields.
    Configuration audit JSON now escapes strings, handles nulls and masks encrypted values.

## Admin function inventory

The directory routes to the existing modules; it does not replace their authorization or
claim that every downstream workflow has been certified in a live environment.

| Group | Connected functions |
| --- | --- |
| People & verification | Users & Staff, KYC Reviews, Team User Types, Help Desk, Privacy Centre |
| Finance & subscriptions | Subscription catalogue, SlickHood Accounts, recipient accounts, invoices, payments, Affiliate Management, Community Funds, Tax Administration |
| Property & marketplace | Property Type Catalogue, listing moderation, Estate Management, Property Sale Management, Service Management, Soko Management, Insurance Operations, Wealth Management |
| Security & operations | Visitors, Visitor Management, Smart Gates, Documents & Notices, Notifications, Reports, Audit Logs, Global Config, User Parameters |

Controls are omitted when the current role lacks their required permission. A displayed
link is navigation only, never proof of backend authorization. The existing server guards
must remain in place.

## Verification

- Backend: 764 tests discovered, **763 passed**, no failures/errors, one existing application-context
  test skipped. Log: `D:/SlickHood-Codex/operations/admin-backend-20260907.log`.
- Docker MySQL 8.4: **7 integration tests passed**, none skipped. This includes empty/populated
  user aggregates and repeated invoice amounts grouped by currency, excluding inactive invoices.
  Log: `D:/SlickHood-Codex/operations/admin-mysql-20260907.log`.
- Frontend: final optimized build and TypeScript passed; 93 static pages generated.
  Log: `D:/SlickHood-Codex/operations/admin-final-build-20260907.log`.
- ESLint: zero errors, 456 warnings within the existing 478-warning budget.
  Log: `D:/SlickHood-Codex/operations/admin-lint-20260907.log`.
- Both repositories passed normal `git diff --check`.

The navigation-derived proxy guard now recognizes Insurance Operations and rejects an
unauthorized direct visit before rendering the page. Negative tests assert a dashboard
redirect and **zero protected API requests**, rather than expecting an in-page denial.
Configuration has both this proxy gate and a reactive client permission boundary.

Browser regression: **29/29 passed**, including admin totals/object envelopes, directory
destination existence, unauthorized direct-route rejection with zero protected API requests,
read-only settings, secret replacement without decryption/storage, save-error recovery,
dashboard retry, users, KYC correction, subscription catalogue, Soko/Wealth administration,
Insurance operations and policy/claim workflow, Help Desk, Reports, Tax Administration and
team-access revocation. Log: `D:/SlickHood-Codex/operations/admin-browser-final-20260907.log`.
Browser tests use synthetic sessions and mocked APIs; they do not replace live authenticated
or provider integration evidence. Destination-file existence is not a full write-workflow test
for every directory entry.

## Remaining boundaries

- Live authenticated Super Admin and restricted staff smoke tests remain necessary before
  release, including role/workspace changes and representative writes with audit evidence.
- The previous subscription audit's full subscriber-operations workbench is still absent;
  the directory deliberately calls the current screen **Subscription catalogue**.
- No provider payments, refunds, notifications, physical gates, live KRA connector or
  deployment were exercised. WhatsApp remains parked under the user's earlier decision.
- Configuration maintenance still requires operational review of safe value ranges,
  HTTPS/host allowlists, restart requirements, maker-checker approval and rollback. This
  patch is not a comprehensive runtime configuration policy or an encryption-key rotation UI.
- Existing platform-staff dashboard workload definitions, currency conversion availability,
  and large report queries need separate live performance/permission review. Admin invoice
  totals are not a substitute for the reconciliation ledger.
- MySQL integration uses an isolated Hibernate-created schema, not a production Flyway
  migration rehearsal. No production-readiness or deployment claim is made here.
