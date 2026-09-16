# Monetary precision migration — 2026-09-16

## Approved policy

- Transactional currencies are initially limited to `KES` and `USD`.
- Application money uses `BigDecimal` and database money uses `DECIMAL(19,2)`.
- Values are normalized to two decimal places with `HALF_UP` rounding at controlled input and provider boundaries.
- Provider amounts are converted to and from integer minor units through one shared monetary policy. Direct multiplication by 100 is not permitted in payment integrations.
- Arithmetic involving different currencies is rejected.

The two-decimal/HALF_UP choice preserves SlickHood's current invoice and provider behaviour. Adding a currency with different minor-unit rules requires a reviewed policy change, migration and regression test before activation.

## Inventory and treatment

| Area | Legacy representation | Treatment |
| --- | --- | --- |
| Invoice total and balance | `double` | Dual-write `amount_decimal` and `pending_amount_decimal`; reports, ledger, receipts, affiliate qualification and payment settlement read exact values. |
| Payment amount | nullable `Double` | Dual-write `amount_decimal`; persist normalized `currency_code`; Paystack verification and operations use exact values. |
| Unit asking/rental price | `double` | Dual-write `price_decimal`; reports read exact value. |
| Lease price and repair threshold | `double` / nullable `Double` | Dual-write decimal shadows. |
| Unit and lease charges | `double` | Dual-write decimal shadows; invoice generation converts line items through the shared policy. |
| Payment operations, affiliate commissions, marketplace orders/services, insurance quotes, financial ledger and wealth money | existing `BigDecimal`/`DECIMAL` | Retained; currency and boundary validation added where a new transaction or configuration is created. |
| Coordinates, sizes, ratings, confidence scores and percentage configuration | numeric but not monetary | Deliberately excluded from the money migration. Percentages remain decimal rates and are applied to normalized money. |

## Staged migration

Migration `V94__decimal_money_shadow_fields.sql` adds nullable decimal shadow columns, backfills them without deleting or changing the legacy columns, and creates reconciliation views. Entity lifecycle callbacks keep legacy and decimal values synchronized during the compatibility period. A rollback therefore keeps the existing legacy values available.

Run these read-only checks after migration and before allowing financial traffic:

```sql
SELECT * FROM pms_monetary_shadow_reconciliation;
SELECT * FROM pms_transaction_currency_reconciliation;
SELECT * FROM pms_financial_reconciliation;
```

Release acceptance requires all three result sets to be empty or every historical exception to be documented and corrected. Also reconcile invoice totals/balances, payments, confirmed refunds/reversals/chargebacks, affiliate commission currencies and financial reports against the pre-release export.

## Cutover and rollback

1. Take and verify a database backup.
2. Apply V94 during a controlled release window.
3. Run the three reconciliation views and the payment regression suite.
4. Keep dual-read/dual-write enabled for at least one complete billing and settlement cycle.
5. Only after a zero-difference reconciliation should a later migration make decimal fields authoritative and retire the legacy columns.

If reconciliation fails, stop financial traffic and roll back the application. Do not drop V94 columns during incident recovery; retain both representations for diagnosis and restore from the verified backup only if data correction cannot safely reconcile them.

## Regression coverage

- Money normalization, currency allow-list, minor-unit round-trip and cross-currency rejection.
- Invoice creation, subscription invoices and subscription completion.
- Manual payment, event publication and receipt state.
- Paystack routing, success verification, pending/failure/mismatch handling and signed webhooks.
- Rental reconciliation, refunds/reversals, affiliate commissions and report totals.

## Verification evidence

- Complete backend regression: **1,116 tests**, zero failures and zero errors; two environment-gated tests skipped (`SilveroceanApplicationTests.contextLoads` and the separately configured shared-role MySQL migration fixture).
- Focused financial regression: **86 tests**, zero failures/errors. This includes invoice, subscription, manual payment, Paystack, rental reconciliation, payment operations, receipts, affiliate and report coverage.
- Final payment-channel regression after currency capture was added: **44 tests**, zero failures/errors across M-Pesa, PesaLink, Paystack, manual payment and the monetary policy.
- Production-compatible migration rehearsal: the data-free V82 schema migrated through V83–V94 on isolated MySQL 8.4.11. Flyway applied all 12 migrations, validated version 94 with no failed/pending migration, and Hibernate schema validation passed.
- Post-migration checks confirmed V94 successful, both invoice and payment shadow columns present, and all three reconciliation views empty on the data-free fixture. The disposable database was deleted and the isolated server stopped after the run.

The MySQL rehearsal validates schema compatibility and migration syntax. Because the fixture is data-free, the three reconciliation queries must still be run on the protected production backup/clone before promotion and on production immediately after V94.

The migration is intentionally not a bulk replacement of every numeric field. It is a reversible compatibility release followed by observed reconciliation and a separately approved final cutover.
