# Journey v2: sandbox API contract

Enable with `app.journey.enabled=true` **only after migrations 021-023** on
EquiTransit_Demo or EquiTransit_Test. Startup rejects production activation.
Bearer authentication and the existing five EquiTransit roles are reused.
No FE files or production DB data are changed. Existing v1 orders remain v1;
they are not silently converted into v2 bookings.

## Conventions

All times are UTC `yyyy-MM-dd'T'HH:mm:ss` (no local timezone interpretation).
Money is USD with at most two fraction digits. Responses currently retain SQL
column names (e.g. BookingID, Status, Version). A booking detail contains
`horses`, `paidAmount`, `extraAmount`, `tracking` in addition to header columns.
Customer tracking is restricted to that booking, never the entire shared trip.
Each command uses a new UUID operationId plus the aggregate's current numeric
version. Replay with identical input returns the saved response. Reusing an
operationId with changed input fails with JOURNEY_RETRY_CONFLICT.
IDs, customer identity, prices paid and permissions are never trusted from JWT
claims alone: existing live actor/profile resolution is used.

The exact request definitions are JourneyModels.java. `command` below means:

```json
{"operationId":"4ab8244c-c492-4f42-9f16-a12533f0147c","version":1}
```

## API inventory

All paths have prefix `/api/v2`.

| Method/path | Role | Request/result |
|---|---|---|
| GET /tariffs | Customer/Manager/Coordinator | Active retail tariffs |
| POST /estimates | Customer/Manager/Coordinator | Estimate -> lines,total,estimateOnly |
| POST /bookings | Customer | BookingRequest -> detail, HTTP 201 |
| GET /bookings | Customer/assigned staff/Manager | Latest 100 scoped summaries |
| GET /bookings/{id} | Owner/assigned staff/Manager | Detail including own tracking |
| POST /bookings/{id}/approve | Manager | Approval -> detail |
| POST /bookings/{id}/payments | Manager | PaymentRequest -> detail |
| POST /trips | Coordinator | TripRequest -> trip, HTTP 201 |
| GET /trips | Manager/assigned Coordinator/Driver | Latest 100 summaries |
| GET /trips/{id} | Manager/assigned Coordinator/Driver | Trip and manifest |
| POST /trips/{id}/manifest | Assigned Coordinator | ManifestRequest -> trip |
| POST /trips/{id}/actions/submit | Assigned Coordinator | Command -> trip |
| POST /trips/{id}/actions/approve | Manager | Command -> trip |
| POST /trips/{id}/actions/reopen | Assigned Coordinator | Return to draft/review |
| POST /trips/{id}/predeparture-check | Assigned Driver | SafetyRequest -> trip |
| POST /trips/{id}/actions/depart | Assigned Driver | Command -> trip |
| POST /trips/{id}/deliveries | Assigned Driver | DeliveryRequest -> trip |
| POST /trips/{id}/extras | Manager | ExtraRequest -> trip |
| POST /trips/{id}/actions/close | Manager | Command -> trip |
| GET /bookings/{id}/documents | Owner/assigned staff/Manager | Requirements/latest review |
| POST /requirements/{id}/upload-and-submit | Owner/assigned Specialist | DocumentRequest |
| POST /requirements/{id}/reviews | Assigned Specialist | ReviewRequest |
| POST /booking-horses/{id}/clearances/{phase} | Assigned Specialist | Command using BOOKING version |
| POST /bookings/{id}/files | Owner/assigned crew/Manager | DocumentRequest -> fileKey |
| GET /files/{key} | Owner/assigned crew/Manager | Download, no-store |

## Booking and estimate example

```json
{
  "operationId":"4ab8244c-c492-4f42-9f16-a12533f0147c",
  "originCountry":"VN",
  "destinationCountry":"SG",
  "originAddress":"Customer pickup location",
  "destinationAddress":"Customer delivery location",
  "pickupFrom":"2030-01-02T09:00:00",
  "pickupTo":"2030-01-02T11:00:00",
  "deliveryBy":"2030-01-04T10:00:00",
  "horseIds":[101,102],
  "transitCountries":["MY"],
  "estimate":{
    "mode":"ROAD","distanceKm":100,"horseCount":2,"tariffIds":[1]
  }
}
```

IDs are examples: use owned horses and real active tariff IDs from GET /tariffs.
The nested estimate object is also the POST /estimates body. Supported units:
KM, HORSE_KM, HORSE, POLICY, SHIPMENT. Other units fail explicitly rather than
silently underquote. A base ROAD_TRANSPORT/AIRFARE tariff is mandatory.
Creation recomputes price server-side and freezes horse/price snapshots.

Approval: `{command,specialistId,coordinatorId,depositPercent,routeReviewed}`.
routeReviewed must be true: Manager explicitly reviews the declared country
sequence, including a direct journey with an empty transitCountries array.
Payment: `{command,amount,bankReference}`; bankReference globally unique.
Only staff reconciliation credits funds; no customer self-credit endpoint.

## Trip, dossier and settlement

TripRequest: `{operationId,mode,vehicleId,supplierId,capacity,driverId,
originCountry,destinationCountry,startsAt,endsAt,transitCountries}`.
ROAD uses the actual vehicle capacity and checks expiry/maintenance. AIR uses
an active supplier and a Manager-approved capacity; this does not book a flight.
ManifestRequest: `{command,bookingHorseIds:[...]}`. Horses must share mode,
country route, identical ordered transit countries, compatible pickup/delivery
windows and assigned Coordinator.
Duplicate physical horses or simultaneous active manifest entries are rejected.

Each horse gets PRE_EXPORT, ARRIVAL, QUARANTINE_RELEASE requirements. Effective
configured origin/destination/transit document types are used, with baseline
defaults for absent phases and transit permit requirements for missing country
rules. Transit-specific type keys are distinct, so one country's approval does
not satisfy another country's required permit. Default requirements are
workflow scaffolding, not legal certification.
DocumentRequest: `{command,fileName,contentType,contentBase64,expiresAt}`.
The command version is REQUIREMENT version for upload-and-submit, BOOKING
version for generic file upload. Allowed PDF/PNG/JPEG, maximum 5 MiB; magic
signature checked. ReviewRequest: `{command,submissionId,decision,notes}`;
rejection requires notes. Latest review notes and file key appear on GET docs.
Resubmission invalidates that horse's phase clearance.

Clear uses BOOKING version; all required latest documents must be APPROVED and
valid beyond planned delivery. One horse's clearance does not clear others.
SafetyRequest: `{command,vehicleSafe,waterReady,beddingReady,horsesReceived}`;
all four checks must be true and recorded by the assigned Driver in the last
24 hours. Reopen invalidates the checklist and requires another Manager review.
Departure requires full base payment for every manifest booking and PRE_EXPORT
clearance for every horse. Delivery requires ARRIVAL and QUARANTINE_RELEASE.
DeliveryRequest: `{command,bookingHorseId,receiverName,evidenceKey,occurredAt}`.
Evidence must belong to that horse's booking; time cannot precede departure.
ExtraRequest: `{command,bookingId,amount,reason}` allocates an approved expense
to a manifest booking. Trip close checks all delivery evidence and full base +
approved extras payment. Fully delivered/settled bookings become COMPLETED.

## Boundaries of this increment

- No automatic geocoding/road routing; distanceKm is explicit estimate input.
- No automatic live flight purchase, gateway callback or supplier settlement.
- No copying legacy welfare/incident/Studio history into new shared trips.
- No migration of historical v1 orders or replacement of v1 FE screens.
- Ordered transit country selection is supported; a visual checkpoint editor
  and geographic discovery of countries are not part of this API increment.
  Baseline rules are not a substitute for reviewed authority requirements.
- V2 writes use a transaction-scoped lifecycle lock for sandbox correctness.
  This deliberately favors correctness over throughput, not production scale.
- Legacy and v2 scheduling share the existing resource transaction lock. Both
  vehicle/driver probes and horse confirmation check the other lifecycle.

This increment is an opt-in foundation for the main reference lifecycle, not a
claim that every ancillary reference feature is migrated or production ready.
