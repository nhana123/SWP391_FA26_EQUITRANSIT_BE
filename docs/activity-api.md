# F16 — Scoped dashboards, notifications and audit

Branch feature/16-dashboards-notifications-audit, based on dev after F07/PR32.
Migration008 follows007 and is explicitly deployed, never automatic on EquiTransit.
No external event bus, BI engine, FE UI or fabricated operational milestones.

## APIs (JSON/Bearer)

| Method/path | Scope |
|---|---|
| GET /api/dashboard?page=0&size=20 | Own customer orders / active assigned staff / Manager |
| GET /api/dashboard/tasks?page=0&size=20 | Active case assignments or Driver legs; other roles get empty tasks |
| GET /api/activity/orders/{orderId}/timeline?page=0&size=20 | Same live order scope |
| GET /api/notifications?page=0&size=20 | Current recipient only, with current order access |
| PUT /api/notifications/{id}/read | Recipient only; repeated PUT preserves first ReadAt |
| GET /api/customers/me/notification-preferences | Customer ordinary-mail opt-in |
| PUT same path | {"enabled":true/false}, idempotent; null invalid |
| GET /api/manager/audit-events?page=0&size=20 | Manager only; redacted legacy/new snapshots |
| GET /api/manager/reports/financial | Manager only; exact USD aggregates |

Page>=0,size1..100; stable ID ordering. Lists include totalElements except the
observed timeline. Dashboard status counts are grouped over the authorized scope,
not only the current page. Customer/staff DTOs contain no supplier price, actual
cost, margin, bank account, medical files or internal note. Raw entity maps never
cross the HTTP boundary. Unknown/missing record404; wrong role403; undeployed008
returns503 ACTIVITY_SCHEMA_PENDING. There is no client-supplied recipient UserID.

SPECIALIST/COORDINATOR need live matching OrderAssignment with EndedAt=null.
DRIVER needs an ACTIVE DRIVER TripAssignment in the current plan with assignment
window covering now. Tasks identify the exact leg; case tasks use legId=null.
Revoked/ended assignments are checked again on every list/read/mark-read. These
read scopes do not create reservations or attest resource availability.

## Timeline facts and future BE2 producers

SUBMITTED comes from actual OrderRevision.SubmittedAt; ACCEPTED from actual
Quote.AcceptedAt. PAID means current accepted obligations are fully net allocated
from reconciled USD bank transactions less completed refunds, not PaymentReport.
It is not a cached departure/settlement gate. A pending refund can block F11/F14
even while the current paid fact remains visible; F07 readiness remains authority.

CLEARED/DEPARTED/DELIVERED/CLOSED are emitted only from trusted canonical audit
facts after the responsible BE2 module has committed its validated transition.
They are not guessed from accepted quotes, company price selection, planned dates,
intermediate airport handover, or order status text alone. Until those producers
exist, the timeline omits the unrecorded milestones. This is an observed activity
projection, not immutable payment balance history; a completed refund/reversal
can remove the current PAID fact. No raw audit snapshot is exposed to customers.

## Shared internal facades

Within an authorized producer transaction, retain the parent lock and call:

    auditWriter.append(orderId, ActivityCode.CLEARED, operationId, metadata);

The producer must enforce its role/assignment/legal/operational prerequisites.
This is an internal Java facade, not a public endpoint for recording arbitrary
facts. UserID is resolved from authenticated identity, not client StaffID.
AuditWriter requires an existing transaction (MANDATORY), appends a JSON object
envelope and never updates/deletes history. Stable order/actor/code/UUID retries
reuse the original event. Unknown/free text fields (even nested medical notes,
passwords, raw tokens, supplier costs) are redacted wholesale; only bounded
allowlisted identifiers/status metadata survives. Legacy audit receipts are not
rewritten because F04/F06 rely on them for idempotency, but audit API output is
redacted again. This is application append-only, not a claim of DBA-tamperproof
storage: deployment should grant the runtime principal INSERT/SELECT only on
AuditEvent; controlled test/DBA retention remains separate. No edit/delete API.

F03 submission now uses the facade when008 is deployed, without breaking F03 on
older deployments. F04/F05/F06/F07 existing committed audit records are also durable
notification sources; their receipt format remains compatible. F08–F14 producers
use the same facade after their actual validated transitions. AuditEvent retains
metadata only, not the business object or proof storage replacing domain tables.

## Durable notification projection and ordinary mail

AuditEvent is the durable source. NotificationWriter.dispatch() processes at most
100 eligible committed events in an independent REQUIRES_NEW transaction. It
resolves current owner/active case/driver recipients with one set-based INSERT,
then records a projection marker atomically. Unique SourceEventID+UserID prevents
duplicates on retry or two workers. Failed projection retains unprocessed audit
for the next poll. No network call/secondary failure rolls back a domain commit.

Messages are generic safe templates, never raw internal audit/action/medical text.
Only the current recipient may read/mark read; order scope is checked again.
NotificationWriter is the reusable internal projection facade; no public broadcast
endpoint. A small in-process scheduler polls by default every5seconds; tests set
app.notifications.worker-enabled=false and invoke the worker explicitly.

Ordinary email requires Customer.EmailNotificationsEnabled=true; null legacy flag
means no ordinary email. Recheck active account/ownership/opt-in immediately before
sending. Staff without this customer preference get in-app notifications only.
Security verification/recovery uses the unchanged account-mail listener and is
not suppressed by this ordinary preference.

The existing file/SMTP AccountMailSender adapter is reused; tests mock delivery or
use the ignored file outbox, never real mail/SMS. Claim at most20 exclusive mail
leases, send outside SQL transaction, persist outcome separately. Failed leases
retry after5minutes, up to10 claims; in-app notification remains intact regardless.
SMTP is at-least-once: a crash after SMTP acceptance and before marking SENT can
repeat an email. Exactly-once SMTP is not claimed; in-app dedup is transactional.
Backups/runtime secrets/mail settings remain deployment configuration, not code.

## Manager financial report semantics

- quotedValue: one latest ISSUED/ACCEPTED current-revision quote per order; known
  published retail totals, never sum every historical version or pretend DRAFT
  unknowns are zero-priced quotes.
- reportedTransfers: unverified SUBMITTED PaymentReport claims, not money.
- netBankReceipts: reconciled USD bank receipts less completed refunds; reversed
  bank receipts excluded. Includes money not allocated yet; counted once per bank
  transaction, not once per allocation/report.
- netAllocated: actual reconciled allocations less their completed refunds;
  reversed transactions excluded. Does not equate to unallocated bank cash.
- actualCost: Manager-only recorded USD ActualCost amount, not a customer surcharge.
- operationallyCompletedOrders: persisted DELIVERED/COMPLETED/CLOSED states.
- closedOrders: explicitly persisted CLOSED state; not inferred from a payment
  report or delivery alone. Financial correctness of a closure is enforced by F14
  consuming F07 settlement readiness; this read report does not authorize closure.

Aggregates are independent scalar subqueries in one SQL statement, avoiding join
multiplication. They are observational reports, not a financial gate/cache or
revenue-recognition policy. All money is decimal string with explicit HALF_UP2.
No currency conversion, margin analytics or new accounting business rules.

## Migration/test operations

008 adds nullable audit dedup key, notification source/mail state, unique recipient
source index, recipient pagination index and projection marker. Legacy rows are
preserved. On first deployment existing audits receive projection markers, so old
history does not generate a burst of new notifications. Apply/reapply only in an explicitly authorized environment; no history
backfill, guessed notifications, automatic production migrations or destructive
rollback. Forward repair after usage; preserve audits and notification history.

Native integration opt-in: EQ_ACTIVITY_TEST_ENABLED=true and
EQ_ACTIVITY_TEST_DB_URL points exactly to isolated
EquiTransit_FigmaMigrationTest_20261013_143729. Guard precedes fixture writes,
cleanup removes only fixture users/orders/notifications/projection markers.
Unit tests cover privacy and failed secondary delivery; OpenAPI contract tests
cover field examples, typed schemas, enums, media types and pagination.
