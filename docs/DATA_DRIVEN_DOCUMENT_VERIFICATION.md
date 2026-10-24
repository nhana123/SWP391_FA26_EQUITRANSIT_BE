# Verification — 2026-10-24

## Build and native tests

Final command, from Backend, with JDK21 and the existing SQL JDBC auth DLL:

```powershell
$env:EQ_DOCUMENT_TEST='true'
$env:EQ_DOCUMENT_TEST_DB_URL='jdbc:sqlserver://localhost:1433;databaseName=EquiTransit_Demo;integratedSecurity=true;encrypt=true;trustServerCertificate=true'
$env:EQ_COMPLIANCE_TEST_ENABLED='true'
$env:EQ_COMPLIANCE_TEST_DB_URL=$env:EQ_DOCUMENT_TEST_DB_URL
$env:EQ_JOURNEY_SQL='true'
$env:EQ_JOURNEY_DB_URL=$env:EQ_DOCUMENT_TEST_DB_URL
.\mvnw.cmd -B test-compile checkstyle:check package '-DargLine=-Djava.library.path=.local/tools/sqljdbc-native'
```

BUILD SUCCESS at 15:42:28 Asia/Saigon. Zero Checkstyle violations.
418 tests discovered: **249 executed and passed, 169 skipped** because their
separate environment-gated integration suites were not enabled in this run.
No claim that all native SQL suites executed.

Included passing suites:

- ComplianceIntegrationTest: 18 native SQL/MockMvc tests.
- DocumentWorkflowSqlTest: 2 native SQL/MockMvc tests with default approval
  provisioning enabled, including stallion vs mare on the same route.
- JourneyFlowSqlTest: 1 native SQL shared-trip regression.
- ApiDtoContractTest: 23 OpenAPI/DTO contract tests.

The old Compliance suite explicitly runs manual approval mode to retain its
RULE_ASSESSMENT_PENDING scenarios. The new suite exercises actual Manager
approval automatically creating all three checklists and idempotent replay.
Its assertions cover local streaming, access denial, source exclusivity,
wrong-horse reuse, maximum document age, submission retry and all phase clears.
Malformed content, traversal names and oversized files are rejected.

Local logs (ignored): `.local/document-modules-final.log`,
`.local/document-module-test.log`.

## Database and cleanup

Migration024 was applied twice on EquiTransit_Demo after a verified backup;
second application preserved the same 25 locations / 8 document policies.
Backup path is recorded in DATA_DRIVEN_DOCUMENT_API.md.
No DDL was issued to the real EquiTransit database.

Post-test verification:

- LocationCatalog: 25 rows across 16 countries.
- ComplianceDocumentPolicy: 8 rows.
- SchemaMigrationHistory: exactly one migration024 entry.
- LocalFileObject: zero rows after rollback of test uploads.
- `.local/module-test-files`: zero files after rollback cleanup.
- Disabled/untrusted foreign keys on Demo: zero.

## Running Demo

The existing managed sandbox runtime was restarted with the tested JAR:
`http://127.0.0.1:8081` (PID14360 at verification time).
The other services/port8080 were not stopped.

Live HTTP smoke:

- `/api/health`: UP.
- Anonymous `/api/lookups/countries`: 16 country options.
- `/api/lookups/locations?country=VN&type=AIRPORT`: 2 airport options.
- Swagger's default compliance documents response:
  `#/components/schemas/ComplianceDocuments`.
- File preview documents application/pdf, image/png and image/jpeg responses.
- Anonymous private-file GET: 401.

Smoke evidence: `.local/document-modules-http-smoke.json`.
`git diff --check` passed. No FE edits, commit or push were performed for these
four modules. The earlier working-tree changes remain in place.

## Integration boundary

These modules extend the existing transport-order/F08 API. They do not retire
the earlier experimental `/api/v2` booking flow. The changed default documents
response is intentional; old array consumers use `?view=versions`, or migrate
to `.requirements` / `.versions` in the new envelope. Details and request
examples: DATA_DRIVEN_DOCUMENT_API.md and BE_HANDOFF_FOR_FE.md.

Document rules in migration024 are sample project policy. They are not a
verified legal checklist for international horse transport.
