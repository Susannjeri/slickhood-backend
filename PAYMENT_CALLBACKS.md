# Payment callback routes

All M-Pesa callback URLs require the same non-empty `MPESA_CALLBACK_TOKEN` as
the `token` query parameter. Use HTTPS public URLs outside local development.

## M-Pesa process 1: direct Safaricom Paybill

- Validation: `POST /callback/mpesa/paybill/validation?token=...`
- Confirmation: `POST /callback/mpesa/paybill/confirmation?token=...`
- STK result: `POST /callback/stk?token=...`

The legacy `/callback/validation` and `/callback/confirmation` routes remain
available for existing Safaricom registrations.

## M-Pesa process 2: bank-mediated confirmation

`POST /callback/mpesa/bank/confirmation?token=...`

```json
{
  "mpesaReference": "UCMEWA74WV",
  "bankReference": "BANK-12345",
  "invoiceReference": "INV-300",
  "amount": 300.00,
  "transactionTime": "20260826205000",
  "bankAccountNumber": "12345",
  "customerPhoneNumber": "254700000001",
  "customerName": "Neo Gulf Logistics"
}
```

The M-Pesa receipt is the canonical settlement identity. The bank reference is
kept in the callback audit payload. Consequently, if both routes report the
same M-Pesa receipt and invoice, the second callback is acknowledged without
crediting the invoice again.

## Other providers

- Flutterwave: `POST /callback/fw/payments` with its configured webhook signature.
- Paystack: `POST /callback/paystack` with `x-paystack-signature`.
- PesaLink uses its configured IPN and validation routes.

Provider callbacks are accepted only after authentication/signature checks.
Settlement additionally verifies the local reference, provider reference,
amount, and currency where the provider verification API supports them.
