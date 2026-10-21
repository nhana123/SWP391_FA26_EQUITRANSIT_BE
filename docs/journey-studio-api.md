# Creative Journey Studio — experimental sandbox contract

This additive feature requires migration
`018_visual_journey_graph_nodes.sql`. Apply and validate it only on an
explicitly named test/demo catalog with
`tools/test-graph-migration-sandbox.ps1`. The production `EquiTransit`
catalog is not authorized for deployment. Existing planning, confirmation,
departure and execution APIs remain the source of operational truth.

## Endpoints

Base: `/api/transport-orders/{orderId}/plans`.

| Method and suffix | Contract | Scope |
|---|---|---|
| POST `/graph-drafts` | `GraphDraftRequest` -> `JourneyGraphView` | Active Manager or assigned Coordinator |
| GET `/{planId}/graph` | `JourneyGraphView` | Editor, owning Customer, or assigned Driver |
| PUT `/{planId}/graph` | `GraphLayoutRequest` -> `JourneyGraphView` | Editor, editable current-revision draft only |
| GET `/{planId}/legs/{legId}/graph-drivers` | Up to ten `{staffId, fullName}` records | Editor, current-revision ROAD draft leg |

All responses are JSON. Swagger exposes explicit bounded DTOs; no raw
client JSON or arbitrary metadata maps are accepted. Missing migration or
disabled/untrusted graph constraints returns `503 GRAPH_SCHEMA_PENDING`;
the original F10 APIs do not require migration 018.

## Compile atomically

`GraphDraftRequest` contains:

- `draft`: unchanged `PlanDraftRequest`, including `command.operationId`
  (UUID), `command.recordVersion` (16 hexadecimal characters), exact
  `revisionId`, `setAsCurrentDraft`, and up to twenty `legs`.
- `checkpointNodes`: two to twenty-one typed `GraphNodeInput` records.
- `wires`: one `GraphWireInput` per leg with `sequenceNo`, `fromNodeKey`,
  and `toNodeKey`.
- `layout`: `{zoom, panX, panY}`; zoom is 0.5–1.5, coordinates have at most
  two decimal places and eight integer digits.

Each node has `nodeKey`, `nodeType`, `sequenceOrder`, `locationName`,
`countryCode`, nullable `plannedArrivalAt`/`plannedDepartureAt`,
`dwellMinutes`, optional `transitCustomsMode`, `transitPermitRef`,
`welfareCareNotes`, `quarantineReservationId`, `posX`, `posY`, and typed
optional `details`. See `JourneyGraphModels`/Swagger for details fields.

Graph keys are unique, including case variants under SQL Server's default
collation. The graph must be a single ordered path with one `ORIGIN` and
one `DESTINATION`, no branch, cycle, disconnected node, or duplicate wire.
Node sequences start at 1. Wire sequence N links node N to node N+1 and
maps to TripLeg sequence N. Node names match leg start/end names, and
adjacent node timestamps match leg departure/arrival instants exactly.
Intermediate node dwell must fit the connection gap. Root/destination
country codes match the exact order revision. Referenced quarantine
reservations belong to the same revision; supplied supplier IDs are active.

The Studio supplies `Asia/Ho_Chi_Minh` for both leg timezones and sends
offset-aware timestamps normalized to `+07:00`. JDBC persists UTC
DATETIME2 instants, preserving existing REST `OffsetDateTime` contracts.

The existing parent lock, revision/status guards, sourced distance rules,
retry receipt, and audit run inside the same transaction as node/link
persistence. The full graph input participates in the operation hash.
An identical retry returns the same plan ID; changing nodes/layout under
the same operation ID returns `409 PLAN_RETRY_CONFLICT`. A retry reads
the current graph projection, preserving any later valid layout change.

The response contains real `planId`/`legId` values. Existing assignment,
flight reservation, quarantine, insurance and confirm endpoints must then
be used normally with the latest parent recordVersion and a new UUID per
operation. Graph details are planning notes/preferences, not confirmed
reservations, carrier approval, cover, legal clearance or departure.

## Layout updates and version protection

PUT body:

```json
{
  "command": {
    "operationId": "65cdc568-15b4-4907-9cb9-29375ee79aba",
    "recordVersion": "000000000001A2B3"
  },
  "positions": [
    {"nodeKey": "origin", "posX": 100, "posY": 120},
    {"nodeKey": "destination", "posX": 600, "posY": 120}
  ],
  "layout": {"zoom": 1, "panX": 0, "panY": 0}
}
```

The position set must exactly match existing node keys. PUT changes only
node coordinates and the viewport, touches the parent rowversion, records
an operation receipt and writes an audit event. It cannot mutate leg
times, connections, assignments, bookings, node semantics or execution.
Confirmed/superseded/active plans are immutable. For semantic changes,
compile a new plan version; protected commitments retain existing guards.

Driver candidates support an optional literal name prefix `search`
(maximum 100 characters) and `bufferMinutes` (default 60). Results check
active account/profile/DRIVER role and buffered overlap in a bounded query.
They are advisory: normal road assignment rechecks live eligibility and
resource/driver conflicts transactionally.

## Role-safe reads and tracking

- Editors receive internal node details and assignment identifiers only
  within their existing Manager/assigned Coordinator scope.
- Customer reads require ownership and the current published plan in
  CONFIRMED, ACTIVE, IN_TRANSIT or COMPLETED state. Driver reads additionally require
  a live or completed DRIVER assignment on a ROAD leg in that plan.
- Customer/Driver node projections remove contact/receiver, policy,
  supplier metadata, transit permits, internal care/vet notes and quarantine
  IDs. Customer wire projections additionally remove resource/staff IDs and
  internal distance sources. No costs/offers/private evidence are selected.
- Wire statuses and actual times come directly from TripLeg. This endpoint
  never infers completion from planned times, simulates continuous GPS, or
  claims the horse is at a checkpoint without recorded execution evidence.
- The UI's latest observation uses actual departure/arrival instants only;
  tied second-resolution timestamps prefer the later leg, then its arrival.
- Use existing scoped F12 execution/welfare APIs for live facts. Customer
  welfare visibility remains `IsCustomerVisible=true`; Studio graph
  planning notes are never published as health observations.
- A legacy plan without saved nodes returns 200 with empty nodes/wires;
  the Studio may offer explicit conversion into a new draft. It must not
  pretend an inferred graph has already been saved.

## Verification

Unit suites: `JourneyGraphPolicyTest`, `JourneyGraphServiceTest`.
Native HTTP/SQL test:
`OperationalPlanIntegrationTest#graphCompilationAndLayoutPreserveOperationalFacts`
uses the existing guarded SQL test catalog and cleans only its own fixture
IDs. The sandbox migration script separately checks reapply, graph CRUD,
foreign keys/checks, protected trigger definitions, and fixture rollback.
These are functional proofs, not a production SLA or a deployment approval.
