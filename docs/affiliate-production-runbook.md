# Affiliate production runbook

## Financial policy and configuration

Set and approve these values before deployment. The application fails startup validation when the rate, minimum or hold period is outside its safe range.

```text
AFFILIATE_COMMISSION_RATE=10.00
AFFILIATE_MINIMUM_PAYOUT=1000.00
AFFILIATE_COMMISSION_HOLD_DAYS=14
AFFILIATE_COMMISSION_RELEASE_DELAY_MS=60000
```

- A referral is attributed once to the first valid code captured during the 30-day browser attribution window.
- Self-referrals are rejected. Only fully paid subscription invoices qualify.
- The commission rate is snapshotted when the commission is created.
- Commissions remain pending through the clearing period and only matured earnings can be requested.
- Any confirmed refund, reversal or chargeback disqualifies the commission. An unpaid commission is reversed; a paid commission becomes a clawback that offsets future earnings.
- SlickHood does not receive or initiate the affiliate's payment through this module. A system owner settles externally, records the provider reference, and then marks the payout paid.

## Deployment order

1. Record current backend and frontend commits and take a restorable database backup.
2. Deploy the backend and confirm Flyway applies `V51__affiliate_production_guardrails.sql`.
3. Confirm the commission maturation scheduler is running on only the intended application nodes and processes at most 200 records per pass.
4. Configure edge rate limiting for `/affiliate/public/**` and registration endpoints. Do not expose an unrestricted referral-code oracle.
5. Deploy the frontend.
6. Complete the isolation, payout and reversal matrix below before promotion.
7. Monitor referral validation failures, duplicate invoice commissions, pending maturity depth, payout queue age, payout transition failures and database lock latency.

## Required staging matrix

- Register by email and Google through a valid referral; confirm attribution occurs once and expires after 30 days.
- Attempt a self-referral, malformed code, inactive code and second attribution for the same user.
- Pay a non-subscription invoice and confirm no commission is created.
- Pay an eligible subscription twice through replayed callbacks and confirm exactly one pending commission.
- Mature a commission, request a payout twice concurrently and confirm only one request reserves the earnings.
- Confirm an unverified, inactive, platform-owned or another user's payment account cannot be selected.
- Process, reject and pay requests through every allowed transition; confirm invalid and repeated transitions are denied.
- Confirm rejection releases earnings and payment requires an external reference; rejection requires a reason.
- Confirm refund, reversal and chargeback events reverse pending/earned commissions, adjust an open payout, or create a post-payment clawback.
- As an affiliate, attempt every system-owner endpoint. As another affiliate, attempt direct access to another affiliate's records.
- Inspect API responses and confirm raw profile entities, referred-user IDs, audit fields and account-property secrets are absent.
- Verify dashboard and admin queues are bounded and full history remains available through scoped reports.

## Rollback

V51 is additive. Roll application binaries back to the recorded commits and normally leave the migration in place. Do not drop payout snapshots or reversal evidence during an incident. Pause payout processing while reconciling any in-flight request, and preserve provider references and audit logs.
