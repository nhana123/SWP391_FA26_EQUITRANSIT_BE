# F12 — Leg execution, welfare and tracking

F11 departs the first ROAD leg after live gates, authorization and receipt.
F12 runs subsequent legs and records actual events. Completing the final leg
leaves both the order and plan IN_TRANSIT: F14 owns delivery and closure.
PlannedDeparture/PlannedArrival are never changed by execution commands.

| Method/path (prefix `/api/transport-orders/{orderId}`) | Input | Output |
|---|---|---|
| GET `/execution?page=0&size=20` | page>=0, size=1..100 | ExecutionOverview |
| POST `/legs/{legId}/start` | TransitionRequest | ExecutionReceipt |
| POST `/legs/{legId}/complete` | TransitionRequest | ExecutionReceipt |
| POST `/legs/{legId}/milestones` | MilestoneRequest | ExecutionReceipt |
| POST `/legs/{legId}/welfare-logs` | WelfareRequest | ExecutionReceipt |

All endpoints use bearer auth and application/json. Every write contains:

```json
{
  "command": {
    "recordVersion": "000000000001A2B3",
    "operationId": "d86b4f63-5acc-4f20-8da3-e24323268814"
  },
  "occurredAt": "2027-02-01T08:00:00Z",
  "sourceCode": "CARRIER",
  "sourceReference": "Carrier movement message SQ-101"
}
```

Use the current order version from ExecutionOverview/ExecutionReceipt.
Retry a lost response with the exact original body/UUID/version: it returns
the original receipt, even if that version is now stale. Reusing a UUID with
another action, leg or payload returns409. New commands need the latest version.
Order locks precede plan/leg/assignment locks. AuditWriter appends ORDER_ACTIVITY
with trusted UserID; facts store StaffID. Neither identity comes from the client.

Access and progression:

- Active MANAGER, or the active case COORDINATOR, can write ROAD/AIR.
- Active DRIVER can write only their directly assigned ROAD leg. DRIVER cannot
  write AIR. Completed assignments permit historical reads/replay, not new writes.
- CUSTOMER may only read their own order. They cannot post execution facts.
- Start requires SequenceNo>1, previous sequence COMPLETED with ActualArrival,
  target CONFIRMED, and current order/plan IN_TRANSIT. First-leg start uses F11.
- Complete requires IN_TRANSIT and actual arrival>=actual departure. Start and
  complete append LEG_STARTED/LEG_COMPLETED facts; posting a milestone does not
  change any state. Manual milestoneType is POSITION, DELAY or UPDATE.
- All facts have a timezone-aware occurredAt, separate server recordedAt, source
  and reference. Facts must fall within the actual leg interval and not in the
  future. Late/out-of-order observations are retained; pagination is by recording
  time with identity tie-breaker, not a fabricated chronological rewrite.
- sourceCode is DRIVER_REPORT, CARRIER, AIRPORT or AUTHORITY. AIR rejects
  DRIVER_REPORT. This is evidence entered by staff, never continuous aircraft GPS.
- Optional latitude/longitude are paired decimal strings with six fractional
  digits maximum, bounded to ±90/±180. ETA cannot precede the event.

MilestoneRequest adds milestoneType, latitude/longitude, locationDescription,
estimatedArrivalAt, delayReason, publicNote, internalNotes and customerVisible.
WelfareRequest adds healthStatus (NORMAL/CONCERN/URGENT), temperatureCelsius,
waterIntakeLiters, feedIntakeKg, behaviorNotes, careNotes, publicNote,
customerVisible and optional supersedesLogId. Decimal inputs are bounded strings
with at most two fractional digits and six integer digits, never floating point.
feedIntakeKg maps to the physical FeedAmountKg column, without renaming it.

Welfare and milestone rows cannot be updated/deleted, including through SQL.
Corrections append a new welfare row referencing the same leg/plan. Only one
immediate successor is allowed; further corrections supersede that successor.
A visible fact cannot be corrected with a hidden successor. URGENT creates one
OPEN/CRITICAL WELFARE IncidentReport in the same transaction and returns its ID.
This observation opens staff response; it is not an automated diagnosis, legal
clearance or approval of an additional customer charge. F13 owns incident actions.

Public projection and limits:

- Only IsCustomerVisible=1 facts are counted/paged for CUSTOMER. Internal notes,
  behaviorNotes and careNotes are removed, including from JSON serialization.
- DRIVER histories and legs are scoped to their assigned legs. Staff must remain
  active. Coordinator access requires a current order assignment.
- Legs and both histories use page/size independently; all three counts are
  visibility-scoped totals. lastUpdateAt is the latest visible recording instant,
  including off-page facts; null means no visible update exists.
- Leg pages are bounded to size<=100 without truncating longer plans. Fixed SQL
  queries
  fetch pages/totals; there are no database calls inside result-row loops.

Errors:400 malformed DTO/pagination;401 invalid account/token;403 wrong role or
assignment;404 foreign order/leg;409 ORDER_VERSION_CONFLICT, IDEMPOTENCY_CONFLICT,
EXECUTION_NOT_DEPARTED or revision mismatch;422 invalid chronology, AIR source,
coordinate pair/range, previous-leg state or correction lineage;503
EXECUTION_SCHEMA_PENDING. Reload after a409 except an exact retry.

Deployment: review/apply migration014 explicitly; startup never migrates.
It also bootstraps the seven F12-F14 core tables and adds private operation
receipts, visibility/source fields, composite FKs, correction index and append-only
triggers. Production deployment is separate; tests use an isolated catalog.

Native suite: EQ_EXECUTION_TEST_ENABLED=true and EQ_EXECUTION_TEST_DB_URL pointing
to exactly EquiTransit_FigmaMigrationTest_Execution_20261018. Fixtures roll back;
append-only triggers are never disabled for cleanup. See database-sync.md.
