# Soko and Services journey audit — 7 September 2026

## Scope and release status

Audited the existing consolidated release, not a replacement system. Changes remain local and uncommitted. No deployment, production migration, real payment, customer notification, or customer-record modification was performed in this audit.

The checkout, fulfilment, provider/customer perspective, merchant maintenance and navigation corrections below are implemented. This is **not an unrestricted production certification**: the remaining functional and live-service gates below still apply. Existing Billing, Notifications and Wealth changes in these checkouts have been preserved.

## Navigation

Services & Shopping → Marketplace → Services / Soko.

- Services continues to use /dashboard/marketplace; Soko continues to use /dashboard/soko, preserving existing links.
- Removed the second embedded Soko catalogue from the Services page.
- Services retains its permission filter; Soko does not inherit that unrelated permission.
- My Services and Merchant Accounts remain provider tools. Service Management and Soko Management remain separate administrative workspaces.
- Existing sidebar renderer, route protection and page-title lookup support the nested links.

## Corrected findings

| Finding | Correction |
| --- | --- |
| Delivery could be completed through the pickup state path without delivery proof/code | Backend enforces delivery method at dispatch, ready-for-pickup and pickup completion. Existing proof/code and five-attempt controls remain. |
| Wrong delivery code caused every retry to upload the same proof again, which the backend rejects | Client retains successful proof upload and also recognises the persisted proof timestamp after reload. Retrying requires only the code. Original proof remains private. |
| Failed Soko payment left an unpaid order with no retry control | Added Pay now on the existing invoice and Refresh orders. An initiation response is not displayed as paid. |
| Cancellation API was not accessible from the Soko order journey | Added reasoned cancellation for eligible states and visible refund-request status. Copy distinguishes a refund request from money returned. |
| Shop created without a payment account could not be repaired from its workspace | Added editing of existing shop/payment account, merchant-account setup link, pickup/delivery controls and delivery fee. Existing coordinates are preserved during edits. |
| Merchants could not maintain existing product price and stock | Added product editing, decimal prices and integer stock validation. Editing retains existing images. Photo failure exposes the saved draft for recovery instead of requiring another product. |
| Shop publishing read as immediate publication | Renamed action to Submit shop for review and limited its display to draft/rejected shops. Admin approval remains required. |
| Checkout preview omitted delivery fee/currency | Added delivery fee to catalogue response and currency-aware item, fee and total preview. Older responses without fee do not fabricate a zero-fee total. |
| Disabled payee could still accept a new Soko reservation or Services invoice | Revalidate active, verified, correctly owned merchant account. Soko also requires a configured payment channel. |
| Provider buying another provider's service lost customer actions | Backend returns customerBooking for the authenticated viewer; UI uses that per-booking perspective, not just global provider permissions. Backend ownership remains authoritative. |
| Listed service could be booked after its provider became inactive/blacklisted | Booking requires an active service and active provider; confirmation rechecks both. |
| Browser/host timezone could affect accepted appointment times | UI labels Nairobi time and backend validates the future instant in Africa/Nairobi. LocalDateTime bean validation no longer applies an unrelated host timezone. |
| Repeated clicks could send duplicate booking/checkout requests | Immediate client guards added for booking and checkout; existing backend Soko idempotency key retained. |
| A stale catalogue response could overwrite a newer Soko search | Request generation and effect cleanup reject superseded responses. |
| Failed dispatch closed the form | Dispatch now retains fields when its update fails. |
| Cancellation reason could enter an HTML notification unescaped | Escape the Services booking cancellation reason in notification HTML. |
| Confirmed finance records could be overwritten; unpaid legacy jobs could be settled | Shared finance guard makes exact confirmed repeats idempotent, rejects altered confirmed records, requires paid/completed settlement, prevents confirmed refund + settlement exceeding the paid total, and blocks settlement during a pending refund. |

The finance guard protects **recorded decisions**, not execution of an external refund or payout. It does not prove provider settlement or replace a reconciliation ledger.

## User journeys to verify on the deployed test application

### Soko

1. Merchant sets up their own payment account in Merchant Accounts; verification is required.
2. Create shop with contact details, location, fulfilment options and fee. A draft can be edited to attach the verified account.
3. Submit shop for review. Superadmin approves or rejects with a reason. Suspended shops/products must not be browsable.
4. Add products, prices, stock and scanned pictures; publish under an approved shop. Maintain stock and price without creating duplicates.
5. Customer browses and builds a single-shop cart, checks delivery/pickup and total, and creates an order.
6. The existing invoice is paid through that merchant's verified account. Verify failed initiation/retry, authenticated callback, duplicate callback and late payment after expiry.
7. Merchant confirms → packs. Pickup follows ready-for-pickup → completed. Delivery follows rider/courier assignment and Nairobi ETA → dispatch → scanned private proof → customer code → completed.
8. Customer shares the six-digit code only after receiving/checking the order. A wrong code must not lose proof; five failed attempts must block completion.
9. Cancellation before packing restores stock once and requests a refund when paid. Verify expiry and cancellation racing with payment, then independently reconcile refund/settlement evidence.

### Services

1. Provider completes profile, selects service/category and requirements, uploads scanned verification documents and supplies referees as required.
2. Submit for review; administrator verifies requirements and lists the service. Merchant payment account belongs to that provider, not SlickHood.
3. Customer browses Services, chooses a future Nairobi appointment and supplies job details.
4. Provider accepts and invoices. Customer sees Pay now, including where the customer also has a provider role.
5. Verified payment callback unlocks start → in progress → completion with evidence. Unpaid current-flow bookings cannot start.
6. Customer sees booking/payment status, may cancel eligible work, and may rate completed work. Review complaints through the administrative queue.
7. Reconcile any refund and settlement against provider evidence; do not treat a UI status as proof of money movement.

## Remaining gaps / launch gates

1. **Live recipient-account/payment certification:** actual merchant-owned M-Pesa and Paystack invoice initiation, signed callbacks, amount/currency/destination matching, late/duplicate callbacks and reconciliation remain untested in this turn. Prior Billing audit release gates still apply.
2. **Finance workspaces and ledger:** operational finance APIs exist, but the Soko administrative screen principally covers moderation rather than an end-to-end refund/settlement workbench. The model stores one cumulative decision per finance type; partial/multiple disbursement history, reversal workflows and automatic external payouts/refunds need a dedicated audited ledger design. Do not claim these as implemented.
3. **Large histories:** APIs bound order/booking pages, but customer/merchant screens currently request the first 100 records and catalogue screens have fixed result caps. User-facing server pagination/search beyond these limits is still needed.
4. **Services after-sales:** complaint filing/review APIs and rating ownership checks exist. Review repeated complaint-resolution transitions, cancelled-booking refund complaints, provider resubmission after rejection and completeness of customer-visible complaint tracking before unrestricted launch.
5. **Services document expiry:** upload scanning and ownership checks exist, but readiness queries currently count active uploaded/verified documents without excluding expired documents. Expiry-driven delisting/re-review remains a release risk.
6. **Notifications:** full Soko order lifecycle delivery notifications are not certified. Prior notification audit gaps still apply. WhatsApp remains parked by user request; no webhook authentication was bypassed.
7. **Live storage/scanning:** exercise valid images/docs, malformed files, EICAR, scanner unavailability and cross-user proof/document denial against the configured S3/ClamAV system. Local mocked browser uploads are not malware-scan proof.
8. **Checkout concurrency:** existing Soko row locks/idempotency/expiry need MySQL concurrency testing. Services client duplicate suppression is not server-side booking idempotency.
9. **Geolocation and quoting:** test real-domain geolocation, service radius and smart-gate destination authorisation. Existing checkout uses authoritative current product prices; a dedicated server quote/confirmation step would further protect against prices changing after catalogue display.

## Verification evidence

Local logs live under D:/SlickHood-Codex/operations:

- marketplace-focused-20260907.log — initial 34 focused backend tests passed.
- marketplace-full-backend-20260907.log — 731 tests discovered, 730 passed, one existing application-context skip; before final finance guard.
- marketplace-browser-20260907.log — initial eight mocked browser journeys passed; before merchant maintenance final rebuild.
- marketplace-final-backend-20260907.log — 738 tests discovered, 737 passed, one existing application-context skip; no failures or errors.
- marketplace-final-build-20260907.log — final frontend production-mode build passed with the local test API URL.
- marketplace-final-browser-20260907.log — 29/29 mocked browser checks passed across Marketplace, Soko, Billing, Notifications and Wealth, including merchant edits.
- marketplace-final-lint-20260907.log — passed: zero errors, 463 warnings within the 478 warning budget.

New regression coverage is in MarketplaceFinanceGuardTest, additions to SokoServiceTest and ServiceBookingServiceTest, and frontend e2e/marketplace-audit.spec.ts. Tests cover role-specific navigation and actions, proof retry, unpaid-order payment/cancellation and merchant maintenance.

Builds use a loopback API URL for local browser tests. These are **not deployable production frontend artifacts**. No new migration is introduced. Before any release, merge/review the combined changes, build with verified production configuration, run backend-first verification, and retain rollback artifacts. Do not deploy the whole dirty checkout blindly.
