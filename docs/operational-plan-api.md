# Multimodal operational planning API

Requires Java 21, Spring Boot 3.5.x and SQL Server. JSON APIs require Bearer auth.
Apply the restored application baseline and migrations 001-008 in order. No
production database deployment or native SQL Server verification was performed.

## Staff endpoints

Base: `/api/transport-orders/{orderId}/plans`. Active Manager or currently
assigned active Coordinator only. All nested IDs are scoped to the parent order.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | base | Paginated versions (`page`, `size`; maximum 100) |
| GET | `/{planId}` | Plan, legs, commitments and live confirmation blockers |
| POST | base | Create a new sourced DRAFT version |
| PUT | `/{planId}/legs` | Atomically add, remove or reorder the entire draft chain |
| POST | `/{planId}/legs/{legId}/road-assignment` | Assign ROAD vehicle and Driver |
| POST | `/{planId}/legs/{legId}/stall-assignment` | Assign AIR stall and carrier proof |
| POST | `/{planId}/legs/{legId}/flight-reservation` | Record supplier-backed flight |
| POST | `/{planId}/quarantine-reservation` | Record this plan's quarantine booking |
| POST | `/{planId}/insurance-policy` | Record this plan's verified policy |
| POST | `/{planId}/confirm` | Confirm against the exact accepted quote |

Every write carries `command.operationId` (UUID) and `command.recordVersion`
(the parent order's 16-hex rowversion). Same actor/order/UUID with identical
payload replays the receipt; a changed payload returns 409. Stale versions,
foreign revisions and foreign nested IDs cannot mutate a plan. Actor, author,
reviewer and source confirmation timestamps come from the authenticated session
and UTC server clock.

`PUT /{planId}/legs` takes `{command, legs}`. The entire replacement chain must
be valid before a write begins. Previous draft legs and commitments are cancelled
rather than deleted; new legs get new IDs. Bookings and assignments must be
recorded again against the new schedule. Confirmed/active versions are immutable.
Creating an unselected draft does not change CurrentPlanID. Only Manager may
replace a confirmed operating plan; in-transit replacement belongs to F13.
A draft whose departure has elapsed must be rescheduled before confirmation.
The stored plan window must match the first departure and last arrival of its
current leg chain.

## Route, time and evidence

- At most 20 ROAD/AIR legs, consecutive sequence starting at 1, positive sourced
  kilometres, continuous endpoints, and valid named IANA endpoint timezones.
- Compare timestamp instants; persist UTC datetime2(0). The next departure must
  respect the prior arrival plus connection/rest minutes. AIR requires a carrier
  and flight number and cannot use a ROAD vehicle or Driver assignment.
- ROAD requires an active eligible vehicle and Driver, including buffer/rest
  overlap checks. AIR requires a suitable sourced stall, carrier acceptance and
  confirmed flight matching the planned flight/carrier and exact revision offer.
- HELD does not mean CONFIRMED. Confirmation requires nonblank booking reference,
  evidence file key, authenticated confirmer/time and expiry beyond the relevant
  departure/booking start. Evidence keys reference the supplier documents recorded
  by staff; this API does not upload or authenticate the document bytes.
- Quarantine reads the live F08 assessment through ServiceAssessmentFacade.
  UNKNOWN/expired/revoked assessment blocks confirmation. NOT_REQUIRED is distinct
  from REQUIRED with positive days. A package selection cannot replace assessment.
- Quarantine and insurance rows are bound to PlanID as well as their original
  revision choices. Legacy unbound rows remain in the database but cannot confirm
  a new F10 plan. One draft cannot cancel another plan's reservation/policy.
- Insurance must carry verified proof and cover the complete plan window.
  Availability, driver eligibility, distance reviewer assignment and evidence
  validity are re-evaluated; stored PASS alone is insufficient.
- Flight source provenance is rechecked against the exact plan, revision, AIR
  leg, carrier and booking. Source evidence must have been recorded before the
  booking and its price offer must have been valid at confirmation time. Later
  price-offer expiry alone does not cancel an already confirmed ticket; ticket
  expiry, revoked source proof or an inactive carrier still blocks readiness.
- Flight space must cover the total confirmed eligible stall slots. HELD or
  unapproved equipment cannot be used to satisfy that capacity check.
- A confirmed quarantine booking must expire after the facility arrival time,
  as well as after the current server time.

## Public projections

- `GET /api/transport-orders/{orderId}/itinerary`: owning Customer only; shows the
  current confirmed/active/in-transit itinerary and public route details.
- `GET /api/driver/transport-orders/{orderId}/legs`: active Driver only; shows
  only their assigned ROAD legs and care instructions. `emergencyContact` gives
  the active assigned Coordinator's name and phone, or null if unavailable.
  The contact is read only after the Driver's own assignment has been checked.

Explicit DTO allowlists omit supplier prices/contacts, evidence keys, quote IDs,
unrelated staff identities and internal conflict notes. The designated emergency
contact is only included in the Driver projection. Drafts are hidden from the
Customer operational itinerary. Missing ownership/assignment returns no data.

## Internal integration

`PlanAssessmentAdapter` is the primary ServiceAssessmentFacade implementation.
It retains the original confirmed source when available; otherwise the scoped
PlanItineraryService.pricing(order, revision) supplies current, individually
sourced draft distances. This quantity feed does not accept a quote, confirm a
booking or approve departure. Missing planning/schema/evidence yields PENDING.
BE1 source files and migrations 001-004 are unchanged.

`PlanEvidenceRepository.requireAccepted` checks the actual Quote row, same order
and current revision, ACCEPTED status, acceptance timestamp and actual quoted
amount in the caller's transaction. It does not fabricate commercial readiness
or use the clone's payment/quotation modules. Audit appends to baseline AuditEvent
with authenticated UserID and a bounded server-generated plan/operation snapshot.
PlanOperation, business changes and audit share the transaction.

F11 may consume OperationalPlanService.operationalReady(order, revision, plan).
Only the current confirmed/active plan with current operational evidence can
pass. This method does not provide legal/financial PASS or departure permission;
SQL/infrastructure errors propagate and never become success. A historical gate
check timestamp is read from persisted GatesCheckedAt, not fabricated at read time.
Cancelled, rejected, delivered or closed orders cannot pass this gate. Horse
overlap is checked again when readiness is consumed, alongside current resources,
booking provenance, quarantine and insurance evidence.

## Verification boundary

Default verification includes pure policies, service boundary tests, H2 JDBC
quote/provenance/plan-scope tests, privacy projections, OpenAPI and authentication.
H2 does not validate SQL Server locking/triggers. Native integration tests remain
disabled until the team supplies a dedicated test database; never use EquiTransit
production for test fixtures. Migration syntax and extracted definitions were
checked offline using SQL Server ScriptDom, including embedded dynamic SQL.

See [F10 acceptance coverage](operational-plan-acceptance.md) for the mapping
between business rules, implementation and deferred native verification.

## Delivery deadline and draft pricing

Confirmation and live operational readiness compare plan arrival with the
immutable revision delivery deadline, by instant. Arrival at the deadline is
allowed; later arrival produces `DELIVERY_DEADLINE_EXCEEDED`. A missing deadline
produces `DELIVERY_DEADLINE_PENDING`. Drafts may retain these blockers for editing.

Pricing uses an eligible selected plan of the exact current revision. When the
operating pointer belongs to an older revision or is absent, one unique
DRAFT/PROPOSED/PLANNED candidate of the requested order and current revision may
supply sourced kilometres. Multiple unselected candidates remain `PENDING`;
select a draft explicitly or supersede the other drafts. Source and ownership
checks still apply. This read never changes `CurrentPlanID`. Public itinerary
and driver endpoints continue to use the operating pointer.

Regression coverage includes late arrival rejection, equal-instant arrival,
missing deadline, live readiness, new revision pricing, no current pointer,
ambiguous drafts, missing drafts, and revoked source assignment.
