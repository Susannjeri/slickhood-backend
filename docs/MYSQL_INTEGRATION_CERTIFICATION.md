# MySQL integration certification

## Safe test boundary

`RentalPaymentMySqlIT` uses Testcontainers to create a disposable MySQL 8.4 database. The container supplies its own JDBC URL, username and password through dynamic test properties. The test never reads or connects to a configured SlickHood database and the database is destroyed after the run.

Run the fixture with:

```powershell
$env:JAVA_HOME='D:\SlickHood-Codex\runtime\jdk-21.0.12.1+1'
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

The repository contains migrations through `V74`, but the legacy base schema predates Flyway. Use
`ProductionBaselineMigrationMySqlIT` only against a disposable, data-free copy of the deployed schema.
The test remains disabled unless both a JDBC URL and the explicit reset acknowledgement are present:

```powershell
$env:SLICKHOOD_MIGRATION_MYSQL_URL='jdbc:mysql://127.0.0.1:3307/slickhood_rehearsal_v74'
$env:SLICKHOOD_MIGRATION_MYSQL_USERNAME='<isolated-test-user>'
$env:SLICKHOOD_MIGRATION_MYSQL_PASSWORD='<disposable-test-secret>'
$env:SLICKHOOD_MIGRATION_MYSQL_ALLOW_RESET='true'
$env:SLICKHOOD_TEST_MYSQL_BASELINE_VERSION='73'
$env:SLICKHOOD_EXPECTED_FLYWAY_VERSION='74'
.\mvnw.cmd "-Dit.test=ProductionBaselineMigrationMySqlIT" failsafe:integration-test failsafe:verify
```

The configured user must be restricted to the disposable schema. The test drops that schema's copied
`flyway_schema_history` table, creates the configured baseline and applies all later migrations. Never
set `SLICKHOOD_MIGRATION_MYSQL_ALLOW_RESET=true` for a production or shared staging schema.

The URL must use loopback with an explicit port and a database named `slickhood_rehearsal_*`.
The imported Flyway history must be empty; non-empty history is never reset. A separate
`SLICKHOOD_MIGRATION_MYSQL_*` namespace prevents repository tests from resetting this schema.
For a combined `clean verify`, leave `SLICKHOOD_TEST_MYSQL_URL` unset so repository tests create
their own Testcontainers database. The example above assumes an already imported, data-free V73 schema.

For a release based on a newer exported schema, update `SLICKHOOD_TEST_MYSQL_BASELINE_VERSION` to the
actual deployed version. Update `SLICKHOOD_EXPECTED_FLYWAY_VERSION` to the immutable candidate's latest
migration. A skipped rehearsal, a failed record or any pending migration blocks production promotion.

## Docker API compatibility verification — 2026-09-06

The backend POM overrides Spring Boot's managed Testcontainers version to 1.21.4.
The earlier 1.21.3 client used Docker API 1.32, which the local engine rejects.
Testcontainers 1.21.4 contains the Docker Engine compatibility fix:
https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4

Verified with Docker Desktop 4.89.0 / Engine 29.7.2 (API 1.55, minimum 1.40),
JDK 21 and a disposable mysql:8.4 container reporting MySQL 8.4.11.
RentalPaymentMySqlIT: 1 test, 0 failures, 0 errors, 0 skipped.
External SLICKHOOD_TEST_MYSQL_* variables were cleared from the test process.
The isolated build is D:\SlickHood-Codex\validation\docker-mysql-20260906;
its docker-mysql-verification.log and target/failsafe-reports retain the evidence.
This verifies the repository/payment fixture; it does not certify Flyway migrations
or authenticated browser journeys.

Maven finished BUILD SUCCESS (exit 0). The fixture still emits a shutdown warning: its container stops before Spring finishes closing the connection pool, and Failsafe terminates the fork after 30 seconds. Assertions passed; test lifecycle cleanup remains a separate follow-up. The MySQL container was confirmed stopped.
