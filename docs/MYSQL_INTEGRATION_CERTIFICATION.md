# MySQL integration certification

## Safe test boundary

`RentalPaymentMySqlIT` uses Testcontainers to create a disposable MySQL 8.4 database. The container supplies its own JDBC URL, username and password through dynamic test properties. The test never reads or connects to a configured SlickHood database and the database is destroyed after the run.

Run the fixture with:

```powershell
$env:JAVA_HOME='E:\Slickhood\tools\jdk-21\jdk-21.0.12.1+1'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd "-Dit.test=RentalPaymentMySqlIT" failsafe:integration-test failsafe:verify
```

Docker Desktop or another Docker-compatible runtime must be running. If Docker is unavailable, the test is reported as skipped rather than falling back to another database.

## What the fixture certifies

- Real MySQL persistence for users, property, unit, tenancy, signed lease, invoice and payment.
- Invoice reference update and pessimistic invoice lookup.
- Invoice-issued and payment-applied double-entry journals.
- Balanced debit and credit totals.
- Landlord and tenant payment authorization queries.
- Landlord reconciliation and account-statement JPQL queries.
- Provider-reference idempotency when payment completion is replayed.
- Canonical Paystack completion status and receipt eligibility.

## Flyway limitation

The repository currently contains incremental Flyway migrations `V1` through `V21`, but not the legacy base-schema migration that creates core tables such as users, properties, units, leases, invoices and payments. For that reason, the isolated repository fixture intentionally uses Hibernate `create-drop` and disables Flyway.

Do not represent this test as Flyway certification. Before production migration rehearsal:

1. Obtain a schema-only export from the authoritative pre-Flyway database or reconstruct and review a canonical `V0` baseline.
2. Remove all data and secrets from the export.
3. Apply the baseline and `V1`–`V21` to an empty disposable MySQL database.
4. Run schema validation with `spring.jpa.hibernate.ddl-auto=validate`.
5. Run the lifecycle fixture and the complete regression suite against that migrated database.

Never generate the baseline from production and apply it back to the same database during certification.
