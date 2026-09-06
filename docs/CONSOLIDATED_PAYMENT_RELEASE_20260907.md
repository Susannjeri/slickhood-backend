# Consolidated candidate: payment routing and pending business journeys

Status: local release candidate, not a deployment or live payment certification. D-drive source snapshots and evidence accompany the release manifest. Existing working copies are preserved.

## Scope

This consolidates the pending rental/property/unit, estate, property-sale, offer, tenant lease, homeowner agreement and payment-account fixes over the existing integrated release. It does not replace the existing application with a new system. See `ESTATE_END_TO_END_AUDIT_20260906.md` and `SALES_LEASE_HOMEOWNER_AUDIT_20260906.md` for detailed journeys and limitations. V72 widens the payment-account channel column; backend must precede frontend.

## Payment ownership rules

| Payment purpose | Receiving destination | This candidate |
| --- | --- | --- |
| SlickHood subscription | Verified SlickHood platform account | M-Pesa credentials work without an irrelevant property attachment; Paystack uses the platform integration without requiring a merchant subaccount |
| Rent, estate service charge, property sale | Respective owner's verified, property-attached account | No fallback to SlickHood's subscription destination |
| Soko/Services | Respective verified merchant account | Non-property credential lookup is restricted to approved account categories |
| M-Pesa through a bank paybill | Recipient bank account, with the bank's approved reference and notification contract | Existing generic adapter only; bank-specific certification is outstanding |

Paystack destination selection now rejects an inactive, unverified, wrong-owner or wrong-purpose account. Merchant payments require an explicit subaccount. Verified provider responses must match reference, amount, currency and test/live environment, and callbacks cannot select another payment channel. HMAC verification remains mandatory. These checks do not replace provider onboarding or independently verify that a manually entered subaccount belongs to its claimed beneficiary; the account verifier must confirm that externally before approval.

## Bank-paybill release gate

The existing UI currently constructs `bankAccount#invoiceReference`. That format is NOT universal. Do not activate/verify a bank-paybill account until its bank confirms the exact format and preservation of the invoice reference. Do not substitute an invoice number for the bank account required to route the deposit.

The current adapter expects a trusted notification with M-Pesa receipt, bank transaction reference, beneficiary bank account, invoice reference, amount and time. The recipient account fixed on the invoice must match. Direct and bank notifications use the M-Pesa receipt for duplicate detection. An SMS screenshot, browser return, or customer-entered receipt must never mark an invoice paid automatically.

For each bank, obtain its collection integration specification and beneficiary authorization, implement an adapter for that actual payload/authentication scheme, confirm replay/deduplication and retry handling, and run a bank-supplied sandbox or approved hosted test. The generic shared callback token is not evidence that a bank supports that authentication scheme. Tokens in query strings must be redacted from proxy/access logs; a gateway can translate a bank's authenticated callback into the internal contract without exposing the internal token to a browser.

If a bank does not preserve invoice references or provide suitable notifications, use its supported virtual/reference account facility or a reviewed statement-reconciliation workflow. Until that workflow exists and is validated, keep automated payment status unavailable for that route. Current bank-paybill availability follows the general M-Pesa switch, so do not verify recipient bank-paybill accounts merely because direct STK is enabled. This package does not certify the generic adapter for every bank.

## Sandbox credentials and evidence

The user-provided `E:/sandbox_mpesa_slickhood.txt` and `E:/sandbox_paystack_slickhood.txt` are read only in memory and are NOT included in source, archives, logs or this document.

- M-Pesa file contains a consumer key and secret but no STK shortcode or passkey. The read-only OAuth probe timed out; it did not prove the credentials invalid or valid. Do not invent a shortcode/passkey or copy another recipient's credentials.
- Paystack file contains a test secret, not a live secret. A read-only authenticated API probe returned HTTP 403 without a classified reason. Key acceptance is NOT verified; investigate from the authorized host and Paystack dashboard/support before enabling the account.
- No transaction, fund transfer, provider recipient creation or callback registration was performed by these probes.
- Both dedicated SSH keys available for the approved host were rejected. No host configuration or deployment was changed. HTTPS diagnostic requests from this runner also failed to establish a response; that does not prove the public site is down.

SlickHood's one M-Pesa sandbox is sufficient for its subscription tests. Local role/destination isolation tests do not require creating a sandbox for every landlord. They cannot replace a receiving-account callback test for each supported bank/provider contract.

## Exact configuration and deployment sequence

1. Restore authorized host access. Fetch canonical upstream again and reconcile any newer commits; publish the candidate through the protected review workflow. The older open five-module PRs are not this consolidated source snapshot.
2. Back up database and current frontend/backend artifacts, preserving ownership, mode and checksums. Re-run `scripts/production-preflight.py --help` and follow `PRODUCTION_PREFLIGHT.md`; require V71 before this rollout and V72 afterwards. Do not repair or alter Flyway checksums to force success.
3. Configure Paystack through the protected backend runtime: `payment.paystack.enabled`, `payment.paystack.secret-key`, `payment.paystack.api-url=https://api.paystack.co`, `payment.paystack.callback-url=https://app.slickhood.com/payment/callback`, `payment.paystack.currency=KES`, `payment.paystack.channels=card,mobile_money`. Use the supplied test key only in the explicitly test-phase deployment; never describe sandbox receipts as real settlements. Keep disabled until authentication succeeds. Confirm framework/environment binding rather than assuming a shell variable has overridden the property.
4. Through authorized account administration, create/verify the SLICKHOOD Paystack account for the actual subscription payee. It does not require a subaccount. Do not update the database directly to bypass verification. Merchant Paystack accounts still require externally verified subaccounts and the intended fee/split agreement; independent per-merchant API keys are not implemented by this global integration.
5. Configure SlickHood's M-Pesa SLICKHOOD account using the account configuration UI and encrypted parameter storage: consumer key, consumer secret, STK shortcode and passkey belonging to the same sandbox application. Its callback endpoint must be HTTPS and authenticated; never log its token. Provider callbacks must reach the backend via the proxy unchanged, without caching.
6. Rebuild the backend from an ordinary checkout at the reviewed release SHA. Require clean Maven verify, Docker MySQL execution, a schema-only V71-to-V72 rehearsal, correct embedded `git.properties`, and no failed migrations. The rehearsal only accepts a disposable loopback database named `slickhood_rehearsal_*` with empty imported Flyway history; do not point it at the live database.
7. Build frontend on Linux with clean `npm ci`, lint, build and browser tests. Supply `NEXT_PUBLIC_API_URL=https://app.slickhood.com/api`, `NEXT_PUBLIC_SITE_URL=https://slickhood.com`, the restricted production Maps key, the existing OAuth web client ID, and `NEXT_PUBLIC_COMMIT_HASH` equal to the reviewed frontend SHA. Windows validation output is not the Linux standalone deployment artifact. Test Google Maps on the actual allowed domain.
8. Deploy backend first. Confirm V72, health/readiness and deployed SHA; verify login, existing properties, accounts, and invoice retrieval before deploying frontend. Preserve S3, scanning, tenant authorization and HTTPS CORS protections. Deploy frontend only once backend checks pass.
9. Use designated accounts on `app.slickhood.com` for rental creation and similar units; emailed tenant invitation, registration/KYC, tenant-first/landlord-last signing and occupied status; estate owner invitation/agreement/service-charge invoicing; sales offer signatures, reservation, signed agreement and paid-invoice completion. Legal review of exact template versions remains an authorized admin decision.
10. For each enabled provider: invoice -> Pay Now -> sandbox customer authorization -> signed callback -> provider verification -> invoice/subscription update. Replay the callback; test wrong account, currency, amount, reference and live/test mismatch. Browser return alone cannot grant entitlements or complete a sale. Verify payer and receiving-admin views agree.
11. Observe errors, callback retries, mail delivery, scan/storage reachability and database latency. Record evidence and rollback artifacts. If backend smoke fails, do not promote frontend. V72 is a widening change; preserve the migrated column on rollback and do not destructively reverse migrations or delete created agreements/documents.

## Paystack suitability (official documentation checked 7 September 2026)

Paystack supports cards and M-Pesa for Kenyan integrations. It is a reasonable option for SlickHood subscription invoice payments and approved merchants receiving rent, estate charges or goods/services payments, subject to account onboarding, limits and fees. [Payment channels](https://paystack.com/docs/payments/payment-channels/)

The current SlickHood implementation uses one integration plus explicitly verified recipient subaccounts. Paystack supports splitting settlement to subaccounts, so each recipient need not necessarily manage a separate API secret. That is different from fully independent merchant integrations, which need separate credential/webhook routing work. Confirm the provider-approved platform model, beneficiary bank accounts and agreed platform share before activation. [Split payments](https://paystack.com/docs/payments/split-payments/)

Native recurring subscriptions support cards and Nigerian direct debit, not automatic Kenyan M-Pesa renewal. This candidate pays SlickHood invoices; it does not implement Paystack's recurring-plan lifecycle. Continue to use an explicit Pay Now authorization for M-Pesa renewals. Card automatic renewal would require separate consent and lifecycle work. [Subscriptions](https://paystack.com/docs/payments/subscriptions/)

For large property-completion payments, confirm transaction limits and bank/provider approval; do not represent a payment gateway as regulated escrow. Insurance, investment and custody-related use needs provider review of the actual business model. Prefer a verified bank route where appropriate rather than assuming every transaction belongs on card checkout. [Kenya terms](https://paystack.com/ke/terms)
