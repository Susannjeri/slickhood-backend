# Affiliate production runbook

## Financial policy and configuration

Superadmin manages programme defaults under Affiliate Management → Affiliate reward policy. Edit the commission percentage, eligible subscription payment count, default minimum payout and holding days; preview the terms and publish with a reason. Changes are audited and version-checked.

Deployment configuration remains the fallback until the first policy is published. Preserve the configured values; the example below describes the code defaults, not an approved financial decision.

```text
AFFILIATE_COMMISSION_RATE=25.00
AFFILIATE_ELIGIBLE_PAYMENT_COUNT=3
AFFILIATE_MINIMUM_PAYOUT=1000.00
AFFILIATE_COMMISSION_HOLD_DAYS=14
AFFILIATE_COMMISSION_RELEASE_DELAY_MS=60000
```

- A referral is attributed once to the first valid code captured during the 30-day browser attribution window.
- Self-referrals are rejected. Only fully paid subscription invoices qualify.
- Default rate and minimum apply to newly enrolled profiles. Existing profile overrides are managed separately in the affiliate directory.
- Published payment-count and holding-day changes apply to future qualifying invoices only. Existing earned or pending commissions and requested payouts are not recalculated.
- The affiliate-specific commission rate is snapshotted when the commission is created.
- Commissions remain pending through the clearing period and only matured earnings can be requested.
- Any confirmed refund, reversal or chargeback disqualifies the commission. An unpaid commission is reversed; a paid commission becomes a clawback that offsets future earnings.
- SlickHood does not receive or initiate the affiliate's payment through this module. A system owner settles externally, records the provider reference, and then marks the payout paid.
- Programme states are explicit: `ACTIVE`, `SUSPENDED`, `BLACKLISTED` and `INACTIVE`. Only Active profiles can accept new referral attribution, earn new commissions or request payouts. Status changes require a reason, are audited and preserve existing balances/history. Open payout requests remain an administrator decision: verify and record the external payment, or reject and release reserved earnings.

## Deployment order

1. Record current backend and frontend commits and take a restorable database backup.
2. Deploy the backend and confirm Flyway applies `V51__affiliate_production_guardrails.sql` and the additive `V89__admin_managed_affiliate_policy.sql`.
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
- Verify paged directories, payout queues and complete own/admin-scoped histories; check balances stay separate for each currency.
- Publish a policy, attempt a stale edit and verify a later conversion uses the new count/hold while retaining the affiliate-specific rate.
- Review policy audit history in Audit Logs; no payout API transfers funds.
- Exercise Active → Suspended → Active, Blacklisted and Inactive transitions. Confirm non-Active profiles cannot resolve referral codes, earn future commissions or request payouts, while existing balances, history and payout-review evidence remain visible.

## Rollback

V51 is additive. Roll application binaries back to the recorded commits and normally leave the migration in place. Do not drop payout snapshots or reversal evidence during an incident. Pause payout processing while reconciling any in-flight request, and preserve provider references and audit logs.

V89 is also additive; preserve published policy and audit history during rollback. This release also includes V88 custom property types. Once a custom property code has been used, an older enum-only backend may not read that property. Use compatible rollback binaries or roll forward; never delete or reclassify customer properties merely to accommodate an old binary. Rehearse the complete production migration history and authenticated journeys before deployment.
