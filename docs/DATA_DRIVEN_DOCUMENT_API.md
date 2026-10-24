# Location and document modules — FE integration

Scope: existing `/api` TransportOrder/Revision/Horse workflow. This change
does not introduce another booking API, migrate historical V1 orders into
Journey V2, or retire the existing `/api/v2` experiment. No FE source changed.

## Deployment

Apply `database/migrations/024_location_local_documents.sql` after the
existing Compliance migrations through 020. The script explicitly refuses
databases other than `EquiTransit_Demo` and `EquiTransit_Test`. Running it
again preserves existing rows and configuration.

`tools/apply-document-module-demo.ps1` creates a COPY_ONLY/CHECKSUM backup,
verifies it with RESTORE VERIFYONLY, then applies the migration twice to
verify idempotency. Applied to Demo on 2026-10-24; 25 locations, 16 countries
(the requested 15 plus Germany for FRA), 8 document policies.

Backup verified before deployment:
`C:\Program Files\Microsoft SQL Server\MSSQL15.MSSQLSERVER\MSSQL\Backup\EquiTransit_Demo_before_024_20261024_152247.bak`.

`app.storage.directory` / `EQ_STORAGE_DIRECTORY` defaults to
`storage/uploads/documents`. Files use UUID names and remain outside public
static resources. Back up this directory together with DB metadata.
No Supabase/S3 dependency is needed for these APIs. Existing binary files in
ComplianceFile remain readable; this migration does not move historical bytes.

## 1. Cascading route selectors

Public GET endpoints:

- `/api/lookups/countries`
  returns `[{countryCode,countryName,supportsRoad,supportsAir}]`.
- `/api/lookups/locations?country=VN&type=CITY`
- `/api/lookups/locations?country=VN&type=AIRPORT`
  return `[{locationId,countryCode,locationType,locationName,locationCode}]`.

Only active catalog rows are returned. Country must be two uppercase letters;
type must be CITY or AIRPORT. Unknown country yields an empty list; invalid
parameters yield 400. SupportsRoad/Air describe available catalog entries,
not proof that a specific international route is legally or operationally open.
The capability list updates from SQL data, not FE hardcoded arrays.

## 2. Private upload and inline preview

Both require bearer authentication.

`POST /api/files/upload`, multipart/form-data:

- `file`: PDF, PNG, JPG or JPEG, nonempty, maximum 10 MiB.
- `category`: optional logical grouping, default `documents`.

Response:

```json
{
  "fileKey": "84032724-eac7-45df-8d04-e54f092279d0",
  "fileName": "certificate.pdf",
  "fileSizeBytes": 10240,
  "contentType": "application/pdf"
}
```

Content is parsed, not trusted solely from extension/MIME. Active PDF content
and unsupported images are rejected by the existing legal-file policy.
The filesystem write is removed if the metadata transaction rolls back.

`GET /api/files/{fileKey}` returns the bytes with Content-Type,
Content-Disposition: inline, private/no-store and nosniff headers.
Uploader and Manager can read; after a horse version links the file, that
horse's order owner and actively assigned case staff can read it as well.
Unknown/invisible key returns 404; anonymous request returns 401. Opaque UUID
keys alone never grant access. Use authenticated fetch -> Blob/object URL for
browser preview; do not put access tokens in URLs.

Oversized multipart requests: 413 FILE_SIZE_INVALID. The service also checks
the 10 MiB bound. Invalid paths/category/size produce 422; malformed parsed
content produces the existing 409 FILE_* errors. Missing schema returns 503
FILE_SCHEMA_PENDING / LOCATION_SCHEMA_PENDING rather than exposing SQL.

## 3. Approval automatically provisions the checklist

Manager's existing `OrderAssessmentService.decide(APPROVED)` publishes a
synchronous internal event after assignments and status update, inside the
same transaction/order lock. The listener uses internal repository access,
not the Manager calling Specialist-only F08 methods. Provisioning errors roll
back approval; replaying an approved operation does not duplicate checklists.

PRE_EXPORT requirements come from `dbo.ComplianceDocumentPolicy`: baseline
FEI_PASSPORT, COGGINS_EIA, EQUINE_INFLUENZA, EXPORT_PERMIT; destination-specific
GLANDERS_CFT samples for GB/FR/DE; EVA_TEST only for a STALLION snapshot.
The EIA/Influenza sample maximum ages are 30/180 days. ARRIVAL and
QUARANTINE_RELEASE retain the existing defaults. Existing explicitly reviewed
route rules take precedence; default rules are scoped to the correct horse.

These are project demo workflow defaults, not verified veterinary/legal advice.
The assigned Specialist must verify actual route requirements. Changing
published requirements follows the existing rule/checklist reassessment flow.

`app.compliance.auto-approval=false` supports legacy/manual rollout and is
used by the old manual-flow regression tests. Default is true when migration
024 and the F08 schema are present. Historical orders bootstrap on a compliance
GET; there is no bulk rewrite of old orders.

`GET /api/transport-orders/{order}/compliance/documents`
now returns a typed envelope for the two document tabs:

```json
{
  "recordVersion": "000000000001A2B3",
  "requirements": [{
    "requirementId": 101,
    "horseId": 12,
    "documentType": "FEI_PASSPORT",
    "phase": "PRE_EXPORT",
    "status": "PENDING_SUBMISSION",
    "submissionId": null,
    "documentVersionId": null,
    "instructions": "...",
    "feedback": null,
    "attachment": null
  }],
  "versions": [],
  "totalElements": 8,
  "page": 0,
  "size": 20
}
```

Optional `phase`, `page`, `size` filter/page the checklist. Version history
uses the same page/size independently. `totalElements` counts requirements.
`GET .../documents?view=versions` retains the former bare version-list shape.
**FE integration change:** consumers of the old default bare array must use
`versions` in the envelope or explicitly request `view=versions`.
The existing phase overview endpoint and PENDING enum remain unchanged;
PENDING_SUBMISSION is the new document-list presentation status.

## 4. Two-tier submission through the same existing endpoint

`POST /api/transport-orders/{order}/compliance/requirements/{id}/upload-and-submit`

Exactly one source is permitted: fileKey, existingDocumentVersionId, or the
legacy contentBase64 upload. Current owner or assigned Specialist may submit.
Every variant requires current `recordVersion` and UUID `operationId`.

New local file:

```json
{
  "fileKey": "84032724-eac7-45df-8d04-e54f092279d0",
  "documentNumber": "CERT-001",
  "issuedDate": "2026-10-24",
  "expiryDate": "2027-04-01",
  "recordVersion": "000000000001A2B3",
  "operationId": "d86b4f63-5acc-4f20-8da3-e24323268814"
}
```

Reuse an existing horse paper:

```json
{
  "existingDocumentVersionId": 123,
  "recordVersion": "000000000001A2B3",
  "operationId": "a7cf1df3-77c7-4ad2-aa21-5d84041efa9d"
}
```

Reuse verifies the same horse and document type, issue/expiry, revocation and
latest-version constraints. It inserts only DocumentSubmission; it does not
duplicate the file or DocumentVersion. Policy maximum ages are checked at
submission and readiness evaluation. Both source choices invalidate previous
clearance and await Specialist review; reusing a document never carries an
approval from another submission automatically.

Response retains ComplianceReceipt: `entityId` (SubmissionID), `operationId`,
`recordVersion`, `fileKey` when created/attached. Identical operation retries
return the original receipt; changed payload with the same operation conflicts.
Legacy base64 request fingerprints retain their prior serialization fields.
Reviews, rejection notes and original order audit continue through existing F08.
Local-file evidence also works with automatic authority procedures on Clear.

## Verification

`DocumentWorkflowSqlTest`: real MockMvc/security and native SQL, auto approval
enabled, rolled-back orders/horses/users/files; covers selectors, conditional
stallion rules, local preview and authorization, upload/reuse/retry, wrong horse,
expired policy age, invalid sources, all-three-phase clearance and bad uploads.

`ComplianceIntegrationTest`: legacy manual/rule-assessment mode, including
RULE_ASSESSMENT_PENDING before bootstrap. Its old version-history assertions
use the explicit compatibility view. Default PRE_EXPORT fixture now expects
the four configured standard papers on migration024 databases.

Use `tools/verify-document-modules.ps1` for the native suites; see the local
verification logs for the recorded run. No production migration was applied.
