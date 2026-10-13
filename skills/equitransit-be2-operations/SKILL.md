---
name: equitransit-be2-operations
description: "Implement and review EquiTransit Spring backend flows for BE2: legal evidence, compliance phases, resource fit, ROAD/AIR plans, reservations, departure gates, tracking, welfare, incidents and final handovers. Use for F08–F14 in SWP391_FA26_EQUITRANSIT_BE; coordinate booking and finance dependencies with BE1."
---

# EquiTransit BE2 — Legal and operational flows

Work as BE2 on the human-assigned Issue. BE2 has no GitHub assignee yet;
do not assign another collaborator based on their presence in the repo.
This skill does not request implementation of every flow at once.

## Establish the source of truth

1. Locate the checkout whose origin is
   `nhana123/SWP391_FA26_EQUITRANSIT_BE`, not the installed skill directory.
   A likely local checkout is
   `D:/FPT-ki7/SWP/Cross-Border Racehorse Transport System/SWP391_FA26_EQUITRANSIT_BE`.
2. Read applicable AGENTS.md and these checkout-relative files:
   - `docs/team/PROJECT_CONTRACT.md`: shared business/Java contract.
   - `docs/team/BACKLOG.md`, assigned entry in `docs/team/issues.json`
     or the current GitHub Issue, and `docs/team/github-issues.json`.
   - `docs/database-schema.md`, `README.md`, `CONTRIBUTING.md`,
     `pom.xml`, `config/checkstyle.xml` and relevant mappings/migrations.
3. Read the relevant parent-project plugin model/catalogue and source
   documents when available. `docs/team/source-versions.json` identifies
   local versions reviewed, not a live Figma inspection. Read-only live
   schema inspection can establish newer physical names/constraints.
4. New human decisions override old road-only/multi-horse/Express/MongoDB
   scope. Use current international single-horse Spring/SQL scope. Explain
   conflicts and missing decisions without inventing legal requirements.

## Own these flows

- F08: rule/checklist, document versions, review, procedures and clearance.
- F09: vehicle/stall catalogues, availability and fit policy.
- F10: multimodal plans, legs, assignments and reservations/policies.
- F11/F12: three departure gates, physical receipt, execution and welfare.
- F13/F14: incidents, controlled adjustments, handover and actual costs.

BACKLOG lists each table's owner. BE1 owns booking, revisions, choices,
offers, quotes, payments and account/admin foundations. Consume their
CurrentActor/access policy, CommercialReadiness, QuarantineSelection and
audit/notification facades. Provide ComplianceReadiness, ItinerarySummary
and fit policy contracts. Do not bypass BE1 services by directly reading
their repositories to infer approval or funds.

## Preserve the business invariants

- Legal rules have route, phase, version, source and effective dates.
  Missing rule means pending assessment, not exemption. Specialist reviews
  the exact submitted document version; replacements retain history.
- Pre-export clearance is distinct from arrival/quarantine release.
  Expired/revoked evidence fails the relevant gate. Manager cannot override
  legal compliance; a click cannot manufacture an authority approval.
- Draft planning may run alongside document review. Actual departure needs
  Legal, Financial and Operational gates, checked again transactionally
  when departing. Do not mock READY in the real path.
- One submitted order has one horse. A plan points to its revision and
  accepted quote; latest revision is not automatically the running one.
  Keep one active operational plan with explicit version selection.
- ROAD uses FleetVehicle and assigned Driver. AIR uses carrier reservation
  and stall equipment, not a FleetVehicle pretending to be an aircraft.
  Slot preference is not proof of physical fit or carrier acceptance.
- Resource overlap uses startA < endB and startB < endA with buffers and
  capacity checks. Protect against concurrent assignment/activation,
  including the same horse in overlapping active orders. Do not introduce
  automatic booking pooling or invent shared inventory.
- Source/expiry/status of flight, quarantine and insurer confirmations
  matter. Offer or HELD is not CONFIRMED. Specialist sets quarantine days,
  customer selects a package; optional insurance choice is not cover.
- Driver receives the correct microchip/condition and only updates assigned
  ROAD legs. Coordinator records AIR milestones with an external source.
  Customer sees their timeline; do not pretend continuous airborne GPS.
- Welfare emergencies permit prompt safety action and evidence recording;
  they do not auto-approve extra charges or waive legal requirements.
  Commercial changes require BE1 revision/quote/consent workflow before
  overwriting accepted obligations. An acknowledgement is not price consent.
- Airport/quarantine handover is intermediate. Final delivery needs correct
  receiver, evidence and applicable release. Silence is not customer
  confirmation. Operational completion and financial closure are separate.
- ActualCost is company expenditure, not an automatically payable customer
  surcharge. If LegID exists it belongs to PlanID; customer DTOs hide costs.

## Implement and verify

Keep Java 21/Spring Boot 3.5.16/Maven Wrapper and the existing layered
monolith. Controllers use DTOs, services own transitions/transactions,
repositories implement persistence. Use BigDecimal, explicit units, UTC
and route timezones. StaffID is not UserID; enforce role plus assignment
and customer ownership, with producer/consumer contract tests.

Before adding endpoints, define status transitions, actor, blockers and
phase/asOf/revision keys. Check actual SQL FK/CHECK/nullability. Address
schema gaps using minimal migrations with backfill/repair instructions,
including AcceptedQuoteID, fit limits, reservation evidence or consent
when required. Do not invent nonexistent tables or run schema updates on
the real database merely because this skill is loaded.

Follow Oracle/Sun conventions summarized in PROJECT_CONTRACT: 4 spaces,
80 columns, naming, braces, ordering, Javadoc and truthful project file
comments. Review semantics in addition to Checkstyle.

Run Maven Wrapper verify and relevant SQL Server integration tests in a
separate test database. Cover wrong actor/assignment, stale revision,
expired/revoked evidence, wrong horse, resource conflicts, retries and
concurrent gate/assignment changes. Do not copy deleteAll cleanup to real
data or substitute H2 alone for SQL-specific migration verification.

Use secure file adapters with opaque keys and ownership/MIME/size/path
checks; keep credentials and sensitive medical records out of logs/repo.
Coordinate shared interfaces before dependency PRs, work on the assigned
feature branch and cross-review into dev. The human's authorized Git
scope governs external actions; this skill does not independently grant
authority to send messages, deploy, merge or call external services.

Report actual behavior, validation, migrations and remaining blockers.
Distinguish code implementation from authority/carrier/insurer results
that must be recorded from genuine external evidence.
