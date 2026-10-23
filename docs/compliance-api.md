# F08 compliance API contract

Base: `/api/transport-orders/{order}/compliance`. Bearer authentication and
JSON media type on every operation. Only the owning Customer and the assigned,
active Specialist can read. Only that Specialist can record legal decisions;
Manager/Coordinator cannot override them. Staff identity comes from active
StaffProfile, while audit records the authenticated UserID. F08 uses the
separate lockLegalRevision assignment boundary for APPROVED, IN_PREPARATION,
CONFIRMED, IN_TRANSIT and DELIVERED cases; the original F04 preparation-only
boundary is unchanged. Terminal/cancelled cases cannot accept new legal writes.

## Workflow for FE

The streamlined BE-02 workflow is documented in [BE-02.md](BE-02.md).
GET initializes missing checklists by default; the explicit assessment and
two-step document endpoints below remain available for advanced workflows.

1. Specialist POST `/rules` publishes an immutable source-backed rule version
   for this case's origin/destination, a phase and optional transit country.
   RuleCode retains the same route/phase/type across versions. Document type
   codes are bounded uppercase ASCII catalog identifiers; inactive types cannot
   be used. A new catalog type may be registered with its displayName. SourceURL
   must be an HTTPS reference; the server never fetches arbitrary supplied URLs.
   This records a human legal assessment, not verification of the issuing body.
2. Specialist POST `/checklists` with phase, optional `transitCountryCodes`,
   `sourceReference` and `validUntil`. Omitted/empty countries are read from
   the current F10 checkpoint countries, excluding origin/destination.
   No airport-to-country mapping is invented. Airports present
   with no resolved countries are rejected. Absent active base/transit rules
   are provisioned as explicit default workflow templates. At most100 active
   rules per phase. Matching current rules
   become mandatory requirements; every assessment snapshot is retained in
   ComplianceChecklistHistory while ComplianceChecklist is the current projection.
   Global rules can affect other matching cases: readiness always reevaluates
   their current version. An expired latest rule never falls back to an older one.
3. Customer POST `/documents` with documentType, filename, declared contentType,
   contentBase64, issuedDate, expiryDate and optional documentNumber. Horse is
   inferred from the owned single-horse order. Responses identify a new immutable
   DocumentVersion and opaque fileKey; VersionNo grows under the horse lock.
4. Customer POST `/requirements/{id}/submissions` with documentVersionId.
   It must match this horse/type and this current revision's active requirement.
   Existing versions may be reused, but each new requirement needs submission
   and Specialist review. Reuse never implies auto-approval.
5. Specialist POST `/submissions/{id}/reviews` with decision APPROVED/REJECTED/
   REVOKED and customer-facing feedback (required for REJECTED; `notes` and
   `reason` are aliases). The submission pins the exact
   version. A superseded submission cannot be reviewed as the current one.
6. Optionally, Specialist POST `/files`, then POST `/procedures`
   with ruleId, authorityName, applicationReference, decision, sourceReference,
   evidenceFileKey, decidedAt, expiresAt and optional holdReason. APPROVED needs
   inspected evidence uploaded by the recorder for this order/revision, a real
   recorded decision time and future expiry. PENDING/REJECTED/REVOKED needs a hold
   reason. These are recorded external facts, not an API issuing a legal permit.
   The latest result for that rule/phase wins; older approvals cannot bypass it.
7. Specialist POST `/assessments` records sourced applicability/requiredDays and
   clinical visit/test/dose/certificate counts with validUntil. All four counts
   are required; missing JSON values never silently become assessed zeros. REQUIRED needs
   positive days; NOT_REQUIRED needs null, never0. Positive days are synchronized
   to QuarantineChoice with StaffID/time; NOT_REQUIRED clears its days and
   confirmation metadata. F05/F06 consume the revision assessments with their
   existing assignment/expiry checks. Pending quantities remain pending.
   Nonaccepted commercial preparation is superseded on quantity changes;
   accepted quote terms/amounts remain immutable and require the commercial
   change workflow if the new facts affect the customer's obligations.
8. Specialist POST `/clearances` with phase and expiresAt. Every current rule,
   exact document review and required authority result must pass, and clearance
   expiry cannot exceed the earliest evidence/checklist/rule expiry. If all
   documents are approved and a required procedure has never been recorded,
   Clear records a Specialist document clearance using the approved file and
   review ID. Explicit pending/rejected/revoked/expired procedures still block.
   Optional
   rule EffectiveTo=null represents an open-ended rule, bounded by checklist
   validity; it is not a missing-rule exemption. AuthorityRequired=false must
   be explicitly recorded in the sourced rule. The API requires an explicit
   authorityRequired boolean; omission is rejected. Existing rows default
   to authority evidence required at the SQL schema level.

Legacy POSTs include `command`: `{recordVersion, operationId}`. The new
`/requirements/{id}/upload-and-submit` accepts these two fields at the top level.
Version is the
16-hex SQL rowversion from the current order/overview; operationId is a UUID.
A retry must keep the entire original body and UUID; it returns the original
receipt without duplicate files/reviews/clearances. Different input with that
UUID returns409. A new action uses a fresh UUID and latest recordVersion.
Receipts contain entityId, operationId, recordVersion and nullable fileKey.
Refetch after stale-version conflicts; do not automatically replay a modified
legal command. The order's rowversion is advanced atomically with each mutation.

## Read operations

- GET base `?phase=PRE_EXPORT&page=0&size=20`: current revision, recordVersion,
  fresh readiness and paginated requirements, including latest attachment
  metadata, effective status and review feedback. Missing checklists for all
  three phases are initialized for approved/preparation/confirmed/transit cases.
  Pass `bootstrap=false` for a read without provisioning. Internal readiness
  consumers never bootstrap. Page0..100000,size1..100.
  `status` on a requirement is its checklist projection; `decision` is the exact
  latest submission review. The root readiness/blockers determine legal status.
- GET `/rules?phase=...`: bounded current route rule versions and public sources.
- GET `/documents?page=0&size=20`: retained immutable version metadata.
  Legacy external keys/dates may be null; external paths are never exposed.
- GET `/files/{key}`: authorized JSON base64 content; no public filesystem URL.
- GET `/procedures?phase=...`: latest recorded result for each rule in that phase.
- GET `/assessments`: sourced counts for the current revision or nullable values
  when unavailable/expired; null is never presented as assessed zero quantities.

PRE_EXPORT, ARRIVAL and QUARANTINE_RELEASE are distinct phases. A READY result
in one phase never clears another. Requirement/authority changes invalidate
current clearances; uploads elsewhere for the same horse also cause the facade
against that horse to detect replacement. Historical files/reviews are retained.
Revocation POST `/documents/{id}/revocations` needs the assigned Specialist and
reason; the original content and version row's file fields remain unchanged.

## Files and privacy

PDF, PNG and JPEG only;5MiB decoded; sanitized simple ASCII filenames (no slash,
backslash, traversal or executable extension). Declared MIME/extension must match
parsed content. Images are decoded with a20MP bound. PDFBox3.0.8 checks PDF structure,
page count<=100 and rejects encrypted PDFs, scripts, actions and attachments;
object traversal is bounded. Evidence bytes are retained unchanged with SHA256,
including signatures, in private transactional SQL storage with opaque UUID keys.
These checks do not certify a document's legal authenticity or replace Specialist
verification. Source for the PDF parser release/security:
https://pdfbox.apache.org/download.html and https://pdfbox.apache.org/security.html.

Customer DTOs expose no supplier cost, margin, ActualCost, internal notes,
reviewer identity or unrestricted JSON maps. Authority references/instructions
are legal workflow data. Feedback/internal free text is never automatically
broadcast as an audit notification; audit receives bounded IDs/action/phase only.
Files must not be rendered as active unsandboxed HTML by FE.

## Consumer facade and transaction boundary

`ComplianceReadinessFacade.readiness(orderId,revisionId,phase,asOf)` requires
an existing authorized transaction. It retains parent order -> horse locks and a
shared legal-rule catalog lock; rule publication uses the corresponding exclusive
lock. Customer/staff access is the caller's responsibility for internal consumers.
Result: revisionId,phase,asOf,expiresAt,status,blockers. Revision mismatch, missing
schema/assessment/rules, source/expiry, document and authority evidence each block.
No cached clearance bit can authorize F11 departure. Rule, assignment, file,
review and phase evidence are reread transactionally; F11 must evaluate this
facade in its final departure transaction along with its other gates.

All `datetime2` writes/reads use explicit UTC LocalDateTime conversion; document
legacy expiry dates include the complete UTC expiry day. One-step submissions
also retain exact UTC ExpiresAt; whichever bound is earlier governs readiness.
Set-based queries load current
requirements, reviews, authority facts and active Specialist IDs in bounded
batches, with no queries in per-row validation loops. File histories use SQL
pagination. Query timeout10s, transaction timeout15s, catalog lock timeout5s.

Errors:400 invalid DTO/query,401 unauthenticated,403 forbidden role,404 unavailable
owned/assigned evidence,409 stale/retry/business conflict,503 missing schema
through020.
No automatic Manager override, fabricated clearance, financial acceptance,
quarantine release or physical departure is implemented by these endpoints.

## Deployment and repair

Apply prerequisites001–008 then009 using the deployment account after normal
backup/review.009 is transactional, re-runnable, additive and seeds no legal data.
Existing external file keys/source-less authority rows remain historical; they
cannot become valid private evidence merely by migrating. Upload validated new
versions and record sourced results. Do not rewrite/delete legal history to fix
an expired or replaced file. Use a new version/review/result instead.

BE-02 applies additive migration020 and idempotent seed only to EquiTransit_Demo;
both scripts reject any other catalog. BE-02 native fixtures also run only there
and remove their own IDs. No production deployment is part of this task.
No guarantee of operational departure readiness
is inferred from compilation or a PRE_EXPORT clearance.
