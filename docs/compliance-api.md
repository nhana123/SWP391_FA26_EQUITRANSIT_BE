# F08 — Legal documents and compliance

Requires JDK 21, Spring Boot 3.5.16 and SQL Server compatibility level >= 130.
The compliance API is enabled with `be2.compliance.api-enabled=true`.

## Database

Apply the original schema, then migrations 001–006 in order. Migrations
001–004 belong to BE1 and have not been edited by the F08 completion changes.

- 005 owns document catalogs, source rule versions, immutable documents,
  requirements, submissions, reviews, authority procedures and clearances.
- 006 adds `QuarantineAssessment`, `RuleRequirementPolicy`,
  `RevisionChecklistState` and read-only route projections. It does not
  alter `OrderRevision` or the BE1 quarantine table/constraints.
- Confirmed quarantine decisions are also published transactionally to the
  original `RevisionQuarantineAssessment` contract: source, future validity,
  positive days when REQUIRED, and the real Specialist ID. PENDING exists in
  the BE2 table; the prior confirmed BE1 row is superseded, preventing F05
  from using an obsolete decision. Errors roll the whole transaction back.
- An expired BE2 decision cannot fall back to an older BE1 decision.
- No migration invents government sources, legal exemptions, approvals or
  airport/country mappings. Reviewed source data must be entered explicitly.

Migration 006 and the 006 section in `EquiTransit.sql` use the same
definitions. The route view is `RevisionRouteSource`; country codes
must contain exactly two uppercase letters before rule matching.

Rerunning 006 renames the previous F08-prefixed BE2 objects without
copying or deleting their rows. A legacy `QuarantineAssessment`
compatibility view over the BE1 table is replaced by the BE2 table;
the underlying BE1 rows remain available through the read fallback.
If both old and new tables exist, deployment stops for reconciliation.
Any BE1 schema changes from an older 006 remain untouched.

## Rules and revision changes

`ComplianceRuleVersion` and `Airport` contain reviewed official source data.
Rules match the exact revision snapshot's origin, destination, optional
transit country and planned departure within the rule's effective window.
Missing dates, unresolved transit airports, missing reviewed sources or
missing checklist policies keep readiness blocked.

An assigned Specialist confirms each rule version's policy with:

`POST /api/staff/transport-orders/{orderId}/compliance/rules/{ruleId}/policy`

```json
{
  "scopeCode": "HORSE",
  "mandatory": true,
  "validityBasis": "DELIVERY",
  "instructions": "Instructions supported by the reviewed source"
}
```

Scope is HORSE or SHIPMENT. Validity is through DEPARTURE or DELIVERY, as
required by that source. Policies are immutable; changes require a new rule
version. Policy confirmation records the real Specialist and an audit event.
Source rule and airport administration still uses reviewed SQL data; this
endpoint does not create or claim to verify an external regulation.

Every authorized F08 checklist/readiness operation reconciles the exact
current revision before evaluating it. A revision change immediately stops
old clearances from opening the new revision's gates. Matching policies
generate missing requirements under transaction locks, without duplicates.

Still-valid, previously approved evidence for the same rule, horse, type and
order is attached to the new requirement as PENDING_REVIEW. A Specialist must
confirm applicability to the new revision; no approval or reviewer is
fabricated. Files expiring before the required departure/delivery date remain
PENDING. Old submissions, decisions, procedures and clearances remain history.

## Files and history

Set `EQ_F08_STORAGE_ROOT` to a private persistent directory outside public web
content, e.g. `D:/EquiTransitData/compliance`. Default:
`.local/compliance-files` under the application working directory. Mount and
back up this directory together with SQL data when deploying/restarting.

- Upload: `POST /api/horses/{horseId}/documents/upload`, multipart `file`,
  `documentType`, optional document number and issue/expiry dates.
- PDF/JPEG/PNG headers, positive size <= 10 MiB and SHA-256 are verified.
- Opaque keys derive extensions from verified MIME. Existing files cannot be
  overwritten. Private owner/horse/hash metadata survives process restart.
- Binary reads recheck disk size/hash and immutable database hash; a cache
  cannot conceal modified disk contents.
- Customers download only owned horse files. Specialist downloads use the
  assigned order/requirement route and verify the submitted horse/type/version.
- Per-horse transaction locks serialize version allocation across instances.
  Re-registering the same verified key and document metadata returns the
  original version; conflicting metadata returns 409. A new multipart upload
  creates a new key/version; it is not a replay of the previous request.

Public submission/review history, including CHANGES_REQUESTED feedback:

`GET /api/transport-orders/{orderId}/compliance/requirements/{requirementId}/submissions?page=0&size=20`

Pages include accurate totals and preserve more than 100 submissions.
Current owners/assigned staff may inspect requirements from historical
revisions of that order. Foreign orders return no history. Storage keys,
hashes and internal procedure evidence are not exposed in this history.

## Gate behavior

PRE_EXPORT and QUARANTINE_RELEASE are independent. Readiness checks live rule
coverage, the latest non-expired PASS, current mandatory approved evidence,
external procedure decisions/expiry, and confirmed quarantine. PASS requires
an explicit future expiry. A COMPLIANT flag without actual latest approved
evidence cannot open the gate. Database failures propagate fail-closed.

## Verification

Run `./mvnw.cmd verify` with the project's normal test configuration.
`ComplianceCompletionTest` uses H2 fixtures to exercise revision reconciliation,
delivery-date validity, idempotent registration and paginated public feedback.
`ComplianceDurableStorageTest` launches separate JVMs to verify restart persistence,
overwrite rejection and disk tampering detection.

H2 results are not SQL Server evidence. The dedicated
`ComplianceNativeIntegrationTest` uses the schema-only original 56-table fixture and
the actual migrations 001–006. It compares BE1 schema metadata before/after
006, reruns 006, executes the inherited business workflows, verifies native
quarantine constraints, persisted case assignment, a complete approval/gate
flow, and concurrent version allocation. No production data is restored.

The native suite creates and drops only its own generated database named
`EquiTransit_F08Test_<UUID>`. Use a test SQL Server login with permission to
create a disposable database. Set credentials in environment variables;
never commit them or put passwords in a connection URL:

```powershell
$env:EQ_F08_NATIVE_MASTER_URL = 'jdbc:sqlserver://localhost:1433;databaseName=master;encrypt=true;trustServerCertificate=true;loginTimeout=10'
$env:EQ_F08_TEST_DB_USERNAME = 'dedicated_test_login'
# Set EQ_F08_TEST_DB_PASSWORD securely in this process environment.
./tools/test-f08-native.ps1
```

On this machine, `./tools/test-f08-native.ps1` defaults to `localhost:1433`
with Windows Authentication. Run it from your normal Windows terminal.

Windows Authentication is also supported: omit username/password and add
`integratedSecurity=true` to the master URL. Run from a terminal holding a
valid Windows identity and install the JDBC auth DLL matching the configured
JDBC JAR. The runner uses `%LOCALAPPDATA%/EquiTransit/tools/sqljdbc-native` when present.

Without `EQ_F08_NATIVE_ENABLED=true`, native tests are skipped deliberately.
A skipped native suite is not a passed integration check and does not close
the feature. Record successful native results before merging/closing issue 8.
