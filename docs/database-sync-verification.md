# Schema and F12 native verification — 18 October 2026

The primary checkout started clean on dev563266a; preserved startup/CORS stashes
remain untouched. The pricing worktree was clean and its078180c commit was
already an ancestor of dev, so that obsolete worktree was removed. The earlier
schema worktree evidence was reused without losing the short README.

Migration014_leg_execution_welfare_tracking.sql combines the reviewed guarded
seven-table bootstrap with F12 operation receipts, visibility/source fields,
same-plan composite FKs and append-only triggers. F11 FirstLegGuard now checks
real departure transitions, permits completion without re-authorizing departure,
and prohibits overwriting an already recorded actual departure.

Database evidence (Windows integrated authentication; production untouched):

| Catalog suffix (prefix EquiTransit_FigmaMigrationTest_) | Evidence |
|---|---|
| Execution_20261018 | Created empty; baseline57 tables then001–014 PASS; reapply PASS; seven-table metadata110 columns PASS |
| Bootstrap_20261018 |001–014 reapply PASS; F10/F11 native regression |
| Staff_20261018 | Existing isolated staff catalog; F15 native tests |

SchemaReadinessIntegrationTest invokes all nine actual repository ready() probes,
not mocks. The physical verifier checks keys, types, CHECK/FK definitions and
trust state. All57 original baseline tables remain; LegExecutionOperation is
an additional private table. No schema deployment to EquiTransit is claimed.

Required native suites: StaffAdministrationIntegrationTest5 cases,
LegExecutionIntegrationTest11 cases, OperationalPlanIntegrationTest4 cases,
DepartureGateIntegrationTest1 case, SchemaReadinessIntegrationTest1 case.
F12 fixtures roll back and AfterTransaction asserts zero users/orders, facts,
operations, incidents and audits. The parent lock test uses a separate SQL
connection with a finite lock timeout; trigger tests never disable append-only
protection. Other opt-in SQL suites remain separately gated; their skips must
not be described as native verification of unrelated modules.

Reproduce with tools/sync-db-migrations.ps1 and tools/verify-sql-integration.ps1.
See leg-execution-api.md and team/BE2_F13_F14_HANDOFF_SPEC.md for contracts.

Final commands/results:

- `mvnw.cmd clean verify`: BUILD SUCCESS,311 cases,163 executed/pass,
  0 failures/errors;148 unrelated opt-in cases skipped (not claimed as verified).
- `tools/verify-sql-integration.ps1`:58/58 PASS,0 skipped:22 required native SQL
  cases plus36 unit/OpenAPI contract cases. F15 is5/5 and F12 is11/11.
- Final DTO rowversion-pattern refinement: ApiDtoContractTest/OpenApiTest32/32
  PASS and test-compile/checkstyle:check0 violations.
- Execution/Bootstrap catalogs:69 tables each,0 untrusted/disabled FK or CHECK;
  Staff catalog:68 tables,0 untrusted/disabled FK or CHECK. Each catalog has0
  business rows; only Role=5 and existing SchemaMigrationHistory=1 are nonempty.
- README/local documentation links and verification-script syntax PASS.