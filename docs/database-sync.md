# Reproducible local/test schema and native verification

Use PowerShell with Windows SQL authentication and JDK21. This helper accepts
only `EquiTransit_FigmaMigrationTest_*` catalogs; it cannot target `EquiTransit`.
No SQL login secrets, roles/grants, automatic production migration or business
fixtures are embedded. SQL Server must allow the executing Windows identity to
connect and perform the requested DDL (and create a test database if requested).

```powershell
# Existing test database; copy-only backup with checksum and VERIFYONLY first.
.\tools\sync-db-migrations.ps1 -Backup

# New empty test catalog, constructed without copying production data.
.\tools\sync-db-migrations.ps1 `
  -Database EquiTransit_FigmaMigrationTest_Bootstrap_20261018 -CreateDatabase

# Repeat the same command to verify reapplication; metadata only:
.\tools\sync-db-migrations.ps1 -VerifyOnly
```

The checked-in `database/bootstrap/000_figma_schema.sql` recreates the approved
57-table/620-column foundation from the 2026-10-14 metadata snapshot. Existing
tables and primary keys are retained, even when a historical PK has a different
name. Candidate keys precede foreign keys to support cyclic/composite links.
It seeds only the five required reference roles, never staff accounts, tariffs,
legal evidence, booking confirmations or payment receipts.

After this foundation the runner executes numbered migrations001 through014
in order, on one connection so transactions across `GO` batches remain intact.
It takes a session-owned sync lock, checks for sequence gaps/duplicates, stops
on the first failure, rolls back any open file transaction and reports the file.
Earlier successfully committed files remain safe to reapply; there is no claim
that the entire chain is one atomic transaction or that database version can
be inferred from an application restart. No new migration history is invented.

Migration014 explicitly creates the seven F12-F14 tables when absent and adds
missing nullable/defaulted fields, keys, indexes, trusted FKs and CHECKs. If a
populated table lacks required fields without defaults, it stops for a reviewed
backfill instead of inventing zeros, identities or evidence. Existing rows and
history are never dropped or truncated. Incompatible types/constraint definitions
are reported by the metadata verifier, not silently rewritten.

`database/verify-f12-f14-schema.sql` checks all seven table shapes, SQL types,
length/precision/scale/nullability/identity, PK/index columns, FK column ordering
(including ActualCost.LegID+PlanID, same-leg welfare supersession, and the
OrderChangeRequest.BaseRevisionID+OrderID link), and trusted CHECK definitions.
It also requires all57 baseline tables. A failed comparison raises SQL error;
it cannot return a success-shaped fallback. Future intentional schema changes
must update the approved contract and this verifier together.

## Zero-skip native tests

Staff's last-Manager race requires an empty dedicated catalog; it must not
deactivate unrelated demo accounts in the shared test database. Provision it:

```powershell
.\tools\sync-db-migrations.ps1 `
  -Database EquiTransit_FigmaMigrationTest_Staff_20261018 -CreateDatabase
.\tools\verify-sql-integration.ps1 -JavaHome C:\path\to\jdk-21 `
  -NativeDirectory C:\path\to\sqljdbc-native
```

Create the dedicated F12 catalog as well:

```powershell
.\tools\sync-db-migrations.ps1 `
  -Database EquiTransit_FigmaMigrationTest_Execution_20261018 -CreateDatabase
```

The test helper sets all five SQL enable flags and native JDBC URLs itself,
runs `test-compile checkstyle:check test`, and rejects zero-case/skipped/failed
Surefire reports. It restores the calling process's environment afterwards.
Staff suite must contain5 cases. F10/F11 run on the guarded empty bootstrap
catalog; staff runs on its empty guarded staff catalog. Schema probes check
the isolated execution test catalog. All cleanup uses
fixture-owned IDs, and existing accounts/history must remain intact.

`SchemaReadinessIntegrationTest` invokes the actual nine repositories' `ready()`
methods against SQL Server and executes the physical verifier. To check the
fresh bootstrap instead, run that suite with `EQ_SCHEMA_TEST_ENABLED=true` and
`EQ_SCHEMA_TEST_DB_URL` pointing to the new FigmaMigrationTest catalog.
H2 and mocks do not establish SQL Server schema readiness.

This proves the schema/integration boundary; F12 execution is implemented. F13 incident/change approval and F14
delivery/settlement business APIs remain the handoff scope.

Migration014 also adds LegExecutionOperation, visibility/source/recorded-time
fields, same-plan FKs on both execution logs, correction uniqueness, immutable
fact triggers and a narrowly scoped F11 first-departure trigger repair. The
seven-table verifier includes110 core plus F12 extension columns. Test rollback,
not disabled triggers, removes append-only fixtures. F12 and schema smoke tests
run on Execution_20261018, with every table left empty except reference roles
and pre-existing migration metadata.
