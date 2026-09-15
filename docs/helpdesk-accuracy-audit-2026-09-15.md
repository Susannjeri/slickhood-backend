# Help Desk accuracy and usability — 15 September 2026

## Scope and preserved boundaries

Local backend and frontend improvements. The user's instruction to reuse the existing OpenAI configuration was respected: no key was created, copied, printed or replaced. The configured model remains unchanged (default `gpt-5-mini`). No production access, deployment, database migration, role grant or automatic knowledge publication was performed.

The assistant remains guidance-only. It does not query private properties, orders, invoices, KYC documents or financial records, and cannot approve KYC, confirm payments, sign agreements or execute refunds. Existing case ownership, guest-token expiry, support permissions, internal-note visibility and manual-draft publication controls are preserved.

## Findings and corrections

1. Retrieval previously counted substring matches, including common words. It now matches normalized whole words, removes common question words, weights titles and keywords, normalizes a small set of common inflections, rejects single incidental body matches, and returns at most four articles in stable order. This is lexical retrieval, not a semantic/vector search engine; unfamiliar vocabulary may still require support.
2. Full chapters and previous AI answers could consume context and reinforce unsupported earlier claims. Long chapters now supply bounded, question-relevant excerpts; only recent non-secret customer questions enter the transcript. Internal notes and previous bot claims are not evidence. Truncation is explicitly labelled, and insufficient evidence must cause handoff.
3. Free-text prefix detection was an unreliable handoff contract. Responses now request a strict structured object (`answer`, `needs_human_support`, `article_ids`) and validate field types, bounds and positive article IDs locally. Refusals, incomplete responses/messages and malformed payloads fail closed to existing human support.
4. The previous 700-token output cap could truncate reasoning/output. The cap is now 2,000; incomplete responses are not presented as completed answers. This increases the maximum possible per-answer output cost; neither actual cost nor latency is certified without live evaluation.
5. Citation text alone was not checked. Ordinary answers require an explicit supplied citation; unknown or inconsistent article references are rejected. Only the IDs actually cited are stored on the message. Publication and audience visibility are rechecked after the answer call. This validates source availability, not whether every generated claim is entailed by that source.
6. Five-minute process-local caching could retain guidance withdrawn on another backend instance. Published article reads now use the repository directly. Knowledge load should be observed at production scale before considering a shared, revision-aware cache.
7. Provider failure could paste an entire first article as a supposed answer. The fallback is now a short, honest handoff with instructions to provide non-secret troubleshooting details.
8. Human-case messages unnecessarily depended on AI moderation availability. Existing human-owned/handoff conversations now retain non-secret customer follow-ups without invoking AI. During a moderation outage, the initial non-secret question is also retained for authorised staff; flagged content is not retained or sent to the answer model. This is not bypassing moderation for AI generation.
9. Prompts now distinguish general process/navigation guidance from account-specific decisions, instruct concise clarification rather than guessing, preserve exact button labels, and prohibit invented action/status/deadline claims. These are model instructions, not deterministic guarantees that all high-risk intents will be detected.
10. Customers could not inspect the articles referenced by an answer. Both the floating chat and Help Desk page now provide **Read article** controls, including mobile guest support. The viewer reloads currently published role-visible articles, never the administrative endpoint. Withdrawn/unavailable guidance and fetch failures get readable notices; in-flight viewer requests are invalidated on close/unmount. The floating chat remounts on workspace changes. Billing is recognised as payment context, and Soko's canned escrow-like question was replaced with payment-status guidance.

## Knowledge freshness and deployment

The revised packaged manual remains draft-only. Import preserves existing slugs; it does not refresh, overwrite or publish live articles. Authorised administrators must review the revised guidance against deployed features before updating published knowledge. Source articles may change after any completed response; the viewer explicitly presents current guidance, not an immutable historical revision.

No new environment variables or schema changes are required for this change. Existing credentials, provider URL, model, timeouts and enablement remain in place. A configured compatible OpenAI Responses model/provider must support strict structured output. Unsupported configurations fail closed rather than falling back to unchecked free text.

## Verification

Automated coverage includes lexical ranking, incidental/subword non-matches, excerpt bounds, stable source limits, supported/unknown/missing citations, withdrawn articles, transcript exclusions, moderation/answer outages, role-switch isolation, structured-response/refusal validation, existing Help Desk controller security, manual-draft integrity, customer source navigation and mobile guest source navigation/closing.

Final results:

- Full backend `./mvnw.cmd -q test`: 180 suites, 1,019 tests; 1,018 passed, zero failures/errors, one pre-existing application-context skip. The separately named MySQL `IT` fixtures were not rerun for this change and are excluded from these totals.
- All 40 Help Desk/security/manual tests passed within that run (22 service, seven retrieval, six provider-client, two controller-security, three manual-bundle checks).
- Frontend Help Desk Playwright tests: eight passed, including existing case claiming/reopening/manual import, human handoff, customer source visibility, withdrawn guidance and mobile guest source viewing/closing. APIs were mocked; development CSP/hydration warnings were present and do not establish production defects or production success.
- Scoped frontend lint: zero errors, one existing `requestGeneration` cleanup warning.
- Frontend build: compilation and TypeScript passed; 103 pages generated. A loopback API URL was explicitly used for this local verification, so the generated artifact is **not a deployable production release**.
- Normal backend and frontend `git diff --check` passed.

An initial new transcript test fixture omitted message IDs, causing one test error; it was corrected before the successful full run. All provider requests in these tests use mocks or loopback synthetic responses; no live OpenAI request or production journey was exercised.

## Remaining release checks

- Evaluate real answers against an authorised, representative question set using the existing configured credential; assess factual grounding, escalation correctness, language coverage, latency and cost. Passing contract/regression tests is not a measured production accuracy score.
- Review deployed role-visible knowledge, including stale manual chapters, before publishing revisions.
- Validate the complete pending release bundle, migrations and external notification providers separately. This change alone does not certify the wider pending bundle or the whole production system.
- Versioned knowledge snapshots, semantic retrieval, customer feedback metrics and deterministic comprehensive high-risk intent classification are not part of this bounded correction.

## Official API guidance used

[OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs) establishes the Responses `text.format` schema contract and refusal handling. It explicitly notes that structured outputs can still contain mistakes; structure/citations are guardrails, not a substitute for factual evaluation.
