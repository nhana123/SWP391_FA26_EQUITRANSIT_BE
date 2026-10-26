# Booking flow: ROAD / AIR

## Fixed ROAD booking
- SUBMIT leaves the order SUBMITTED: AWAITING_MANAGER_APPROVAL, depositRequired=false, canAssignDriver=false; no quote or deposit request is generated.
- Manager APPROVED with an active Specialist and Coordinator creates an ACCEPTED fixed ROAD quote, 30% DEPOSIT / 70% BALANCE and a deposit PaymentRequest, only when all frozen route/service fees are known. Customer submission accepts the fixed terms conditionally on Manager approval; no Manager identity is fabricated for automatic preparation/issue.
- Unknown Hub transfer distances or requested extras needing assessment send the order to Coordinator pricing instead. No partial transport-only deposit is collected.
- Team allocation, approval, quote creation and deposit opening share one transaction. Invalid team/version or pricing failure rolls everything back; identical retries do not duplicate requests.

## AIR / MULTIMODAL and manual pricing
- Submit waits for Coordinator pricing, including actual AIRFARE evidence.
- Assigned Coordinator can ISSUE a publishable quote. Existing source/pricing checks still apply.
- Customer ACCEPT opens the deposit request transactionally. Replays and legacy manual creation return the existing request.
- Explicit route bookings require a two-row 30% DEPOSIT / 70% BALANCE schedule when issuing/accepting. Legacy requests without route metadata retain schedule compatibility.

## Assignment and departure
- Draft Plan/Graph design remains possible before payment.
- Road assignment requires fully paid DEPOSIT; otherwise HTTP 409 DEPOSIT_REQUIRED_BEFORE_ROAD_ASSIGNMENT.
- Stall assignment and flight reservation similarly return DEPOSIT_REQUIRED_BEFORE_AIR_ASSIGNMENT.
- Gates use current-revision accepted quote and net reconciled allocations, accounting for reversals/refunds. Pending refunds prevent new assignment.
- New route-aware bookings must pay BALANCE before departure; blocker BALANCE_REQUIRED_BEFORE_DEPARTURE.

## Read status
GET /api/transport-orders/{orderId}/booking-flow-status

Access: owning CUSTOMER, active MANAGER, assigned active COORDINATOR.

Response fields:
orderId, transportMode, orderStatus, hasAcceptedQuote, quoteTotal,
depositRequired, depositAmount, depositPaid, isDepositSatisfied,
canAssignDriver, blockReason.

Money values are decimal strings. Unpublished draft totals are hidden.
canAssignDriver describes the order/payment gate, not resource availability
or permission to invoke assignment APIs.

Blockers include AWAITING_MANAGER_APPROVAL, AWAITING_COORDINATOR_QUOTE, QUOTE_ACCEPTANCE_REQUIRED,
DEPOSIT_SCHEDULE_REQUIRED, DEPOSIT_REQUIRED, REFUND_PENDING,
ORDER_NOT_ASSIGNABLE.

No database migration or Frontend change is required for this feature.
## Operational sequence
SUBMIT -> SUBMITTED / AWAITING_MANAGER_APPROVAL -> Manager APPROVED + Specialist/Coordinator -> DEPOSIT_REQUIRED -> Customer pays 30% (reconciled allocation) -> isDepositSatisfied=true / canAssignDriver=true -> Coordinator assigns resources.

Existing approval endpoint: POST /api/manager/transport-orders/{id}/decisions (AssessmentRequest). No new assessment URL is introduced. AIR and pending-fee ROAD still await a publishable quote and Customer ACCEPT after approval. Old payment request/report mutation paths also reject SUBMITTED orders with HTTP 409 AWAITING_MANAGER_APPROVAL.
