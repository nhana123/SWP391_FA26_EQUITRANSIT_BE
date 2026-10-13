---
name: equitransit-be1-commercial
description: "Implement and review EquiTransit Spring backend flows for BE1: accounts, horses, booking revisions, service pricing, quotations, manual payments, staff administration and audit dashboards. Use for F01–F07 and F15–F16 in SWP391_FA26_EQUITRANSIT_BE; coordinate legal and operational dependencies with BE2."
---

# EquiTransit BE1 — Commercial and customer flows

Work as BE1 on the human-assigned Issue, preserving the existing Spring
layered monolith. BE1 is `nhana123`; BE2 remains unassigned. This skill
guides implementation; it does not assign the entire backlog to one AI.

## Establish the source of truth

1. Locate the checkout whose origin is
   `nhana123/SWP391_FA26_EQUITRANSIT_BE`. Do not assume the skill directory
   is the repository. A likely local checkout is
   `D:/FPT-ki7/SWP/Cross-Border Racehorse Transport System/SWP391_FA26_EQUITRANSIT_BE`.
2. Read applicable AGENTS.md, then these paths relative to the checkout:
   - `docs/team/PROJECT_CONTRACT.md`: reconciled business rules and Java
     conventions; read this before implementing a flow.
   - `docs/team/BACKLOG.md` and the assigned body in
     `docs/team/issues.json` or its current GitHub Issue.
   - `docs/team/github-issues.json`: actual Issue identifiers after publish.
   - `docs/database-schema.md`: physical SQL Server snapshot.
   - `README.md`, `CONTRIBUTING.md`, `pom.xml`,
     `config/checkstyle.xml`, relevant existing code and migrations.
3. If available, inspect newer database metadata read-only and the local
   `SWP-RE/booking-model.js` in the project parent. Source versions are
   recorded in `docs/team/source-versions.json`. Local plugin code does
   not prove the live Figma was inspected.
4. Latest human decisions override legacy docs. Explain material conflict
   or a missing business decision; continue independent work. Do not
   invent fields, APIs, production rates or completed integrations.

## Own these flows

- F01: review existing verified auth; do not regenerate baseline.
- F02/F03: horse ownership, single-horse draft, submission and revisions.
- F04: Manager decision and Specialist/Coordinator assignment.
- F05/F06: eligibility, charge disclosure, offers and quotation lifecycle.
- F07: manual bank reports, reconciliation, allocation and refunds.
- F15/F16: staff/tariff administration, audit, notifications and dashboards.

Table ownership is listed in BACKLOG. BE2 owns compliance, resources,
planning, reservations, execution and delivery. Agree typed facades for
ComplianceReadiness, ItinerarySummary and fit policy rather than querying
BE2 repositories to bypass rules. BE1 provides CurrentActor/access policy,
CommercialReadiness, QuarantineSelection, AuditWriter and NotificationWriter.

## Preserve the business invariants

- Individual customers, one horse per submitted order. Horse updates do
  not change snapshots. Submitted revisions are immutable and include
  services; latest submitted revision need not be the operating contract.
- Customer ownership comes from authenticated identity. StaffProfile
  StaffID is distinct from UserID. Check role AND ownership/assignment;
  enable/test method security before relying on PreAuthorize.
- One published road KM rate; stall slot × AIR km; coordination ROUTE km
  once. Veterinary/care/quarantine use their actual units. Published rate
  with unknown quantity is pending, never a fake zero payable amount.
- Show all charges reveals breakdown. Document processing, handling and
  customs appear in extras. Prevent bundled duplicates. Optional care and
  insurance are not silently selected; mortality + combined cover do not
  stack. Declared horse value is not the insurance premium.
- Oversized or missing horse measurements exclude 1-slot options. Check
  selections server-side using confirmed policy, not fixture thresholds.
  Reject invalid choices with reasons; do not silently upgrade the price.
- Specialist controls required quarantine days; distinguish not-required
  from unknown. Do not insert zero into RequiredDays CHECK > 0.
- Issue Quote only with valid priced items, terms and expiry. Customer
  accepts their quote; Manager cannot accept for them. Tariff changes do
  not mutate issued quote snapshots. Use existing QuoteStatus CHECK codes.
- PaymentReport is a claim, not receipt. Allocation/refund need reconciled
  money, matching currency, idempotency and concurrency control. Never
  allocate/refund beyond available funds or claim a bank transfer occurred
  without evidence. Return a typed financial gate to BE2.
- Accepted or running orders change through controlled revision/consent
  with BE2. Do not overwrite committed routes, prices or accepted plans.

## Implement and verify

Inspect existing mappings and error conventions first. Keep Java 21,
Spring Boot 3.5.16, Maven Wrapper and controller/service/repository layers.
Use DTOs, thin controllers and service transactions. Map real SQL names,
quote User, use BigDecimal and UTC with explicit display timezones.

Before persistence changes, compare constraints, FK, types and nullability.
Resolve the assigned schema gaps in PROJECT_CONTRACT with a minimal
reviewable migration, backfill and repair instructions. Do not use
Hibernate update or execute migrations on the real EquiTransit database
merely because this skill is loaded.

Follow the PDF conventions summarized in PROJECT_CONTRACT: 4 spaces,
80 columns, PascalCase types, camelCase methods/fields, constant names,
braces, declaration ordering, Javadoc and truthful project file comments.
Checkstyle passing does not substitute for semantic/manual review.

Run Maven Wrapper verify. Test wrong role/ownership, transitions, invalid
pricing, expired evidence and retries; test concurrent money/activation
changes when relevant. Use a separate SQL Server test database for changed
SQL persistence; never copy auth deleteAll cleanup onto real data.

Keep secrets external, avoid logging tokens/passwords, secure proof file
access, and omit supplier costs/margins from customer DTOs. Work on the
assigned feature branch, coordinate shared contracts and cross-review the
PR into dev. Follow the human's authorized Git scope; this skill does not
independently authorize messaging others, merging, deployment or payment.

Report implemented behavior, tests actually run, migration requirements
and remaining blockers. Do not call a flow complete when its producer
facade is only a mock returning READY.
