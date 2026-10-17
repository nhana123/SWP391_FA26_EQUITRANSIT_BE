# F11 — Verify Departure Gates & Physically Receive the Horse

## Overview
Provides real-time 3-gate departure readiness evaluation (`Financial`, `Legal/Document`, `Operational`), manager-only departure authorization (`Authorize Departure`), road driver physical horse receipt & microchip/passport verification (`HorseReceipt`), and atomic first-leg departure (`depart`).

## Database Migration
Explicitly apply [`database/migrations/012_departure_gates_horse_receipt.sql`](../database/migrations/012_departure_gates_horse_receipt.sql) after `001..011`:
- Creates index `IX_HorseReceipt_PlanLeg` on `dbo.HorseReceipt(PlanID, LegID, ReceiptID DESC)`.
- Creates trigger `dbo.TR_Departure_FirstLegGuard` on `dbo.TripLeg` to enforce `PASS` gates, active `MANAGER` authorization, and a `MATCHED` + `ACCEPTED` `dbo.HorseReceipt` before `SequenceNo = 1` sets `ActualDeparture` or `LegStatus = 'IN_TRANSIT'`.

## Endpoints (`/api/transport-orders/{orderId}/departure`)

| Method | Path | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/transport-orders/{orderId}/departure` | `CUSTOMER` (redacted), `MANAGER`, assigned `COORDINATOR`, assigned `DRIVER` | Evaluates live 3-gate readiness, authorization state, and recorded horse receipts. |
| `POST` | `/api/transport-orders/{orderId}/departure/gates/evaluate` | Active `MANAGER`, assigned `COORDINATOR` | Evaluates all 3 gates as of `now` and persists gate statuses on `dbo.TransportPlan` (clearing stale authorization if any gate is blocked). |
| `POST` | `/api/transport-orders/{orderId}/departure/authorize` | Active `MANAGER` only | Authorizes departure when all 3 gates evaluate to `PASS` in the same transaction. Cannot bypass Legal, Financial, or Operational gates. |
| `POST` | `/api/transport-orders/{orderId}/departure/horse-receipts` | Assigned active `DRIVER` on first `ROAD` leg | Records physical horse pickup inspection (`ObservedMicrochip`, `ObservedPassportNumber`, `IdentityCheckResult`, `ReceiptStatus`). |
| `POST` | `/api/transport-orders/{orderId}/departure/depart` | Assigned active `DRIVER` on first `ROAD` leg | Re-verifies all 3 gates (`BR-09`), manager authorization, and latest `MATCHED`/`ACCEPTED` `HorseReceipt`, then atomically sets `ActualDeparture`, `LegStatus='IN_TRANSIT'`, `PlanStatus='IN_TRANSIT'`, and `OrderStatus='IN_TRANSIT'`. |
