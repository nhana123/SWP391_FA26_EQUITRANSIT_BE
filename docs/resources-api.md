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
credentials or an existing opaque source file key. Reviewer StaffID and review
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

Review `database/migrations/010_resources_availability_fit.sql` after the
001..009 baseline. It adds rowversions, nullable legacy buffers, a bounded
idempotency receipt table, schedule indexes and three enabled SQL guards.
It is additive and reapplicable, with required SQL SET options. Existing
buffers remain NULL until an authorized Manager configures them; no policies,
suppliers, schedules, capacities or availability evidence are invented.
Unsupported legacy asset states/supplier tag JSON return 409 for review.
Missing migration prerequisites return `RESOURCE_SCHEMA_PENDING` (503).

Only the isolated `EquiTransit_FigmaMigrationTest_20261013_143729` database was
used during implementation. Applying migration010 to the real database is a
separate deployment action. Java code never automatically migrates it.

Native tests require `EQ_RESOURCE_TEST_ENABLED=true` and
`EQ_RESOURCE_TEST_DB_URL` for that exact catalog, plus the SQL authentication
native library when using integrated security. Tests create disposable actors
and resources and remove committed race fixtures. Existing SQL suites remain
independently opt-in. Run `mvnw.cmd clean verify` with Java21 and the relevant
test variables; retain the normal Checkstyle checks.

Verification on 17/10/2026: Java21 `clean verify` passed 249 tests, including
140 native SQL tests (11 F09), with 0 failures/errors/skips and 0 Checkstyle
violations. Coverage includes sourced policy API to F05, role/assignment
isolation, stale versions/retries, maintenance/expiry/buffer boundaries,
transactional F10 checks, plan reactivation and two distinct orders competing
for one physical stall. Final migration reapply succeeded, all three guards
were enabled, and the 30 checked business tables contained 0 test rows.
