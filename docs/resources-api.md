# F09: resource catalogues, availability and fit

All endpoints return JSON and require a Bearer access token. Catalogue data,
supplier contact details, maintenance notes and policy evidence references are
staff-only. Availability returns blockers without another customer's schedule.

## Manager catalogues

Base: `/api/manager/resources`.

| Collection | Resource |
| --- | --- |
| `/vehicles` | ROAD fleet vehicle |
| `/stalls` | Physical AIR stall equipment |
| `/suppliers` | Resource owner or carrier supplier |
| `/fit-policies` | Versioned, source-backed fit policy |

Each collection supports these operations:

| Method | Suffix | Result |
| --- | --- | --- |
| GET | `?page=0&size=20` | Bounded page; size 1..100 |
| GET | `/{id}` | Current detail and 16-hex `recordVersion` |
| POST | none | Create; policy allocates next family version |
| PUT | `/{id}` | Full replacement with optimistic version check |

POST and PUT return 200, including a successful idempotent replay. Commands use
`{"command":{"operationId":"UUID","recordVersion":null},"data":{...}}`.
Create requires null version. Update requires the version returned by GET.
Use a new UUID for a new action; retry an uncertain action with the same UUID
and identical body. The original receipt is replayed even after later edits.
Changing input under the same UUID returns `RESOURCE_RETRY_CONFLICT` (409).
A stale/missing update version returns `RESOURCE_VERSION_CONFLICT` (409).

There is no DELETE. Archive vehicles/stalls/policies with `status=INACTIVE`;
archive suppliers with `active=false`. Historical IDs and references remain.
Vehicle/stall states are ACTIVE, INACTIVE and MAINTENANCE. A resource without
complete availability evidence cannot be used just because its state is ACTIVE.

Vehicle data: `vehicleCode`, `plateNumber`, positive `horseCapacity`,
`climateSystemType`, `status`, optional `ownerSupplierId`, required `window`.
Stall data: `stallCode`, positive `capacitySlots`, `internalHeightCm`,
`internalWidthCm`, optional positive `weightLimitKg`, `status`, optional owner,
required `window`. Supplier data: `name`, optional `contactName`, `email`,
`phone`, bounded `services` string array, `active`.

The window has optional `maintenanceFrom`, `maintenanceTo`,
`documentExpiresAt`, `notes` and required `bufferMinutes` (0..525600).
An absent maintenance end means maintenance continues indefinitely. An end
without a start, reversed interval or blank supplied text is rejected. Dates
are stored in UTC to second precision; the accepted year range is 1900..9997.
An unknown document expiry or legacy buffer remains PENDING, not available.

## Policy approval and existing F05 consumer

Policy data: `policyCode`, optional `stallId`/`carrierSupplierId`,
`slotQuantity` (1, 1.5, 3), positive optional `maxWeightKg`, `maxHeightCm`,
`maxWidthCm`, `effectiveFrom`, optional `effectiveTo`, `sourceUrl`,
`sourceFileKey`, and status DRAFT/APPROVED/INACTIVE.

APPROVED requires all three positive limits and an HTTP(S) source URL without
credentials or an opaque source file reference attested by the reviewer. Reviewer StaffID and review
time come from the authenticated active Manager, never the request body.
The URL is stored as evidence metadata; the API does not fetch arbitrary URLs.
This catalogue API does not upload files or establish carrier acceptance.

Approved/inactive policy content is immutable. Use POST with the same policy
code to create a new version. Approving it retires older APPROVED versions of
that family atomically; a prior approved version can only be archived intact.
Old rows remain as history. DRAFT policies can be edited before approval.

F05's existing `ServiceSourceRepository` consumes approved effective global
policies (no stall/carrier scope) with source and trusted review. F09 tests this
producer/consumer path using `StallFitEvaluator`. Equipment/carrier policies
require the F09 exact-context probe; they are not applied globally by F05.
No demo weight threshold or default one-slot approval is seeded.

## Availability probe

POST `/api/resources/{kind}/{id}/availability`, where `kind` is VEHICLE or
STALL. Only an active Manager or the case's assigned active Coordinator can
probe the current assessed revision. A Customer has no access. Other kinds or
a VEHICLE/AIR or STALL/ROAD combination return `RESOURCE_MODE_INVALID` (400).

Example request:

```json
{
  "orderId": 101,
  "revisionId": 201,
  "mode": "AIR",
  "start": "2027-02-01T08:00:00Z",
  "end": "2027-02-01T18:00:00Z",
  "carrierSupplierId": 21,
  "legId": null
}
```

Optional `legId` excludes the same leg when replacing its assignment. It must
belong to this order/revision; it cannot hide a different case's conflict.
Responses include kind, resourceId, recordVersion, asOf, policyId,
`carrierConfirmed=false`, status and a typed blockers array:

- AVAILABLE: all evidence checked by this probe is presently satisfied.
- PENDING: evidence is missing/ambiguous (buffer, expiry, measurements, weight
  limit, schedule, selected slots, or approved fit policy).
- UNAVAILABLE: an actual rejection exists, such as inactivity, maintenance,
  expired documents, overlap, insufficient capacity or unsuitable fit.

Intervals use `startA < endB && startB < endA`. Buffer is the minimum gap
applied once between uses: a 30-minute buffer permits exactly a 30-minute gap.
Zero-buffer adjacent intervals do not overlap. Maintenance uses the same gap;
document expiry must be strictly after the proposed end plus buffer.
Cancelled/rejected/superseded plans and inactive/cancelled assignments do not
consume active availability. Unknown active schedule windows block availability.

Stall fit checks the immutable current horse snapshot, chosen slots, physical
dimensions/weight limit, capacity, and a unique approved/effective policy for
the equipment/carrier context through the proposed end. Missing or ambiguous
policy stays PENDING. Fit is distinct from carrier acceptance or reservation.
ROAD capacity is checked for the single horse; vehicle count never enters F05
price calculations. Physical stall overlap is rejected even if unused slots
exist. Automatic pooling and shared-stall matching are outside F09.

## Case-scoped discovery and staff reads

`POST /api/resources/{kind}/availability?page=0&size=20` accepts the same
current-order/revision/window body as the single-resource probe. Kind is
VEHICLE with ROAD or STALL with AIR. Page is zero-based and size is 1..50.
Manager may inspect cases; Coordinator must have a live assignment on that
order. The parent order and global resource lock are retained for one page.

The response contains `items`, `page`, `size`, `totalElements`, `totalPages`.
Each item contains `resourceCode` and the typed `availability` result. Pages
include AVAILABLE, PENDING and UNAVAILABLE candidates, so missing evidence
is visible rather than silently removed. Totals count catalogue candidates,
not just AVAILABLE results. Other customers' schedules, supplier contacts and
internal maintenance notes are never returned. This read creates no booking.

`GET /api/resources/fit-policies?page=0&size=20` and
`GET /api/resources/fit-policies/{id}` let active Specialist and Manager read
source-backed policy versions for technical review. Specialist has no
catalogue mutation or approval permission; approval remains Manager-owned.

`GET /api/resources/legs/{leg}/vehicle` returns a Driver's assigned vehicle
code, plate, capacity, climate system and state. The persisted DRIVER
assignment must be active and unexpired, on a ROAD leg of the order's current
plan and revision. Another driver, an expired/revoked assignment or an old
revision cannot access it. Supplier and maintenance metadata are excluded.

The shared F05/F09 evaluator keeps its response contract and rejects a null
horse or false completeness flag as pending. Measurements must have a true
`measurementsComplete` flag, all three positive
values and a nonfuture measurement timestamp. A missing or invalid measurement
stays MEASUREMENTS_PENDING. All three slot configurations are evaluated
independently; the approved policy supplies every numeric threshold.

For an existing `legId`, mode and AIR carrier must match the persisted leg.
A consumer changing that context must persist the proposed context in its own
transaction before requesting the final F09 check. Policy effectiveness covers
the proposed start/end; a reviewed future policy can apply to a future trip.
Policies never use a future review timestamp. A draft policy cannot change its
family code in place; create a new family or immutable family version instead.

Cancelled AIR legs release physical stall availability. Reactivating such a
leg rechecks its stall assignments in the SQL guard, just as plan reactivation
does. An existing zero-length, reversed or missing active schedule remains
SCHEDULE_PENDING and cannot be treated as free equipment.

## F10 transactional consumer

A probe does not reserve anything; its lock ends when the request finishes.
F10 must call `ResourceAvailabilityService.requireAvailable(kind,id,request,
expectedVersion)` through the Spring bean in the transaction that writes the
assignment. Propagation is MANDATORY. Non-AVAILABLE results and stale resource
versions reject the write with 409. `lockAndCheck` is the non-throwing facade
for consumers that need the detailed blocker result in an existing transaction.

Lock order is parent TransportOrder, then transaction-owned resource app lock,
then resource rows. Retain all locks through the assignment write/commit; do
not call external services while holding them. All resource catalogue mutations
and the SQL schedule guards use the same resource lock. The lock is currently
global: safe serialization has a finite 5-second wait, not a scalability claim.
Busy catalogue/probe calls return `RESOURCE_BUSY` (409), not false AVAILABLE.

The SQL guards reject invalid vehicle/stall assignment windows and concurrent
physical double booking. Plan activation rechecks schedules. They do not
replace F10's actor, fit, carrier, quote, revision or departure-gate checks.
Writers on triggered tables must use `OUTPUT ... INTO` if requesting IDs.

## Deployment and verification

This transfer targets the current F08 project and uses
`database/migrations/007_resources_availability_fit.sql` after its baseline
and migrations 001-006. The full `EquiTransit.sql` also embeds this
007 definition. Do not bring migration010 or the clone's migrations
005-009 into this project. Migration006 uses `QuarantineAssessment`,
`RuleRequirementPolicy`, `RevisionChecklistState` and `RevisionRouteSource`.

Migration007 preserves the four original resource tables and adds rowversions,
nullable legacy buffers, idempotency receipts, schedule indexes and three SQL
guards. Existing buffers remain NULL until an authorized Manager configures
them. No policy, supplier, schedule or availability evidence is invented.
Missing schema prerequisites return `RESOURCE_SCHEMA_PENDING` (503).
Java code does not automatically run the migration.

F09 completion verifies Java compilation, Checkstyle, H2 regressions,
resource policy boundaries and OpenAPI contracts in an isolated build copy.
The build report records the actual results for the combined code.
Original-branch SQL Server test results do not establish native verification
for this combined F08/F09 project.

`ResourceIntegrationTest` remains opt-in through `EQ_RESOURCE_TEST_ENABLED`.
It requires `EQ_RESOURCE_TEST_DB_URL` for the exact isolated catalog checked
in that test, `EquiTransit_FigmaMigrationTest_20261013_143729`. Keep it disabled
for this transfer: native SQL Server testing is deferred until team integration.
Do not point fixture tests at the application database `EquiTransit`.
