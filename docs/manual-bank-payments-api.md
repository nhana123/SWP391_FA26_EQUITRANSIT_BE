# F07 — Manual bank payments

Requires reviewed migration007 after005 (006 is tariff management, not reused).
No automatic real-DB migration, bank transfer execution or production fixtures.
All HTTP responses are application/json; Bearer token required.

## Routes and actors

Base: `/api/transport-orders/{orderId}/payments`.

| Method/path | Actor | Contract |
|---|---|---|
| GET base?page=0&size=20 | Owning Customer / active Manager | Bounded safe overview |
| POST /requests | Manager | Clone one accepted QuotePaymentSchedule obligation |
| POST /proofs | Owner / Manager | Validate and store sanitized PNG/JPEG evidence |
| GET /proofs/{key} | Uploader Customer on owned order / Manager | Typed base64 image JSON |
| POST /reports | Owning Customer | Transfer claim, never bank money |
| POST /reports/{id}/reject | Manager | Reject claim with audited reason |
| POST /transactions | Manager | Verified received bank transfer, separate from reports |
| GET /transactions/{id} | Manager | Safe bank status for related receipt |
| POST /allocations | Manager | Allocate existing reconciled money to obligation |
| POST /refunds | Owner / Manager | Request refund against an allocation |
| POST /refunds/{id}/actions | Manager | APPROVE / REJECT / COMPLETE |
| POST /transactions/{id}/reversal | Manager | Evidence-backed FULL bank reversal |

Coordinator/Specialist/Driver have no money mutation/proof access in F07.
Manager is the reconciliation actor; no extra finance role is introduced.
Order ownership and live staff identity are checked inside the transaction.

Every write body has a nested command (except GETs):

```json
{
  "command": {
    "recordVersion": "000000000001A2B3",
    "operationId": "d86b4f63-5acc-4f20-8da3-e24323268814"
  },
  "quoteId": 301,
  "scheduleId": 401
}
```

recordVersion belongs to the PARENT TransportOrder, not a payment/quote row.
Business writes return PaymentMutationReceipt `{entityId,recordVersion}`;
refresh GET after success or replay (a receipt is historical). Same UUID, user,
order, exact body and target path retries return the original result. Reusing a
UUID with changed body/target returns409. No silent refresh-and-retry on conflict.
Proof upload returns PaymentProofReceipt; uploading does not change money or the
order version, but stores an immutable idempotent receipt. UUIDs are scoped to
order + authenticated UserID; both proof and money share the receipt namespace.

## Accounting rules

PaymentRequest is copied from a schedule of the current accepted quote: QuoteID,
ScheduleID, stage, AmountDue and USD. Clients cannot send an amount or currency
for creation. One request per schedule. An accepted quote remains a commitment
after its former offer validity ends; an expired/unaccepted offer cannot fund a
request. No price estimate or customer report creates a PaymentRequest.

BankReportCommand contains paymentRequestId, reportedAmount, reportedCurrency,
transferredAt, bankReference and proofFileKey. It records SUBMITTED only. Rejection
marks REJECTED and audits reason. Neither state changes paid totals or gate flags.

BankReceiptCommand contains amountReceived, currency, recipientAccount,
bankTransactionReference, postedAt, verificationSource, proofFileKey and optional
internalNotes. Amount/currency/evidence are independently verified by Manager;
no automatic link from a report turns it into money. Unique account+bank reference
prevents duplicates even with another UUID/order. No bank account fixture is
published as the actual company receiving account. Reconciliation records actual
bank evidence; it does not expose bank notes/accounts to Customers.

AllocationCommand contains transactionId, paymentRequestId and amount. Requires
RECONCILED money and matching USD; aggregate allocations cannot exceed received
amount; net payments cannot exceed the obligation. Completed refunds reduce net
paid, enabling replacement payment from NEW funds. Already refunded money never
returns to the old bank transaction's allocatable balance. Gross allocations
remain history. Cross-order use of a bank receipt is serialized by the ledger
lock; no transaction balance is cached in a client or per-order aggregate.

Money is a positive plain decimal string, <=2 fractional digits, fitting
DECIMAL(18,2). Normalization uses explicit HALF_UP, never floating point. Reports
and future transfer timestamps are rejected for invalid formats/currency/time.

RefundRequestCommand: allocationId, amount, reason. Status REQUESTED reserves
refundable balance. APPROVE stores trusted Manager StaffID/time; REJECT releases
the reservation. COMPLETE requires a prior APPROVED request, unique real
bankRefundReference, protected proofFileKey and refundedAt between approval and
now. RequestedBy is UserID; ApprovedBy is StaffID. No same-call approval and bank
completion, no pretend automatic transfer. Requests+approved+completed amounts
cannot exceed the allocation. Both owner and Manager can request; only Manager
approves/completes. Refunds do not cancel accepted contractual obligations; that
requires the separately controlled F13 process. Paid totals decrease on COMPLETE.

A FULL reversal requires real proof and reason, records Manager/time and excludes
its allocations from paid totals. It is blocked if any requested/approved/completed
refund exists for that transaction (reject pending refund first; completed refund
requires separately reviewed accounting resolution). Partial bank reversal and
retroactive ledger editing are outside this API; never fake them via reports.

## Protected proof contract

POST /proofs uses command, mediaType (`image/png` or `image/jpeg`), contentBase64.
Decoded size1..5MiB, <=20million pixels. Verify actual decoder format, bounded
image dimensions, then re-encode pixels without input metadata/trailing payloads.
PDF/SVG/HTML/other MIME are rejected in this MVP. FileKey is a generated UUID,
never a client filesystem path or external URL. SQL storage has independent size/
MIME checks. GET returns typed base64 JSON, no executable/content-disposition URL.
Customers only access their own uploads in their own orders; Manager can review
order evidence. Reconciliation/refund verifies key scope/existence. A syntactically
valid invented UUID is insufficient. Only metadata is fetched to validate proof;
bytes are loaded only for an explicit content read. No public proof links or
customer access to Manager evidence, costs, internal notes or account identifiers.

Deployment must protect SQL backups/connection and TLS. Do not log body images,
JWTs or internal notes. Keep FE caches account-scoped. There is no proof deletion
endpoint that could erase financial evidence. Image-only limits are an explicit
supported-format restriction; this is not a general document upload service.

## Financial facade for BE2

Inside an authorized consumer transaction, call
`QuotationCommitmentFacade.readiness(order, revision, acceptedQuote, asOf)`.
Propagation MANDATORY; retains order -> quote -> ledger locks until caller commit.
`requireAccepted` remains compatible for quotation identity and also returns this
financial evidence; F10 must not infer a financial PASS merely from total/acceptance.

CommercialReadiness exposes quoteId, revisionId, total, net paidAmount, asOf,
all FinancialObligation schedule rows and SEPARATE departure/settlement blockers.
Schedules without PaymentRequest still count. The facade reads reconciled bank
allocations at asOf less completed refunds/reversal, not PaymentReport or quote
amount as receipts. Refund REQUESTED/APPROVED blocks both gates conservatively.

Owner decision17/10/2026:
- F11: net paid>0, all DueAt<=asOf schedules paid; a single-stage quote requires
  the whole stage even when future; any DueAt=null blocks. Future installments
  in a multi-stage quote may remain unpaid. Financial readiness alone is not
  legal/resource/welfare/departure authorization.
- F14: every accepted schedule fully net-paid (100percent), no pending refund
  or unresolved schedule. Operational completion remains separate.

No obligation waiver, writeoff or fabricated adjustment is implemented. Consumers
must evaluate again under their write transaction, not trust a cached UI gate.

## Concurrency, errors and verification

All financial paths lock the parent with UPDLOCK,HOLDLOCK, lock the accepted quote,
then take a transaction-owned global ledger application lock (5second wait), then
lock bank rows with UPDLOCK,HOLDLOCK. This deliberately serializes the MVP ledger
across orders, preventing shared-bank overspend and reversal/gate races. No reverse
lock acquisition from bank to another order. Transactions15seconds, SQL10seconds.
No external bank or network call occurs inside the money transaction.

Overview page/size applies independently to each of4 collections, ordered by ID;
size1..100. Fetch subsequent pages deliberately; no unbounded history/N+1 queries.
The overview does not include bank accounts/internal notes. Customer allocation
IDs are needed for refund requests; transaction details remain Manager-only.

400 invalid JSON/constraints;401 unauthenticated;403 wrong role;404 unavailable
order/read record;409 changed versions, missing evidence, invalid state, financial
bounds/currency/retry conflict;503 PAYMENT_SCHEMA_PENDING before migration007.
Data integrity failures roll back; retry only the same uncertain operation.
Do not automatically convert a failed reconciliation into a completed report.

Tests: native SQL suite is opt-in EQ_PAYMENT_TEST_ENABLED=true,
EQ_PAYMENT_TEST_DB_URL must be exactly the isolated test catalog; guard runs before
first fixture write. Fixtures and receipts/proofs/audits are removed by owned IDs.
Policy unit tests cover distinct F11/F14 rules; ApiDtoContractTest covers new schema
examples, exact decimal types, command refs, enums, privacy and JSON media types.
