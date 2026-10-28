# Manager one-click deposit request

## Contract

`POST /api/manager/transport-orders/{id}/request-deposit`

Authenticated, active MANAGER with a staff profile only. Body:

```json
{
  "totalAmount": 1000.00,
  "depositAmount": 250.00,
  "currency": "USD",
  "note": "Please pay the deposit after reviewing the quote.",
  "recordVersion": "000000000001A2B3"
}
```

Both amounts are required, positive, at most two decimal places and 16 integer
 digits. Deposit cannot exceed total. Currency defaults to USD if omitted;
other currencies are rejected because the current ledger supports USD only.
Note is optional, customer-visible, at most 1000 characters. Use the latest
order recordVersion. No customerId is accepted from the client.

200 response:

```json
{
  "orderId": 101,
  "recordVersion": "000000000001A2B4",
  "depositRequested": {
    "totalAmount": 1000.00,
    "depositAmount": 250.00,
    "note": "Please pay the deposit after reviewing the quote.",
    "requestedAt": "2026-10-27T11:00:00Z",
    "isPaid": false,
    "currency": "USD",
    "quoteId": 201,
    "paymentRequestId": null,
    "paymentAvailable": false
  }
}
```

## Workflow and integration

- Allowed order states: SUBMITTED, APPROVED, with a current submitted revision.
- SUBMITTED: persist the request and show it to the owner; Manager approval
  remains mandatory before any payment/report/allocation. This is the user's
  final Option 1. This endpoint never approves the order or assigns staff.
- Reuse a current ISSUED/ACCEPTED quote if its total and deposit match exactly.
  Never rewrite an accepted price, payment amount, allocation or receipt.
- Without a current quote, Manager explicitly issues a manual assessed quote
  (`MANAGER-DEPOSIT-V1`) and DEPOSIT/BALANCE schedule. The provided amount is
  a Manager assessment, not a computed tariff. A 100% deposit has one stage.
- Preserve existing CUSTOMER quote acceptance. A newly issued quote must be
  accepted through the existing quote action API after Manager approval.
  This endpoint never records fictitious customer consent. Acceptance opens
  the standard PaymentRequest. Already accepted quotes reuse/open their
  existing deposit obligation immediately when the order is APPROVED.
- The custom Manager schedule can differ from the automatic 30/70 split;
  automatic ROAD quotes retain their existing 30/70 rules.
- The parent lock serializes concurrent requests. Exact retries with the same
  body and original version return the existing issuance. Changed input with
  stale version fails. A second issuance does not overwrite an existing one.
- A durable ORDER_DEPOSIT / DEPOSIT_REQUESTED AuditEvent records actor,
  owner, revision, quote, amounts, note, time and a request fingerprint. No
  new table or DDL is needed. This is not a separate financial ledger.

`GET /api/transport-orders/{id}` includes the same `depositRequested` property
(or null when no current active issuance). Existing ownership checks remain.
FE displays amount/note and links to quoteId for review/acceptance. Show the
payment action only when paymentAvailable=true and paymentRequestId is set.
Do not treat the Manager's 200 response or a transfer report as money received.
isPaid is derived from reconciled allocations minus completed refunds and
excludes reversed bank receipts. Historical revision requests are hidden.

## Errors

- 400: missing/invalid amount, scale, currency, note or version format.
- 403: caller lacks active Manager access.
- 404: unknown order or owner-scoped customer detail unavailable.
- 409 ORDER_VERSION_CONFLICT: reload order before a changed command.
- 409 DEPOSIT_ORDER_STATE: not SUBMITTED/APPROVED or no current revision.
- 409 DEPOSIT_AMOUNT_INVALID: deposit exceeds total.
- 409 DEPOSIT_QUOTE_MISMATCH: incompatible existing quotation; review it.
- 409 DEPOSIT_REQUEST_EXISTS: existing issuance must be reviewed, not replaced.
- 409 DEPOSIT_ORDER_PROTECTED: financial/travel commitment prevents new pricing.
- 503 PAYMENT_SCHEMA_PENDING: existing quotation/payment schema unavailable.

No FE changes are included in this task. Refresh cached order detail after
successful issuance and use the returned recordVersion for the next action.

## Verification (2026-10-27)

`mvnw -B test-compile checkstyle:check test` with Demo-only SQL test flags:
BUILD SUCCESS; 499 discovered, 330 executed, 169 skipped by existing suite
conditions; zero failures/errors and zero Checkstyle violations. Includes
11 BookingFlow SQL rollback tests and all API contract tests. Covers
Manager/other-role access, ownership, version conflicts, exact retries,
SUBMITTED gating, custom and 100% deposits, existing accepted obligations,
quote acceptance, reconciliation, allocation and reversal projections.

Final Demo JAR is running on 8082. OpenAPI includes the bearer-protected
Manager endpoint and customer depositRequested field; anonymous POST is 401.
No schema migration, real EquiTransit DDL, or FE source change was needed.
