# BE2 handoff — F13 incidents/adjustments and F14 handover/settlement

Source: current backend controller/DTO/facade contracts and migration014.
F13 (#13) and F14 (#14) remain unimplemented business workflows. Bootstrap is
physical readiness, not evidence that their APIs or business rules are complete.
Read this file with [F12 API](../leg-execution-api.md),
[schema setup](../database-sync.md), [F07](../manual-bank-payments-api.md),
[F08](../compliance-api.md), [F10](../operational-plan-api.md),
[F11](../departure-api.md) and [F16](../activity-api.md).

## Mandatory architecture and safety

- Java21 / Spring Boot3.5.16 / SQL Server, layered Controller → Service →
  Repository, parameterized JDBC. Never use Hibernate auto-update for schema.
- Keep one horse per order and immutable revision/accepted-quote history.
- Resolve CurrentActorService live; UserID is the AuditWriter actor, StaffID is
  operational staff. Check active account/profile and actual assignment per write.
- Lock TransportOrder WITH(UPDLOCK,HOLDLOCK) first, then its plan/legs and other
  dependencies in consistent order. No HTTP/email/file transfer under DB locks.
- Every write requires recordVersion matching 16 hex characters and UUID
  operationId. Parent rowversion changes on write. Replay exact requests before
  stale-version validation, with receipt/input fingerprint bound to actor/order.
- Distinct operation UUIDs racing on one stale version must have one winner.
  Same UUID and payload returns the original receipt, not another obligation.
- AuditWriter.append(order, ActivityCode, operationId, JsonNode) appends
  ORDER_ACTIVITY with redacted metadata. There is no AuditCategory enum in the
  current Java API. Use the actual facade, not a new direct AuditEvent INSERT.
- OpenAPI3.1: application/json, explicit typed DTOs/enums and examples, String
  @Size(min=1,max=...), decimal money strings with at most2 fractional digits,
  BigDecimal/HALF_UP, bounded collections/pagination. No raw Map in public DTOs;
  no type:null sibling of $ref. Optional object refs use anyOf(ref,null).
- Checkstyle:4 spaces, no tabs, <=80 Java characters/line, Javadoc and naming.
- Opaque file keys need MIME/size/path/ownership checks and private download.
  Do not accept arbitrary filesystem paths or external proof URLs as verification.
- All schema changes are reviewed, additive/idempotent scripts. Never run tests
  or migrations on real EquiTransit without separate authorization.

## Integration boundaries that already exist

F12 LegExecutionService owns start/complete, timestamp/source checks, mode-aware
assignment and immutable milestone/welfare writes. LegExecutionRepository is its
JDBC adapter, not a permission facade for other modules. F13/F14 must authorize
their own commands and use the same parent lock; never insert fake execution
facts or bypass transitions by calling repository.update directly.

F12 source events:

- First leg departed through F11. Subsequent start requires the previous leg
  COMPLETED. Complete records ActualArrival and leaves order/plan IN_TRANSIT.
- URGENT welfare appends the observation and creates an OPEN/CRITICAL incident
  with IncidentType=WELFARE in the same transaction; receipt includes incidentId.
  Consume that incident; do not create a second incident merely for the same event.
- Welfare corrections use SupersedesLogID on the same (LegID,PlanID), retain all
  history and cannot silently hide a previously public observation.
- GET execution provides bounded customer-visible facts and lastUpdateAt.
  Customer-visible text is explicit PublicNote, never internal care/health notes.

F07 financial gate is provided by the actual signature:

```java
CommercialReadiness readiness(long order, long revision,
        long quote, OffsetDateTime asOf);
```

Call QuotationCommitmentFacade.readiness inside the authorized consumer's
transaction (Propagation.MANDATORY). Its CommercialReadiness has total,
paidAmount, obligations, departureBlockers, settlementBlockers,
departureReady() and settlementReady(). F14 must require settlementReady(), not
departureReady():100% of quote schedule paid from reconciled net allocations,
no pending refunds, no undated/pending obligations. PaymentReport is never paid.
Completed refunds/reversals reduce net paid. Do not recompute finance from raw
PaymentReport or use Quote.ACCEPTED as proof of payment.

F08 ComplianceReadinessFacade.readiness(order, revision, phase, asOf) also needs
an enclosing transaction. FINAL_DELIVERY must evaluate the applicable ARRIVAL /
QUARANTINE_RELEASE phase with live sources/expiry, not reuse PRE_EXPORT PASS.
Manager cannot override Specialist/legal results.

F16 AuditWriter plus NotificationWriter are shared producers; no event bus is
required. Free-text customer/staff remarks must not leak into public audit data.
Use real operational facts and distinguish DELIVERED from financially CLOSED.

## Physical schema foundation in migration014

The approved 57-table baseline plus001–014 constructs the schema without
copying production data. Migration014 explicitly guards CREATE for:

| Table | Core keys and boundaries |
|---|---|
| LegMilestoneLog | MilestoneID; composite LegID+PlanID FK; source/event/recorded times; visibility; immutable |
| HorseWelfareLog | WelfareLogID; composite LegID+PlanID FK; supersedes same leg; nonnegative feed/water; immutable |
| IncidentReport | IncidentID; LegID, ReportedBy; severity/time/status, immediate action/contact; acknowledgement/resolution staff+time; EvidenceFilesJSON |
| OrderChangeRequest | ChangeRequestID; OrderID+BaseRevisionID composite reference; RequestedBy UserID; JSON changes/reason; staff response |
| OperationalAdjustment | AdjustmentID; PlanID, IncidentID, optional ChangeRequestID; JSON proposal, additional estimate, approval and customer response |
| TripHandover | HandoverID; LegID, RecordedBy; handover type/time, receiver/authority/signature, condition/outcome, evidence and customer response |
| ActualCost | CostID; PlanID; optional LegID+PlanID composite FK; code/amount/currency, RecordedBy and evidence |

Additional F12 technical table: LegExecutionOperation (OrderID,UserID,OperationID
primary key; RequestHash; JSON ReceiptJSON). Do not reuse its keys/receipts for
F13/F14 commands; add separate private operation tables if needed.

F12 added visibility/source/recorded-time fields, append-only triggers and
same-leg correction protection. FeedAmountKg is the physical column;
feedIntakeKg is the F12 DTO field. Never rename existing columns from prompts.
Exact complete core definitions are checked into database/bootstrap/000_figma_schema.sql,
database/migrations/014_leg_execution_welfare_tracking.sql and
database/verify-f12-f14-schema.sql. Treat these as executable schema references.
Use reviewed new migrations015/016 for genuinely missing constraints/metadata;
do not rewrite applied014 or renumber existing migrations.

## Issue13 — F13 controlled incidents and adjustments (migration015)

Expected screens/contracts: staff incident list/detail/report, acknowledge,
resolve; customer change request and scoped status; Coordinator adjustment
proposal; customer explicit response; Manager approval. Design exact endpoint
names/typed DTOs before implementation and document them for FE.

Required behavior:

1. ROAD Driver reports only assigned legs; Coordinator handles assigned AIR
   with external source. Coordinator/Manager acknowledge and resolve within
   their authority. Urgent safety action and contact happen promptly; they do
   not wait for commercial approval or create veterinary diagnoses automatically.
2. Customer change requests reference their order/base revision and do not
   directly rewrite the active plan, accepted quote, route or price.
3. Every adjustment remains linked to the correct plan, incident/change request,
   explicit proposal, author and decision. Validate cross-parent composite keys.
4. AdditionalCostEstimate is company estimation, not an authorized customer debt.
   A customer acknowledgement or silence is not price consent. Commercial change
   must go through BE1 revision/quote/acceptance and applicable gate rechecks.
5. Do not mutate immutable F12 facts. Append correction/incident evidence.
   Include external source, actual vs recorded time and private proof ownership.
6. Customer projection excludes supplier costs, actual costs and internal notes.

Tests: wrong actor/assignment/ownership, stale revision/version, same-UUID replay,
concurrent decisions, urgent-welfare linkage, plan/incident mismatch, explicit
customer acceptance/rejection, and no automatic charge/gate bypass. SQL tests
run on a dedicated name-guarded catalog; clean owned fixtures without disabling
append-only business constraints. Extend ApiDtoContractTest/OpenApiTest.

Suggested prompt to assigned BE2:

> Implement GitHub Issue13 on feature/13-incidents-controlled-adjustments from
> current origin/dev. Read this handoff, Issue13 and relevant physical schema.
> Preserve F12 execution ownership and immutable observations. Add minimal
> idempotent015 only for missing private receipts/constraints. Implement scoped
> incident acknowledgement/resolution, customer change requests, controlled
> adjustments and explicit customer consent; coordinate BE1 quote changes.
> Do not invent authority clearance, price consent or automatic debt. Prove
> unit/OpenAPI/native SQL tests, 0 Checkstyle issues and no remaining fixtures.
> Document exact FE contracts; open PR to dev and report unimplemented scope.

## Issue14 — F14 final handover and financial closure (migration016)

Expected screens/contracts: intermediate/final handover recording, receiver
identity/authority and proof, customer response/exception, internal ActualCost
entry, operational delivery state and separate settlement/closure readiness.

Required behavior:

1. Airport/quarantine/intermediate handovers do not mark order DELIVERED.
   FINAL_DELIVERY requires completed execution, the correct receiver/authority,
   time/condition/signature/evidence and applicable arrival/release legal gates.
2. Customer response is explicit ACCEPTED / EXCEPTION / NOT_DELIVERED per
   existing schema. Silence or staff clicking delivered is not customer consent.
3. Record actual costs in USD with exact arithmetic, source/receipt evidence and
   StaffID. Optional LegID must belong to PlanID. ActualCost is company cost;
   it must not become an automatically billable customer surcharge.
4. Operational delivery and financial closure remain distinct. Before CLOSED,
   require CommercialReadiness.settlementReady(), applicable legal release,
   completed final handover and no outstanding blockers defined in Issue14.
   Check refunds/reversals freshly in the same parent-locked transaction.
5. Emit DELIVERED/CLOSED through AuditWriter only after the respective transition
   actually commits; separate authorization/consent from notification delivery.
6. Protect replay, recordVersion, active assignment, customer ownership and
   concurrent closure vs refund/reversal/change request. No Manager bypass of
   legal evidence or manufactured customer confirmation.

Tests: intermediate!=final, unfinished legs, wrong receiver/order/phase, expired
release, missing signature/proof, customer rejection, partial vs fully reconciled
payment, pending/completed refund and reversal, cost parent mismatch, duplicate
closure and competing financial write. Native SQL tests must run, not skip.

Suggested prompt to assigned BE2:

> Implement GitHub Issue14 on feature/14-final-handover-settlement from latest
> dev after F13 dependencies are reviewed. Read this handoff and actual F07/F08
> facades. Use migration016 for missing constraints/receipts only. Implement
> intermediate/final handover with receiver proof and explicit customer outcome,
> internal ActualCost, DELIVERED separately from CLOSED. Closure requires live
> settlementReady() and applicable legal release under the parent lock. Preserve
> immutable F12 facts, accepted pricing and all ownership/role checks. Prove
> unit/OpenAPI/native SQL tests, cleanup and 0 Checkstyle; document FE contracts
> and open PR to dev. Do not claim missing external evidence as completed.
