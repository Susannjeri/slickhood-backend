# Payment and termination notification matrix

This matrix is the production contract for rental, property-sale and estate-charge notifications. Email provides external delivery; an in-app copy is created when the recipient already has an active SlickHood account. Payment and termination are deliberately separate workflows: an overdue balance never terminates a lease, sale or ownership record automatically.

| Journey | Event | Recipient(s) | Channels | Action / safeguard |
| --- | --- | --- | --- | --- |
| All invoices | Payment applied (partial or full) | Payer | Email, SMS, in-app | Shows provider reference, applied amount and remaining balance; opens Billing / Invoices. Ledger idempotency prevents duplicate application. |
| Rental, sale, estate charge | Late fee assessed | Payer; biller/payee | Invoice, email, in-app | Biller explicitly saves a percentage and grace period during setup. The rule is prospective, applies once to unpaid principal, produces a separate payable invoice and never compounds on another fee. |
| Rental | Rent overdue | Tenant; landlord/payee | Email, in-app | Repeats on the configured interval and stops after payment, deactivation, due-date change or the configured occurrence cap. Landlord must separately review any breach/termination action. |
| Rental | Termination notice recorded | Tenant and landlord | Email, in-app | Records requester, reason, effective date and notice-period validation. It does not immediately release the unit. |
| Rental | Termination becomes effective | Tenant and landlord | Email, in-app | Stops future billing, closes tenancy, releases the unit and retains immutable lease documents. |
| Rental | Lease renewal / term expiry | Tenant and landlord | Email, in-app | Renewal shows the new end date. Term expiry uses the same governed notice/completion lifecycle. |
| Property sale | Sale payment overdue | Buyer; seller/payee | Email, in-app | Shows outstanding amount and due date. Cancellation is blocked while a collectible/paid escrow invoice remains; finance must void/refund first. |
| Property sale | Offer accepted or pipeline status changed, including cancellation | Buyer and, on acceptance, sales agent | Email, in-app | Shows the current sale status and links to the sale workspace. Cancellation requires a reason. |
| Estate | Service charge due soon / overdue | Homeowner | Email, in-app | Links to Billing / Invoices and uses the current unpaid balance. |
| Estate | Service charge overdue | Estate manager/payee | Email, in-app | Operational arrears alert only. It must not end legal ownership. |
| Estate | Ownership record ended or transferred | Homeowner | Email, in-app | Requires estate-management authority, a valid effective date and a reason; used for transfer/correction, not debt collection. |

## Governed customer requests

The current customer roles can view their records and receive the notifications above. The following are intentionally not implemented by reusing staff cancellation authority:

- A tenant notice to vacate needs a dedicated, owner-scoped permission and request state (`SUBMITTED`, `ACKNOWLEDGED`, `WITHDRAWN`, `EFFECTIVE`). It must enforce the signed lease notice period and notify the landlord.
- A buyer withdrawal needs a dedicated, buyer-scoped transition. It must be blocked or routed to finance when an escrow invoice is collectible or paid.
- A homeowner should request an ownership transfer, correction or dispute rather than “terminate ownership.” Estate management must validate and complete the change, preserving the ownership history.

These request workflows must be delivered as explicit APIs and UI actions with audit records. Granting a tenant, buyer or homeowner the existing landlord/sales/estate management permissions would cross workspace boundaries and is prohibited.

## Rollout controls

- `pms.receivables.reminder.enabled=false` is the safe default. Set it to `true` only after reviewing the live overdue candidate count; it covers `RENTAL` and `SALE` invoices.
- `pms.rental.reminder.repeat-days` controls the repeat interval (default 7).
- `pms.rental.reminder.max-occurrences` caps reminders per invoice (default 12).
- Service-charge reminder timing remains controlled by the estate reminder configuration.
- Legal breach, termination and demand notices remain human-reviewed. Automatic reminders may inform users of arrears but must never claim that a contract or ownership has ended.
- Late-fee policies default to off. Saving or changing a policy makes it effective from that date, so historical overdue invoices are never surcharged retroactively.

## Next notification stages

Before enabling high-volume production traffic, add configurable lease-expiry reminders (for example 60/30/7 days), offer-response deadline reminders, and explicit customer-request workflows above. WhatsApp may be added as an opt-in delivery channel after signed webhook verification is available; email and in-app remain the authoritative channels meanwhile.
