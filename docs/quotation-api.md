# Versioned quotations (F06)

Java 21 / Spring layered monolith. All endpoints produce JSON and require a
Bearer token. Database migration `005_versioned_quotations.sql` is required
after 001–004. Without that deployment, quotation endpoints return 503 with
`QUOTE_SCHEMA_PENDING`; existing flows keep their schema/behavior.

## Actors and endpoints

Base path: `/api/transport-orders/{orderId}`.

| Method/path | Actor | Result |
|---|---|---|
| GET `/quotes?page=0&size=20` | Owning Customer; Manager; assigned Coordinator | Bounded quote summaries |
| GET `/quotes/{quoteId}` | Same scope | Captured retail breakdown, terms and payment schedule |
| POST `/supplier-offers` | Manager or assigned Coordinator | Immutable evidenced offer ID and new parent version |
| POST `/quotes` | Manager or assigned Coordinator | New DRAFT quotation version, with priced items and pending blockers |
| POST `/quotes/{quoteId}/actions` | Manager or owning Customer, per action | Immutable operation receipt |

Customers cannot see unissued or superseded preparations. Staff access is
checked against active database roles and the current case assignment, never
against a client-supplied staff ID. Specialist/Driver cannot prepare quotations.
Internal supplier purchase prices are accepted only at the staff-only offer
endpoint and are never returned by quotation/customer DTOs.

## State and version contract

- Manager: DRAFT → APPROVED → ISSUED. Manager may reject DRAFT/APPROVED.
- Owning Customer: ISSUED → ACCEPTED or REJECTED, before quote expiry and only
  for the order's current approved revision. Manager cannot accept for them.
- Manager may explicitly mark elapsed DRAFT/APPROVED/ISSUED quotes EXPIRED.
  Acceptance is blocked immediately at expiry, even before this state update.
- A replacement POST creates a new version and supersedes prior unaccepted
  DRAFT/APPROVED/ISSUED versions. There is no in-place quote/item update API.
- Accepted quotes prevent direct order revision and new preparation. Controlled
  post-commitment changes remain F13; this endpoint does not bypass consent.
- A quote version is allocated under the order lock across the order's revision
  history; quote numbers follow `Q-{orderId}-{version}`.

Every mutation contains a nested command:

```json
{
  "command": {
    "recordVersion": "000000000001A2B3",
    "operationId": "d86b4f63-5acc-4f20-8da3-e24323268814"
  },
  "action": "APPROVE"
}
```

Use the latest parent `recordVersion` from the order or quotation response.
Use a new UUID for a new action; preserve the same UUID/version/body for an
uncertain retry. An identical successful retry returns the original receipt,
which is historical, not necessarily the latest order version. GET current
state after success/replay. Changed input with an old UUID returns 409
`QUOTE_RETRY_CONFLICT`; stale versions return `ORDER_VERSION_CONFLICT`.
All mutation paths lock the parent order before quote/source writes. They use
the same parent lock as F03/F04, so acceptance and a direct revision cannot
both win with one observed version.

## Preparation and price provenance

`QuotePrepareRequest` includes command, revisionId, validUntil, termsVersion,
terms, cancellationTerms, a bounded paymentSchedule, and assessed commercial
source gaps. See Swagger's complete request examples for the exact JSON shape.
Examples use fake IDs/prices only; replace them with actual authorized sources.

F05 supplies the catalogue, selection semantics and exact formulas. Quotation
pricing consumes sourced draft itinerary distances without claiming that the
plan, flight, vehicle or departure is confirmed. Distances still need current
assigned Coordinator proof. Veterinary counts and quarantine applicability
still come from revision-bound Specialist assessments. Fit still requires
approved measurement policies and captured horse measurements for air routes.

F06 permits evidenced assessments only for AIRFARE, AIRPORT_HANDLING,
TERMINAL_HANDLING, CUSTOMS_DECLARATION, GOVERNMENT_DUTY, INSURANCE and ESCORT:

- PRICED requires positive quantity, exactly one tariffId/offerId, evidence
  reference, reason and validity covering the quote. The selected source must
  match revision/service/unit/currency and remain valid. Offers must contain
  separately recorded retail pricing and evidence; purchase cost is not retail.
- NOT_REQUIRED requires an explicit reason/reference and validity, with no
  quantity or price source. It is commercial evidence only, not legal clearance.
- Existing INCLUDED/NOT_SELECTED/NOT_REQUIRED/EXTERNAL_PROVIDER/EVENT_ONLY
  lines cannot be overwritten by a charged assessment. Airfare inclusions remove
  duplicate stall/handling charges; included lines stay visible without creating
  zero-priced QuoteItems. No automatic markup or supplier reservation occurs.

Missing required sources remain PENDING, and their amount/quantity stay null.
No fake zero item is inserted. A DRAFT with blockers has null total and cannot
be approved/issued. Resolve producer data/evidence, then prepare a new version.
Approval and issue recheck current evidence, sources, fit, captured amounts and
schedule equality. Source/rate changes before issue require a new preparation.
Issued snapshots remain unchanged when company tariffs subsequently change.

API quantities are exact decimal strings with up to three fractional digits;
money and retail rates use two. Each line is quantity × retail rate, rounded
once with HALF_UP to two decimals; total is the sum of rounded line amounts.
Unit rates also respect the existing SQL DECIMAL(18,4) storage limit (14 integer
digits); invalid ranges return a business error before an insert can overflow.
Totals exceeding DECIMAL(18,2) remain blocked drafts. Calculation snapshots
retain tariff versions, sourced kilometres/clinical counts, quarantine and fit
policy references, inclusions and the rounding formula for later review.
Payment stages are consecutive positive sequences, positive amounts with future
UTC due dates, and must sum exactly to the total before approve/issue. Offset
timestamps normalize to UTC, matching SQL datetime2(0) storage.

## Commitment is separate from money and operations

ACCEPT records AcceptedAt and AcceptedByCustomerID. It does not create bank
transactions, PaymentRequests, receipts, resource reservations or departure
authorization. QuotePaymentSchedule is captured once at preparation; retries
and acceptance cannot duplicate it. F07 owns actual payment reconciliation.

F07/F10 can consume `QuotationCommitmentFacade.requireAccepted` inside their
authorized write transaction. It retains the parent lock and checks the exact
order/revision/accepted quote. `CommercialReadiness` contains accepted retail
price evidence only, not financial/legal/operational gate PASS. Existing SQL
FK/trigger lineage checks remain enabled for TransportPlan.AcceptedQuoteID.

## Errors and frontend behavior

- 400: invalid decimal/version/UUID or malformed/unknown input fields.
- 401/403/404: authentication, role/assignment or ownership/visibility failure.
- 409: stale revision/version, expired quote/source, invalid schedule, pending
  price, changed evidence, invalid transition or protected commitment.
- 503 `QUOTE_SCHEMA_PENDING`: deploy reviewed migration 005 before F06 usage.

Preserve form input after conflicts. Do not silently refresh the version/UUID
and retry a write. A null total is unknown, not zero. Only the issued quotation
is an offer for acceptance; a draft estimate is not payable.

## Migration and verification

005 adds nullable Quote.PricingSnapshotJSON and supplier retail/recorder fields,
allows draft expiry without fabricated IssuedAt, and adds accepted-quote and
schedule uniqueness plus an audit lookup index. Legacy rows are not backfilled
with guessed prices or approvals. Null/unsupported snapshots require a new,
reviewed preparation. Duplicate accepted quotes/schedules stop migration;
repair them manually with their audit evidence rather than deleting history.

Deploy in a reviewed maintenance transaction after a verified backup. Reapply
is supported. Forward repair is preferred once F06 data exists: do not drop
columns or restore a pre-feature database over live customer data. This feature
does not automatically migrate the application's real database.

Native tests are opt-in with `EQ_QUOTE_TEST_ENABLED=true` and
`EQ_QUOTE_TEST_DB_URL` pointing exactly to
`EquiTransit_FigmaMigrationTest_20261013_143729`. They assert the catalog before
fixture writes and remove only their own fixture IDs, including race writes.
Run the existing HORSE/ORDER/ASSESSMENT/SERVICES native suites for regression.
Maven clean verify also validates OpenAPI examples and Java Checkstyle.

Quotation transactions have a 15-second timeout and the quotation/source SQL
adapters use a 10-second statement timeout. These are finite failure limits,
not a measured latency/capacity SLA. Source-producer queries remain in the same
transaction and inherit its timeout; no external HTTP/mail call holds its locks.

## F05 charge disclosure alignment

GET /api/transport-orders/{orderId}/services reads the latest usable F06
commercial snapshot for the exact current revision. The captured quantity,
retail unit rate, amount and assessed NOT_REQUIRED/included states are shared
with Show all charges. PRICED maps to F05 ESTIMATED; DTO fields are unchanged.
SupplierOffer.RetailUnitPrice is captured by F06, never inferred from purchase
cost. An unassessed offer alone does not select a charge or complete an estimate.

DRAFT/APPROVED/ISSUED snapshots must be within their quote validity. ACCEPTED
snapshots retain the committed retail breakdown after that validity date;
changing a tariff/offer never rewrites these captured prices. Rejected, expired,
superseded or other-revision quotes are excluded. Without a usable snapshot,
the existing source-based estimate/pending calculation applies. Snapshot
blockers still prevent totalEstimate from becoming payable or falsely complete.
No supplier cost, offer proof/reference or internal assessment reason is exposed.
This read-only disclosure is not quote acceptance or payment authorization.

F03 revision and F05 submitted service confirmation both invalidate all
DRAFT/APPROVED/ISSUED quotes through the existing shared commitment policy in
the same parent-locked transaction. Rollback restores the quote/revision pair;
UUID replay does not create another revision. Accepted commitments still block
direct rewrites. No new SQL migration is required for this audit patch.

Monetary scale reductions explicitly use HALF_UP. Quantity remains exact to
three decimals; SQL precision checks do not round invalid input into acceptance.
Supplier offer RecordedAt is truncated to UTC seconds before persistence,
avoiding SQL datetime2(0) rounding a newly created source into the future.
