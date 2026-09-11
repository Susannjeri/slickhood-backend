# Billing end-to-end audit — 11 September 2026

## Scope and release state

This audit covers subscription, rent, service-charge, property-sale, Soko, Services and community-fund invoices; payer checkout; recipient payment accounts; payment history; receipts; callbacks; ledger application; and subscription fulfilment. The changes are in the consolidated D-drive release checkout and are not live until the backend-first test deployment completes.

## User-facing information architecture

The sidebar has one **Billing** parent. Its role-scoped destinations are:

1. **Bills & invoices** — amounts due and paid, invoice PDF, Pay balance and authorised manual reconciliation.
2. **Payment history** — provider and manually recorded transactions visible only to the invoice payer or recipient. Super Admin sees SlickHood platform/subscription transactions, not landlords' or merchants' customer invoices.
3. **Receiving accounts** — the active workspace's own destination: landlord, estate, property sales, merchant, insurance or SlickHood platform.
4. **Subscription & billing** — current subscription, term and billing state for roles that purchase plans.
5. **Change plan** — the curated, purchasable plan catalogue.

Tenant and buyer workspaces receive bills and payment history, but no receiving-account or plan-management controls unless another active role entitles them to those controls.

## Confirmed defect and correction

The subscription checkout validated the selected SlickHood receiving account but did not persist its ID on the new invoice. The M-Pesa callback deliberately rejects a receipt whose destination cannot be matched to the immutable invoice destination. Safaricom could therefore collect the sandbox payment while SlickHood left the invoice unpaid and never activated the subscription.

The corrected flow now:

1. pins the exact active, ready, SlickHood-owned payment account on the subscription invoice;
2. persists Safaricom's `CheckoutRequestID` before returning from STK initialisation, closing the fast-callback race;
3. keeps that request ID for callback correlation and stores the M-Pesa receipt in the separate provider-receipt field;
4. rejects wrong destination, amount, receipt reuse or unknown callbacks;
5. applies the payment through the idempotent financial ledger;
6. marks the invoice paid and publishes the durable paid-invoice event; and
7. activates or renews the purchased subscription once per invoice.

No database migration is required for this correction.

## Channel behaviour

| Channel | Checkout | Settlement rule | Test-release state |
|---|---|---|---|
| M-Pesa Direct | STK prompt to the payer's phone | Signed/correlated provider callback; exact invoice, amount, destination and unused receipt | Corrected and covered by regression tests |
| M-Pesa via Bank Paybill | Shows durable paybill/account/reference instructions | Bank callback where integrated, otherwise an authorised recipient records a verified manual payment | Existing guarded flow retained |
| PesaLink | Shows durable bank instructions | Provider IPN where integrated, otherwise authorised recipient reconciliation | Existing guarded flow retained |
| Paystack | Redirect to hosted card/mobile-money checkout | HMAC-signed webhook plus server-side transaction verification | Adapter ready; test key must be installed on the host and the host IP allowlisted |
| PesaWise | STK prompt | Per-recipient webhook secret plus server-side status verification of payment ID, reference, amount, currency and balance | New adapter is disabled by default until merchant sandbox credentials exist |

Manual channels never display a successful-payment state merely because instructions were shown. Only a validated callback or an authorised recipient's explicit reconciliation changes financial state.

## Payment-account security

- Each receiving account is owned by its recipient and category-scoped to landlord, estate management, property sales, merchant, insurance, community fund or SlickHood.
- Checkout accepts only the exact active, ready account attached to the invoice and matching its payment channel and billing category.
- Secret fields are encrypted, write-only in the UI and masked in reads and logs.
- Editing any routing or credential value clears readiness until the owner rechecks the complete configuration.
- SlickHood administrators cannot claim that a bank or gateway verified an account. They may only revoke a destination for a security or configuration concern.
- Test auto-readiness remains an explicit environment switch and must be disabled before a real-money launch.
- Payers cannot charge another user's invoice; recipients cannot initiate a charge on behalf of a payer.
- Payment history and receipt PDFs are participant-scoped. Platform administrators are restricted to SlickHood invoices and payments.

## Provider setup needed for test deployment

### Paystack

- Set `PAYMENT_PAYSTACK_ENABLED=true`.
- Install the sandbox secret from the protected E-drive credential file in the host secret/configuration store; never commit or log it.
- Set `PAYMENT_PAYSTACK_API_URL=https://api.paystack.co`.
- Set `PAYMENT_PAYSTACK_CALLBACK_URL=https://app.slickhood.com/payment/callback`.
- Configure Paystack's webhook URL as `https://app.slickhood.com/api/callback/paystack`.
- Allowlist the test host's fixed public IP `13.205.200.43` in Paystack.
- For non-SlickHood recipients, create/approve a Paystack subaccount and save only its `ACCT_...` code in that recipient's Receiving accounts screen.

### PesaWise

- Obtain a sandbox merchant account and complete its provider KYC.
- Obtain an API key, API secret, numeric balance ID and webhook secret/hash.
- Set `PAYMENT_PESAWISE_ENABLED=true` and `PAYMENT_PESAWISE_API_URL=https://api.pesawise.xyz` only in the test environment.
- Configure its webhook as `https://app.slickhood.com/api/callback/pesawise`.
- Configure each recipient's four protected PesaWise fields in Receiving accounts.

PesaWise stays unavailable in checkout while disabled; this avoids presenting an untestable channel.

## Acceptance journey

For every billing type, test with separate payer and recipient accounts:

1. issue the invoice and confirm both participants see the same amount, currency, due date and reference;
2. confirm an unrelated user and Super Admin cannot see a customer-to-customer invoice;
3. open the PDF and select **Pay balance**;
4. confirm only the invoice's active, ready receiving account is offered;
5. complete the provider sandbox flow and deliver the authentic callback;
6. confirm the invoice updates without a page restart, is shown once in payment history and has a downloadable receipt;
7. replay the callback and confirm the balance and ledger do not change again; and
8. for a subscription invoice, confirm the purchased plan becomes active and navigation entitlements refresh.

## Current external blockers

- The dedicated deployment SSH key is rejected by the test host. The local IAM user no longer has permission to grant itself the narrowly scoped Lightsail recovery action, so host configuration and deployment cannot be completed from this workstation until that temporary recovery permission is attached by an AWS administrator.
- The Paystack sandbox credential is present, but the workstation probe is rejected by Paystack's IP allowlist. The fixed test-host IP must be allowlisted and the probe repeated from the host.
- No PesaWise sandbox credentials were found. The adapter can be deployed disabled, but a live sandbox checkout cannot be certified without the four provider-issued values listed above.
