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

## Production-baseline Flyway rehearsal

The repository contains migrations through `V71`, but the legacy base schema predates Flyway. Use
`ProductionBaselineMigrationMySqlIT` only against a disposable, data-free copy of the deployed schema.
The test remains disabled unless both a JDBC URL and the explicit reset acknowledgement are present:

```powershell
$env:SLICKHOOD_TEST_MYSQL_URL='jdbc:mysql://<isolated-host>:3306/slickhood_release_test'
$env:SLICKHOOD_TEST_MYSQL_USERNAME='<isolated-test-user>'
$env:SLICKHOOD_TEST_MYSQL_PASSWORD='<secret>'
$env:SLICKHOOD_TEST_MYSQL_ALLOW_RESET='true'
$env:SLICKHOOD_TEST_MYSQL_BASELINE_VERSION='67'
$env:SLICKHOOD_EXPECTED_FLYWAY_VERSION='71'
.\mvnw.cmd "-Dit.test=ProductionBaselineMigrationMySqlIT" failsafe:integration-test failsafe:verify
```

The configured user must be restricted to the disposable schema. The test drops that schema's copied
`flyway_schema_history` table, creates the configured baseline and applies all later migrations. Never
set `SLICKHOOD_TEST_MYSQL_ALLOW_RESET=true` for a production or shared staging schema.

For a release based on a newer exported schema, update `SLICKHOOD_TEST_MYSQL_BASELINE_VERSION` to the
actual deployed version. Update `SLICKHOOD_EXPECTED_FLYWAY_VERSION` to the immutable candidate's latest
migration. A skipped rehearsal, a failed record or any pending migration blocks production promotion.
