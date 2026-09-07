# Help Desk and manual audit — 7 September 2026

## Scope and status

Audited the existing guest/customer chat, support queue, knowledge publishing and OpenAI client in the consolidated release on D. Existing Billing, Notifications, Wealth, Marketplace, Subscription/Funds/Tax/Access and Admin corrections remain intact. No customer identity, invoice or payment was changed for this audit.

The manual and corrections are release-candidate work. This report alone is not deployment evidence. The subsequent release record must identify immutable commits, workflow outcomes and live hashes.

## Findings corrected

| Finding | Correction |
| --- | --- |
| Detached conversation saves discarded the entity returned by JPA merge, leaving an obsolete optimistic version for later saves within a chat response | Propagate the returned version. Remote AI calls remain outside a long database transaction. Added a merge-semantics unit regression and a real MySQL guest-message/idempotency regression. |
| AI continued replying after human handoff | Customer messages remain in the human-support workflow once escalated/assigned/waiting. A change of active role also prevents old-role transcript reuse for new AI answers. |
| Repeated escalation extended deadlines and could lower priority | Preserve the earlier waiting time/deadline and stronger priority. A new response cycle clears the old breach marker. |
| Empty/malformed moderation response was treated as safe | Require a real boolean moderation result; unavailable/malformed responses fail closed to support. |
| No-source questions could still reach the answer model | Escalate without requesting an ungrounded answer; reject known credential patterns in generated output before storage. |
| A first chat message copied credentials into the conversation subject before message validation | Apply known-secret checks at conversation creation. Retain only a safe page path, stripping query strings/fragments. |
| Signed-in case creation lacked a creation limit | Add the existing rate limiter to authenticated starts. |
| Support UI lacked claim/reopen and pagination controls | Wire existing claim and reopen endpoints, previous/next case pages and explicit refresh. |
| Queue actions and knowledge rights could rely on account-wide permissions | Use active-role permissions; remount the full Help Desk and floating chat on identity/role changes. Hide write controls from queue-only readers. |
| Failed resolves and false-success envelopes were not consistently handled | Preserve case/draft state, display errors, support list and object envelopes and guard repeated writes. |
| Asking for a person first depended on an AI response | Create and escalate a case directly without requiring an AI answer. |
| Failed first message could create another case on retry | Retain the successfully created conversation before sending its message. |
| Expired guest polling silently retained unusable state | Clear expired/not-found guest state and provide a new-session explanation. |
| Knowledge citations lacked visible article identifiers | Display article numbers in Knowledge. |
| Long article bodies and invalid audience strings were unrestricted | Bound request body length and validate exact role names, including Registration guest. |

## Manual deliverables and publishing

- `docs/SLICKHOOD_USER_MANUAL.md`: approximately 8,100 words, 30 numbered chapters, customer/manager/staff steps, success states, recovery guidance, security boundaries, glossary and source traceability.
- `src/main/resources/helpdesk/user-manual.json`: the same chapter bodies as independently searchable knowledge drafts with title, category, keywords, exact audience roles and source references.
- `POST /helpdesk/admin/articles/manual-drafts`: protected by `manage_helpdesk_articles`. Imports missing chapter slugs transactionally as drafts; existing active or inactive slugs are retained. Repeating an import does not overwrite or publish content.
- Frontend: Help Desk → Knowledge → **Import user manual drafts**, available only with article-management permission.
- Review each chapter against the deployed release, confirm audience, then use the existing article editor to publish. Restricted Support/Superadmin chapters must not have a blank audience. Blank means everyone, including guests.
- No existing Flyway migration is edited and no new migration is required. The importer is deliberate, not a startup data mutation. New manual content is not available to customer/AI retrieval until approved and published.

The manual covers account/Google/session recovery; KYC/OCR; packages/add-ons; landlord/tenant and leases; estate/homeowner agreements and charges; sales/buyer offers and completion; payee configuration and billing; Soko and Services; Wealth; Insurance; Affiliate; Community Funds; Tax Assist; visitors/gates; documents/notifications/reports; Help Desk and administrative operations. It distinguishes confirmed outcomes from drafts, queued messages, payment attempts and recorded external transfers.

## Evidence

- Full backend regression after the detached-version fix: 774 discovered, 773 passed, zero failures/errors, one existing application-context skip. `operations/helpdesk-final-backend-20260907.log`.
- First focused browser rerun: 10/10 passed across Help Desk and Admin. `operations/helpdesk-browser-20260907.log`. Full final browser and MySQL results are recorded below when complete.
- Optimised frontend build and TypeScript passed. `operations/helpdesk-final-build-20260907.log`.
- Lint: zero errors, 457/478 warning budget. `operations/helpdesk-lint-20260907.log`.
- Existing user-authorised OpenAI key verified without printing/writing it: one synthetic moderation request returned unflagged; one Responses request using `gpt-5-mini` returned completed text with `store=false`. No customer data was sent. This proves workstation-to-provider access, not the production host's end-to-end chat journey.

Official guidance checked: [OpenAI safety in building agents](https://developers.openai.com/api/docs/guides/agent-builder-safety). The assistant's instructions keep retrieved articles and user text as untrusted reference content, require human handoff for sensitive decisions and provide no account/payment mutation tools. These controls reduce risk; they do not prove immunity to prompt injection or replace authorisation outside the model.

## Remaining acceptance boundaries

1. On the deployed test application, verify guest → registration/sign-in → linked case → Support claim → private note → public reply → email → resolve/reopen. Use designated accounts; do not use customer identity documents or live payments as fixtures.
2. Check actual SMTP delivery and SLA notifications to monitored administrators. Saved or queued is not delivered. WhatsApp remains parked as requested; no webhook authentication is bypassed.
3. Conversation detail currently shows the most recent 100 messages. Case-list pagination is now exposed; older message-history navigation/export remains a follow-up. No historical messages were deleted.
4. Knowledge is a current editable article store, not a full versioned approval workflow. Import deliberately retains existing slugs. Assign editorial ownership/review dates operationally and test restricted-role visibility before publication.
5. Concurrency beyond the regression cases, provider latency under sustained load, cache invalidation across multiple application instances, and complete credential/PII detection need ongoing operational testing. Known-secret pattern checks are not a universal data-loss-prevention classifier.
6. Sensitive decisions are routed using instructions and output handling, not a comprehensive deterministic legal/payment classifier. The chatbot cannot authorise transactions or inspect private business records; staff must use the separately authorised application workflows.
7. Published knowledge must not advertise unverified automatic refunds, external payouts, tax filing, physical-gate safety, WhatsApp delivery or subscriber administration capabilities. The manual explicitly records those limitations.

## Final integration results

- Docker-backed MySQL 8.4: 8 passed, zero failures/errors/skips. Includes detached Help Desk conversation persistence and idempotency. This uses an isolated Hibernate-created schema, not a Flyway migration rehearsal. `operations/helpdesk-mysql-20260907.log`.
- Full browser regression: 162 passed, one conditional configured-Maps test skipped because the local build intentionally has no production Maps key. The deployment workflow runs the configured-key case against the production build. `operations/helpdesk-full-browser-20260907.log`.
- Live-host preflight passed: V72 has no failed migrations; protected runtime configuration, HTTPS/CORS, ClamAV/fresh definitions, AWS CLI v2/STS, S3 temporary write and cleanup, Textract, SMTP, IMAP, OpenAI/moderation and Alpha Vantage checks passed. Paystack was disabled and not tested. `operations/all-pending-preflight-20260907.log`.
- No new Flyway migration is included. Functional real-money and delivery acceptance boundaries above remain; green infrastructure checks do not certify external settlements or mailbox delivery.
- First deployment gate (run 34134153641) stopped before copying any artifact: one deadline fixture called `now()` twice and differed by nanoseconds on Linux. The fixture now derives its due time from the recorded waiting time; the exact deadline assertion and production logic are unchanged. The subsequent workflow must pass before deployment.
