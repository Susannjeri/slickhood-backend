# Silverwood Insurance production readiness

## What the first deployment did not do well

The original insurance delivery was a branded foundation, not an end-to-end module. It configured insurers, payment destinations and encrypted correspondence, but the customer buttons were inert and there were no durable application, quotation, premium, policy, claim or renewal records. Silverwood ownership existed in wording and roles but was not stored as a data boundary. Incoming mail was a manual `RECEIVED_UNVERIFIED` endpoint rather than mailbox ingestion. All five seeded insurers were missing operational email addresses, logos and verified payment configuration.

The deployment process also exposed repeatable release risks:

- A Wealth page reached production with a blank-screen failure because malformed collections/currency values were trusted and the page had no local containment.
- A production frontend build consumed `.env.local`, baking a localhost API address into the session refresh path.
- Duplicate JWT cookies exceeded the proxy header limit and broke login.
- A copied `node_modules` tree contained absolute links to a developer path and resolved a different Next.js version than the lockfile.
- `package.json`/lockfile drift and manually reconstructed standalone dependencies made releases non-reproducible.
- Health checks ran before Spring was ready, producing misleading 502 results.
- Deployment credentials and GitHub Actions secrets were incomplete, so releases depended on manual SCP/build/swap work.
- The shared SSH key made operator attribution weak.
- Lint, database-backed migrations, dependency advisories and feature prerequisites were not consistently enforced before switching live traffic.
- The Maven Git metadata plugin can resolve the primary checkout while building from a linked worktree, falsely reporting the primary branch/commit and `dirty=false`; candidate provenance must therefore be compared with native Git before deployment.

The useful controls were isolated candidate builds, checksums, backups, private-port smoke checks and reversible release directories. Keep those controls and automate them.

## Delivered insurance boundary and journeys

- One persisted agency owner: `SILVERWOOD` / Silverwood Insurance Agency.
- Authenticated customer access without a subscription or insurance role.
- Internal, non-self-assignable Insurance Adviser and Insurance Manager roles.
- Separate manager approval permission for customer-visible quotations.
- Application, quote comparison/selection, manual premium evidence, verification, insurer remittance, policy issuance, claims and renewal state machines.
- Private PDF/JPG/PNG documents, 10 MB maximum, signature validation, SHA-256 checksums, expiring storage links and owner/staff authorization.
- Encrypted, correlated insurer correspondence with transactional outbox delivery, reference validation, replay protection and feature-flagged IMAP ingestion.
- Durable customer email notifications plus selected-quote payment reminders and 30-day renewal reminders.
- Indexed and batched staff queues to avoid per-row query growth.
- Agency-scoped case, payment, policy, claim and renewal queries; one agency cannot leak into another agency's operations totals or queues.
- A centralized case/claim/renewal lifecycle policy, customer self-service withdrawal before payment, and an active-role staff directory rather than raw adviser IDs.
- Mailbox ingestion attributes verified replies to the initiating outbound actor, so scheduled IMAP work does not depend on a request-bound security context.
- Customer workspace at `/dashboard/insurance`; authorised staff workspace at `/dashboard/insurance/operations`.
- Explicit, accessible operation dialogs replace browser prompts; partial API failure preserves successfully loaded customer and staff records.

## Required production configuration

Do not commit these values. Supply them through the existing production secret store:

```text
INSURANCE_MAIL_FROM=info@silverwoodinsurance.com
INSURANCE_REPLY_TO=info@silverwoodinsurance.com
INSURANCE_MAIL_DISPLAY_NAME=Silverwood Insurance Agency
INSURANCE_IMAP_ENABLED=true
INSURANCE_IMAP_HOST=mail.silverwoodinsurance.com
INSURANCE_IMAP_PORT=993
INSURANCE_IMAP_USERNAME=info@silverwoodinsurance.com
INSURANCE_IMAP_PASSWORD=<secret>
MAIL_HOST=mail.silverwoodinsurance.com
MAIL_PORT=465
MAIL_AUTH=true
MAIL_START_TLS=false
MAIL_SSL=true
MAIL_USERNAME=info@silverwoodinsurance.com
MAIL_PASSWORD=<secret>
```

Before enabling IMAP, send a correlated quotation request to a test insurer mailbox and confirm that a reply becomes `RECEIVED_VERIFIED`. Configure and verify quotation, claims and renewal addresses for every active insurer. Do not expose payment options until the linked insurance account is verified.

## Mandatory guarded deployment sequence

1. Deploy only committed hashes from `codex/homeowner-production`; record backend and frontend hashes independently.
2. Confirm no `.env`, `.env.local`, secret, `node_modules`, `.next` or backend `target` content is tracked or included in the source artifact.
3. In a clean, non-worktree candidate directory run backend `./mvnw test` and `./mvnw package`; run frontend `npm ci`, targeted/full lint and `npm run build`. Do not copy `node_modules` from another machine. Compare `git.branch` and `git.commit.id` in the packaged `git.properties` with native `git branch --show-current` and `git rev-parse HEAD`; reject any mismatch.
4. Run `npm audit --omit=dev` and the organisation dependency/security scanner. Resolve critical/high findings or document an approved exception.
5. Back up the database and current release. Validate Flyway through V49 against a production-compatible MySQL clone. Verify the `SILVERWOOD` agency row and that all existing insurers receive its `agency_id`.
6. Reject the candidate if its resolved public API URL is localhost, blank or different from the approved production origin. Keep all `.env*` files outside the candidate archive.
7. Start the candidate backend on a private port. Wait for Spring readiness with bounded retries; then verify health, Flyway V49 and unauthenticated 401/403 behaviour before touching live traffic.
8. Start the candidate frontend on a private port and test login/session refresh, `/dashboard/insurance`, one customer quote journey, and `/dashboard/insurance/operations` with both an Adviser and Manager account.
9. Confirm Adviser cannot approve a quote, verify/remit a premium, issue a policy or manage insurers. Confirm Manager can. Confirm an unrelated customer cannot read another customer's case/document.
10. Switch traffic atomically. Run public health, authentication, static chunk, insurance customer/staff and existing Wealth/Soko/Services smoke tests.
11. Observe application, proxy, outbox, SMTP/IMAP and database metrics for at least 15 minutes. Roll back application releases immediately on elevated 5xx, auth failures, chunk errors or migration-related exceptions. Do not reverse V49 destructively; restore the database backup only under the approved database recovery procedure.

## Acceptance evidence recorded locally

- Backend final full suite: 399 tests passed, 0 failed, 1 existing environment-dependent test skipped.
- Targeted insurance security, state-machine, correspondence, staff-directory and role suite: 18 passed, 0 failed; the final full run repeated the preceding insurance tests successfully.
- Backend compilation: successful in the final full run.
- Frontend clean lockfile install: 548 packages, 0 vulnerabilities reported by npm.
- Targeted insurance lint: 0 errors and 0 warnings.
- Full CI lint budget: 0 errors, 477/478 warnings; the Windows launcher and generated Playwright-report exclusions were repaired so this guard is reproducible locally and in CI.
- Next.js 16.3.3 production build: successful, 79 routes, including both insurance routes.
- Playwright insurance E2E: 4 passed (customer quote submission; payment and withdrawal dialogs; manager operations; adviser permission-aware operations).
- Full Playwright regression: 47 passed, including authentication, estate/homeowner, sales, rental, visitor, Soko/payment, privacy, KYC and Wealth journeys. This pass also found and fixed an existing estate detail request loop caused by shared metadata loading state.
- Performance safeguards: customer case hydration remains a bounded batch-query path; agency dashboards use database counts and paged queues; staff eligibility is a constant-time count query; frontend queue hydration runs independent requests concurrently and preserves partial results.
- The Windows E2E launcher was corrected to spawn `npm.cmd` through a shell on Windows/Node 22.

The production-compatible MySQL migration run, live SMTP/IMAP handshake, authenticated browser E2E, load test and live smoke test remain deployment-environment gates. They cannot be truthfully certified from this Windows workspace because Docker, production credentials and the production host are not present.
