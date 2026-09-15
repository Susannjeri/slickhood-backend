# Marketplace audit — 15 September 2026

Later verified-rider, stock-display and referee fixes supersede the corresponding follow-ups below. See [pending audit follow-up](pending-audit-completion-2026-09-15.md); remaining payment/security gates are not waived.

Status: local correction batch, not deployed or pushed. This is not a claim that the entire Marketplace is production-ready. The previously committed team-role changes remain preserved for the later deployment bundle.

## Scope and approach

Inspected frontend routes, API clients, controllers, service ownership checks, catalogue state changes, document/referee persistence, payment callbacks and finance recording, rider administration, delivery proof, delivery-code recovery and existing automated tests before changing implementation. No production data, payment credentials, live payments or external customer messages were changed.

## Problems corrected

### Rider administration and verification

- Merchant rider identity/vehicle changes could preserve obsolete verification; edits could reset a busy rider. Identity changes now invalidate verification and require admin approval again. Notes-only edits preserve verification.
- Rider mutation and admin decisions now lock the rider row and check both availability and active delivery assignments. Busy riders cannot be edited, removed, reverified or suspended mid-delivery; finish the active assignment first.
- Merchant availability changes cannot reactivate a suspended/unverified rider or override a legacy incorrect busy flag.
- Admin can reactivate a suspended verified rider, subject to the current KYC gate. Negative decisions require a reason. The merchant can see the decision/status and edit a pending rider; the merchant cannot grant verification.
- Verification retries resolve a rider account registered after the merchant originally added the email. Existing active, verified and email-verified accounts are required for activation.
- Assigned registered riders must remain active and verified to accept, collect or confirm delivery. Assignment reevaluates applicable evidence validity.
- Directory/assignment load failures are displayed as retryable errors rather than false empty lists.

### Existing KYC reused; supplemental rider evidence

- Added an owned rider checklist, private evidence upload and Superadmin review panel. This does **not** create another personal/business KYC case or reset approved account KYC, existing roles or account privileges.
- The existing published matrix remains the source of requirements. Rider good-conduct evidence is already mandatory for individuals, with 365-day validity and 30-day renewal lead time in the existing initial matrix. This batch does not overwrite published matrix edits or invent new legal requirements.
- Approved common evidence is reused. Only active rider-specific supplemental document types from the published matrix can be uploaded; identity documents stay in the established account KYC process.
- Private evidence has generated storage keys, content-signature/size checks and the existing malware policy. Review requires a useful note; already-expired evidence cannot be approved. Pending-upload retries reuse the saved record.
- Renewal is document-specific: an unrelated missing requirement must not cause another approved document to be uploaded again.
- Common evidence expiry/reverification dates are checked before evaluating conditional rules. An expired passport cannot suppress an identity-back requirement.
- Verified legacy service evidence maps to canonical matrix types, and common identity/business evidence maps to existing service document types. Rejected or expired provider evidence no longer counts as submission/approval readiness.
- Rider evidence metadata and decisions are audited. Private documents are available only to the rider and authorised Superadmin through the checklist, not merchants.

### Services and Soko catalogue

- Service wizard now obtains readiness from the backend and shows approved/reused, saved/pending and outstanding evidence. Closing and resuming uses saved documents instead of requesting them again.
- Previously unpersisted referee inputs are replaced with actual Add/Edit/Remove API operations. Saved profile referees are reused across services; rejected referees can be corrected and resubmitted, but cannot satisfy readiness until corrected. Approved referees cannot be casually modified.
- Resuming/backtracking an existing draft updates that draft instead of creating another service.
- Service providers can pause/resume listings and edit draft/paused pricing. Resumption checks current verified requirements; editing paused pricing returns it to a draft for review. Admin-suspended listings cannot be resumed by the provider.
- Admin rejection returns an editable service draft instead of effectively removing it. Approval cannot skip KYC because a provider profile is missing/inactive. Service mutations use row locks.
- “Edit Profile” now calls an owned update endpoint, preserves original consent and receiving account and cannot reactivate a blacklisted provider. Coordinates and business name are validated. The unnecessary external browser IP lookup was removed; the backend already records consent IP.
- Deleted Soko products cannot be revived through edit, publication or image operations.
- A saved product retains its ID if image upload fails, so retry updates the original draft rather than creating a duplicate.
- Shop publication/approval/reactivation and product reactivation check their applicable published KYC scope.
- Admin Soko summary now reads the actual singleton response envelope.

### Payments and delivery

- New Soko orders snapshot receiving-account ID/channel at checkout. Order views no longer substitute a shop's subsequently changed receiving account. Missing historical accounts do not crash channel lookup.
- Existing callback authentication, destination/amount checks, checkout idempotency, stock reservation and buyer-only encrypted delivery-code safeguards are retained and covered by the regression suite.
- Confirmed full authorised service-booking refunds mark the booking payment refunded and cancel uncompleted work. Exact confirmation retries remain idempotent even after the payment status changes. This records an externally completed operation; it does not issue refunds or hold customer funds.
- Rider wrong-code retries reuse saved delivery proof instead of attempting another forbidden proof upload. Delivery progress, failed attempts, returns and successful verification receive audit events and existing buyer status emails.

## Database/configuration changes

- V84: additive `pms_soko_rider_credential` table for private supplemental evidence, status, expiry and review metadata, with user foreign key.
- V85: nullable `payment_account_id` and `payment_channel` snapshot columns on Soko orders. Historical orders are backfilled with the currently configured store account once. **The original historical destination cannot always be reconstructed**; review historical payments before relying on that backfill for financial reconciliation.
- No production migration has run. No credential/provider configuration changes. Existing subscription billing, direct-to-merchant payments, off-system refunds/payouts and grocery-only policy are preserved.
- V84/V85 applied successfully to an isolated MySQL 8.4.11 fixture database, including record insertion and a shop-account-change assertion proving the order snapshot stayed unchanged. This is a migration smoke test, not full production-schema or transactional integration certification. The temporary server was shut down and its test fixtures retained under `target/marketplace-mysql-test`.

## Verification

- Final backend `mvnw -q test`: 930 tests, 929 passed, 1 intentionally skipped application-context test; zero failures/errors. A stale integration-test XML was explicitly excluded from these counts.
- Frontend lint: zero errors; 470 warnings within the existing 478-warning budget.
- Frontend production build: passed, including TypeScript checking and generation of 103 pages.
- Final Chromium browser regression run: 37 passed, zero failures (Marketplace audit, payment, governance, rider correction, Soko delivery/admin and earlier team-access/shared-role regressions).
- Browser tests mock backend/provider responses; they do not prove a live payment provider, private-storage deployment, email delivery or production permissions configuration.
- The first browser run exposed five fixture/selector failures: a missing service-category route permission, the global route-announcer alert matching an error assertion, a common-KYC mock masking approval, a transient-toast role assertion and an obsolete summary response envelope. Fixtures were corrected to match the implementation's actual permissions and response contract; application security was not relaxed to make tests pass.

## Remaining blockers and decisions

1. **Soko refund/reversal consistency — not corrected in this batch.** Authorised full manual Soko refund recording can still leave the order marked paid/fulfillable; provider-confirmed refund/reversal can leave delivery-code/rider/reserved-stock state inconsistent. A combined correction was rejected by the safety review because of its wider payment/stock/delivery impact. Separate approval and focused transactional tests are required. Do not declare the entire Marketplace ready while this remains unresolved.
2. **Rider KYC notifications — excluded.** A proposed email/in-app change carrying verification status and review notes was rejected by safety review because that sensitive payload was not specifically authorised for those destinations. Existing private review/checklist and audits work; new upload/review notifications are not implemented. Recommended approval scope: generic action/status alerts only, with no documents, identity data or review notes in notifications.
3. **One-off courier policy.** Existing external/one-off courier dispatch remains available; it does not use the registered-rider KYC flow. Decide whether to retire it or define a separately verified carrier process. It is not represented as a verified SlickHood rider.
4. **Failed/returned groceries.** Decide the controlled retry/reassignment process and whether returned perishable stock may ever be made saleable. No automatic resale of returned groceries was added.
5. **Catalogue follow-ups.** Services still use category-level descriptions rather than independent listing descriptions/images. Soko variation JSON can display stale variant stock after stock changes even though locked checkout uses authoritative variation rows. These are not claimed corrected here.
6. **Custom service matrix and referee integrity follow-ups.** Additional matrix requirements are enforced at approval/resumption, but arbitrary custom requirements do not yet have a complete service-wizard upload/review mapping. Stronger duplicate-contact/self-referee checks and admin verification-state validation need separate completion/testing.
7. **Release validation.** Apply additive migrations to a full staging copy, validate JPA queries/locking and storage/malware configuration, run browser-to-real-backend journeys and sandbox payment callbacks, and confirm deployed authorities. The current unit/mocked-browser/migration-smoke evidence is not a substitute.

Production readiness: **not ready for a whole-Marketplace completion sign-off**. Local corrections are being verified for a later deployment bundle; no deployment occurred in this task.
