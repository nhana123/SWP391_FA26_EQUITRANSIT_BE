# Journey v2 verification — 2026-10-24

## Implemented main flow

- Multi-horse customer booking with ownership checks and horse/retail snapshots.
- Manager review, staff assignment, reviewed ordered transit countries, deposit.
- Staff-reconciled money with unique bank references and retry receipts.
- Shared trip manifest across customers, vehicle capacity, driver and time checks.
- Per-horse, per-phase required documents, versioned uploads, rejection feedback,
  resubmission and independently expiring clearance; transit permits included.
- Manager trip approval, assigned-Driver checklist, paid/cleared departure.
- Per-horse delivery evidence, quarantine-release guard and timestamp validation.
- Approved extras allocated to booking; full settlement before trip closure.
- V1/V2 vehicle, driver and horse scheduling bridge using shared resource lock.
- Scoped queues, customer-only tracking and file ownership checks.

The implementation is additive. V1 order IDs are not V2 booking IDs. Existing
TransportOrderDetail stays single-horse for compatibility. Multiple-horse
bookings use journey.Booking + journey.BookingHorse; shared trips use
journey.Trip + journey.Manifest. Existing records have not been rewritten.

## Database

Migrations 021, 022, 023 applied only to EquiTransit_Demo, with COPY_ONLY,
CHECKSUM backups and successful RESTORE VERIFYONLY before each migration:

- EquiTransit_Demo_before_021_20261024_103144.bak
- EquiTransit_Demo_before_022_20261024_105644.bak
- EquiTransit_Demo_before_023_20261024_110511.bak

Backups are in the SQL Server instance's default Backup directory.
Demo has 12 journey tables, no disabled/untrusted journey foreign keys, and
zero remaining SQL booking fixtures. Production EquiTransit still has 73
tables and zero journey tables; production was queried read-only, not migrated.

## Tests and runtime evidence

Final command: `mvnw.cmd -B test-compile checkstyle:check package`, with
EQ_JOURNEY_SQL=true and the Demo URL plus JDBC native library path.

- BUILD SUCCESS, zero Checkstyle violations.
- 416 tests discovered; 229 executed successfully, 187 skipped by existing
  native-suite environment gates. This is not 416 executed tests.
- Native JourneyFlowSqlTest executed, not skipped: two disposable customers,
  three horses sharing one trip, wrong-owner rejection, duplicate receipt,
  insufficient payment, absent safety check/clearance, document rejection and
  resubmission, quarantine release, 422 early handover, unpaid extra preventing
  closure, successful settlement, safe retries. All fixture rows roll back.
- Additional old native suites on EquiTransit_FigmaMigrationTest_BE01_20261023:
  OperationalPlanIntegrationTest + ResourceIntegrationTest, 20 executed,
  zero failures/errors/skips. Logs: .local/journey-legacy-sql.log.
- Packaged runtime on 127.0.0.1:8081, database EquiTransit_Demo, feature enabled.
  Health UP; 19 distinct V2 Swagger paths. Five roles logged in and read their
  scoped queues. Customer shared-manifest access 403; incomplete request 400;
  anonymous access 401. No HTTP business orders were created for smoke tests.

## Review and adoption

- Swagger: http://127.0.0.1:8081/swagger-ui/index.html
- Contracts: JOURNEY_V2_API.md; rollout: REFERENCE_FLOW_MIGRATION.md.
- Start/stop dedicated Demo: tools/run-journey-v2-demo.ps1 / -Stop.
- Repeat native v2 checks: tools/verify-journey-v2.ps1.
- Existing frontend is unchanged. It must adopt V2 explicitly to use the new
  multi-horse/consolidation flow. Existing V1 screens continue using V1 data.
- Automatic geographic routing, live flight purchase, payment-gateway callback,
  and migration of legacy Studio/welfare/incident history into shared trips are
  not implemented by this increment. Supported estimate units and other limits
  are listed in JOURNEY_V2_API.md. No claim of complete reference-feature parity
  or production readiness is made.
