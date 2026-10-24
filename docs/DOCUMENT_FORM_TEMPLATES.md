# Hybrid document form templates

## Schema and seed

`database/migrations/025_document_form_templates.sql` adds FormCode,
IssuingAuthority, SubAuthority, CertTextTemplate, BadgeColor, EmblemIcon and
TemplateFileKey to DocumentTypeCatalog. The nullable PDF key references
LocalFileObject(FileKey). Missing catalog is created; migration024 and baseline
migration history must already exist. Only Demo/Test databases are allowed.

The migration inserts missing core types and fills only NULL template metadata;
reruns preserve staff customizations, IsActive and existing PDF assignments.
History key: `025_document_form_templates`.

Six reference layouts: FEI_PASSPORT, COGGINS_EIA, EQUINE_INFLUENZA,
EXPORT_PERMIT, GLANDERS_CFT, EVA_TEST. The form codes supplied in the task
are reference labels; seeded text explicitly marks the forms as unsigned
project examples. They are not authenticated authority-issued certificates or
verified current international legal forms. Staff supply actual verified blank
PDFs through the upload/link flow. The migration does not fabricate those PDFs.

Applied on EquiTransit_Demo on 2026-10-24, twice to check idempotency, after a
COPY_ONLY/CHECKSUM backup verified with RESTORE VERIFYONLY:
`C:\Program Files\Microsoft SQL Server\MSSQL15.MSSQLSERVER\MSSQL\Backup\EquiTransit_Demo_before_025_20261024_161324.bak`.
No DDL was issued to the real EquiTransit database.

## Public lookup

`GET /api/lookups/document-templates`

Returns all active catalog types. The six seeded core types have populated
layout fields; other active types may have NULL metadata until configured.

`GET /api/lookups/document-templates/{typeCode}`

Returns one active type or 404 DOCUMENT_TEMPLATE_NOT_FOUND. Both lookups allow
anonymous reads; disabled types are excluded. Missing migration025 returns
503 DOCUMENT_TEMPLATE_SCHEMA_PENDING.

DTO fields:

```json
{
  "typeCode": "COGGINS_EIA",
  "displayName": "EIA Test",
  "formCode": "AGID-EIA-TEST FORM 10-11",
  "issuingAuthority": "Authorized veterinary authority",
  "subAuthority": "Authorized diagnostic laboratory",
  "certTextTemplate": "UNSIGNED REFERENCE FORM. Sample: ______.",
  "badgeColor": "volcano",
  "emblemIcon": "blood",
  "templateFileKey": null,
  "hasTemplateFile": false,
  "templateDownloadUrl": null
}
```

When a PDF is linked, hasTemplateFile is true and templateDownloadUrl is
`/api/files/{templateFileKey}`. Layout content, issuer text, color and icon
tokens come from DB. Treat CertTextTemplate as plain text; do not evaluate
HTML/script or treat it as proof of veterinary approval. FE can render the
layout and call window.print(); no FE source is changed in this task.

## Publish a blank PDF

The project currently has no ADMIN role. MANAGER is the administrative role;
MANAGER and SPECIALIST may publish templates. CUSTOMER, COORDINATOR and DRIVER
cannot update the catalog file link.

1. Authenticate as Manager/Specialist.
2. Upload a new blank PDF using existing `POST /api/files/upload` multipart
   with `file`. Existing 10 MiB/content-validation rules apply.
3. Call `PUT /api/compliance/document-templates/{typeCode}/file`:

```json
{"templateFileKey":"84032724-eac7-45df-8d04-e54f092279d0"}
```

Response 200 is the updated DocumentTemplateView. The update is transactional
and locks the active catalog row; repeating the same PUT is idempotent.
PDF must belong to the current uploader, exist on disk, have application/pdf
metadata and not already be referenced as private horse DocumentVersion
evidence. Upload a separate blank PDF, rather than publishing customer papers.

Errors: 400 malformed/missing UUID, 401 anonymous, 403 wrong role,
404 missing/inactive type or non-owned file, 422 TEMPLATE_PDF_REQUIRED,
409 TEMPLATE_FILE_ALREADY_USED.

## Download access

`GET /api/files/{key}` stays authenticated. A PDF referenced by an active
template is readable by any authenticated user, including Customer. Other
private files keep their existing uploader/owner/case access rules.
Replacing a link or disabling the type removes this template-based access to
the previous file, unless another active template or existing private access
still authorizes it. Anonymous file GET remains 401.

Use authenticated fetch -> Blob/object URL for preview/download; a bearer token
must not be placed in the URL. No static uploads directory is exposed.

## Verification

`DocumentTemplateSqlTest` uses real SQL Server, MockMvc and existing test
profiles. Set EQ_DOCUMENT_TEMPLATE_TEST=true and EQ_DOCUMENT_TEMPLATE_DB_URL
to an authorized Demo/Test JDBC URL, with the existing native JDBC auth DLL:

```powershell
.\mvnw.cmd -B test-compile checkstyle:check test '-Dtest=DocumentTemplateSqlTest' '-DargLine=-Djava.library.path=.local/tools/sqljdbc-native'
```

`tools/verify-document-templates.ps1` configures/restores those variables and
checks that all three tests executed rather than being skipped.
The tests roll back catalog links, file metadata and fixtures; local test files
are deleted by the transaction completion callback. They cover six core
layouts, missing/inactive forms, both allowed roles, replacement/idempotency,
authenticated downloads, role denial, non-PDF/unknown/non-owned files and
private-evidence publication rejection.

Local evidence: `.local/document-template-test.log`,
`.local/document-templates-final.log`, `.local/template-migration.json`.

Verified on 2026-10-24:

- Targeted DocumentTemplateSqlTest: 3 executed, 0 failures/errors/skips.
- Full regression: 421 discovered, 252 executed and passed, 169 gated tests
  skipped. Includes native Compliance/Documents/Journey and template suites.
- Checkstyle: zero violations. Packaging: BUILD SUCCESS after stopping the
  managed Demo runtime that Windows was holding open on the previous JAR.
- Database: all six layouts populated; migration025 registered once; template
  foreign key enabled/trusted; zero test file rows/links and zero leftover
  files in `.local/template-test-files` after rollback.
- Restarted managed Demo on 8081 with the packaged JAR (PID21120 at check).
  Live health UP; public list/detail return the six core forms among all active
  types; Swagger references DocumentTemplateView and bearer security on PUT;
  anonymous PUT returns 401. Smoke: `.local/document-templates-http-smoke.json`.

No FE changes, production DDL, Git commit or push were performed in this task.
