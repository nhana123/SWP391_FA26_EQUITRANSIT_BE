# F10: multimodal operational plan

All endpoints return JSON and require a Bearer access token. Only an active
Manager or the transport order's currently assigned active Coordinator
(`OrderAssignment` with `AssignmentRole='COORDINATOR' AND EndedAt IS NULL`) may
read or write operational plans. Customer and Specialist tokens are rejected
with `403 Forbidden`.

## Endpoints

Base: `/api/transport-orders/{orderId}/plans`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `` | List versioned operational plans for the order |
| GET | `/{planId}` | Inspect a plan version, its legs, reservations and blockers |
| POST | `` | Create next versioned `DRAFT` multimodal plan and legs |
| POST | `/{planId}/legs/{legId}/road-assignment` | Assign ROAD fleet vehicle and `DRIVER` staff |
| POST | `/{planId}/legs/{legId}/stall-assignment` | Assign physical AIR stall and carrier acceptance |
| POST | `/{planId}/legs/{legId}/flight-reservation` | Record supplier-backed AIR flight reservation |
| POST | `/{planId}/quarantine-reservation` | Record facility quarantine reservation for revision |
| POST | `/{planId}/insurance-policy` | Record verified insurance policy for revision |
| POST | `/{planId}/confirm` | Link accepted quote and confirm plan as `CurrentPlanID` |

Every write request includes a `command` envelope:
`{"command":{"operationId":"UUID","recordVersion":"16-hex"}, ...}` where
`recordVersion` is the current 16-character uppercase hexadecimal optimistic
version of the parent `TransportOrder`. Repeating the same `operationId` with
identical payload replays the stored receipt; repeating with a modified payload
returns `PLAN_RETRY_CONFLICT` (`409 Conflict`). Stale parent `recordVersion`
returns `PLAN_VERSION_CONFLICT` (`409 Conflict`).

## Business invariants

1. **Plan Versioning & F05/F06 Distance Feed:**
   - Creating a plan allocates `VersionNo = MAX(VersionNo) + 1` on the target
     `OrderRevision`. Earlier `DRAFT` plans on the same revision become
     `SUPERSEDED`, and the new draft becomes `TransportOrder.CurrentPlanID`
     when no confirmed plan is active.
   - Each `TripLeg` (`ROAD` or `AIR`) requires positive `distanceKm` and
     non-blank `distanceSource`; the backend stamps `DistanceConfirmedBy`
     (authenticated staff ID) and `DistanceConfirmedAt` (`CK_TripLeg_DistanceSource`),
     feeding `ServiceSourceRepository.itinerary()` for F05/F06.
   - Confirmed or superseded plans are immutable (`PLAN_IMMUTABLE`).

2. **Mode & Resource Assignment Rules (`BR-10`):**
   - `ROAD` legs require an available `FleetVehicle` (validated via
     `ResourceAvailabilityService.requireAvailable(VEHICLE, ...)` with optimistic
     `vehicleRecordVersion`) and an active `DRIVER` `StaffProfile` in
     `TripAssignment`. `DRIVER` overlap is prevented across active plans using
     `startA < endB AND startB < endA` plus `BufferMinutes` (`DRIVER_SCHEDULE_OVERLAP`).
     Stalls and flights are rejected on `ROAD` legs (`LEG_MODE_MISMATCH`).
   - `AIR` legs forbid vehicles and `DRIVER` assignments. They require a
     physical `StallAssignment` (validated via `ResourceAvailabilityService`
     for `STALL` fit and schedule overlap, plus `CarrierAcceptanceStatus='ACCEPTED'`
     and `CarrierApprovalFileKey` to confirm) and a `FlightReservation` tied to
     an active `AIRFARE` `SupplierOffer` on the same `RevisionID` with
     `UsableCapacitySlots >= requiredSlots`, `ReservationReference`,
     `EvidenceFileKey`, future `ExpiresAt`, and `ConfirmedBy`.
   - `HELD` or `PENDING` reservations, or an unbooked `SupplierOffer`, never
     satisfy plan confirmation.

3. **Quarantine, Insurance & Plan Confirmation (`BR-03`):**
   - `QuarantineReservation` must match the revision's `QuarantineChoice` and
     span at least the Specialist's `RequiredDays` (`QUARANTINE_DAYS_SHORT`).
   - `InsurancePolicy` must match the revision's `InsuranceChoice`, cover the
     full `PlannedStartTime..PlannedEndTime` window, and carry `VerifiedBy` +
     `EvidenceFileKey`.
   - Confirming a plan locks `TransportOrder` (`UPDLOCK, HOLDLOCK`), verifies
     `QuotationCommitmentFacade.requireAccepted(orderId, revisionId, acceptedQuoteId)`,
     requires zero confirmation blockers, locks the `Horse` row to prevent
     cross-order active plan schedule overlap (`HORSE_SCHEDULE_OVERLAP`), and
     sets `PlanStatus='CONFIRMED'` and `TransportOrder.CurrentPlanID`.
