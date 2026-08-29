# Paystack test setup

The integration uses one SlickHood Paystack account and routes each transaction to the landlord assigned to the invoice's property. A payment cannot be initialized until that property has a Paystack subaccount code.

## 1. Configure the application

Copy the values from `.env.example` into the environment used to start the backend. Set:

- `PAYSTACK_ENABLED=true`
- `PAYSTACK_SECRET_KEY` to the Paystack **test secret key** (`sk_test_...`)
- `PAYSTACK_CALLBACK_URL` to the frontend page that receives the customer after checkout
- `PAYSTACK_CURRENCY=KES`

Do not commit the real `.env` file or paste the secret key into source code.

## 2. Configure Paystack

In the Paystack test dashboard, set the webhook URL to:

`https://YOUR-PUBLIC-BACKEND/callback/paystack`

The backend verifies the `x-paystack-signature` header and then independently verifies successful transactions with Paystack before marking an invoice paid.

## 3. Add each landlord

For every landlord:

1. Create a Paystack subaccount in test mode using the landlord's settlement bank details.
2. Copy the returned code (for example, `ACCT_...`).
3. In SlickHood, open the landlord/property payment accounts and add the **Paystack** channel.
4. Save the code in **Paystack subaccount code**.

The code is stored through the existing encrypted property-account mechanism. `PAYSTACK_FEE_BEARER=subaccount` means Paystack fees are charged to the landlord subaccount; change it to `account` if SlickHood should pay the fee.

## 4. Test safely

Create a small test invoice, pay it with Paystack's test payment details, and confirm:

- the Paystack transaction names the expected landlord subaccount;
- SlickHood marks the correct invoice paid only after server-side verification;
- replaying the webhook does not duplicate the payment;
- another property's landlord code is never used.

Before live mode, replace the test secret with a live secret in the deployment environment and recreate/verify every landlord subaccount in the live Paystack account.
