# Reference flow migration

## Scope and rollout

Owner approved: multiple horses per booking, shared trips, horse dossiers,
tariff previews, reconciled payments and settlement before closure.
Existing `/api/transport-orders` contracts and their historical data stay intact.
New contracts live under `/api/v2`; this is an additive migration, not a rewrite
of previously accepted quotes or paid orders. Legacy orders are not silently
imported into the new lifecycle. Frontend must deliberately adopt v2.

Only EquiTransit_Demo / EquiTransit_Test may receive migrations 021-023. Back up the
sandbox before applying. Production promotion is a separate release decision.

## Aggregate boundaries

- Booking: one customer, route, many owned horses, immutable price snapshot.
- Dossier: one booking horse, independently reviewed required documents.
  Ordered transit countries drive extra permits; Manager reviews the declared
  route and a shared trip must match each booking's country sequence.
- Trip: many booking horses, one vehicle/crew and departure window. Customer
  endpoints never expose other customers in the shared manifest.
  Legacy and v2 consumers share the resource lock and scheduling probes.
  A recent assigned-Driver safety checklist is required for departure.
- Payment: staff-reconciled receipts, unique operation keys and bank references.
  Customer statements are not payments. Deposit opens preparation; full base
  fare is required before departure. Approved extras must be paid before close.
- Handover: recorded for each manifest horse with receiver/evidence identifiers.

## Transition map

Booking: SUBMITTED -> AWAITING_DEPOSIT -> APPROVED -> COMPLETED.
After delivery, unpaid extras give AWAITING_FINAL_PAYMENT before COMPLETED.
Trip: DRAFT -> PENDING_APPROVAL -> SCHEDULED -> IN_TRANSIT -> ARRIVED -> COMPLETED.
Documents: REQUIRED -> SUBMITTED -> APPROVED / REJECTED -> resubmission.
No removal of old operation/idempotency tables, audit or payment allocations.

## Important semantic distinctions

Public estimates are not supplier-confirmed airfare or legal permission.
Explicit tariff selections are snapshotted; invalid/missing prices fail closed.
Separation of schemas alone does not provide module isolation. Authorization,
transactions, constraints and contract tests enforce boundaries.

## Verification requirements

Use actual SQL Server sandbox for DDL, locking and transaction tests. Exercise
two customers sharing a trip; one rejected horse dossier; duplicate payment;
wrong-owner access; resource overlap; unpaid departure; unpaid extra closure;
retry after successful command; concurrent version conflict. Run existing suite
to verify legacy compatibility. Never claim production readiness from unit
tests alone.
