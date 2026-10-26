# Creative Journey Studio sandbox review

Migration 018 and the Studio are experimental. The checked runtime uses
`EquiTransit_Demo`; deployment to the production `EquiTransit` database
has not been authorized. Do not use the normal production/local launcher
or restore the old Demo baseline during this review.

## Additive schema

`PlanCheckpointNode` stores the six checkpoint kinds, ordered keys scoped
to a plan, location/country, planned UTC instants, dwell, transit treatment,
internal care notes, an optional quarantine reservation, coordinates and
typed metadata JSON. `TripLeg.FromNodeKey`/`ToNodeKey` link each real leg
to two nodes in its own plan. Both keys remain nullable for legacy legs.
`TransportPlan.GraphLayoutJson` stores the bounded viewport transform.
Existing migrations 001–017 are unchanged. Migration 018 rejects catalogs
outside its explicit test/demo allowlist.

`tools/test-graph-migration-sandbox.ps1` checks two applications, rollback
CRUD and sixteen rejection cases, trusted constraints and unchanged
existing trigger definitions. Its ignored proof file is
`.local/journey-studio/graph-migration-proof.json`. The latest recorded
proof targets Demo: nine triggers unchanged, 300 trusted FK/CHECK
constraints, fixture row counts unchanged. This does not authorize promotion.

## Files and contracts

New BE files: `JourneyGraphModels`, `JourneyGraphRepository`,
`JourneyGraphPolicy`, `JourneyGraphService`, their two unit test suites,
migration 018, the sandbox PowerShell/SQL scripts, and the Studio API/demo
documents. Integration changes are in `SecurityConfig`,
`OperationalPlanController`, `OperationalPlanService`, `OperationalPlanPolicy`,
`PlanModels`, `OperationalPlanIntegrationTest`, `OperationalPlanPolicyTest`,
`ApiDtoContractTest`, and `OpenApiTest`. COMPLETED assignments can be read
from F12 history but cannot be submitted as a new F10 stall assignment.

The FE adds `src/features/plans/studio/` (model, graph API, canvas,
inspector, resource controls, styles and tests). PlansPage/PlanDetailPage,
DeparturePage and ExecutionPage embed the same Studio with role-specific
controls. Assignment contracts also recognize the existing COMPLETED state.

See [API contract](journey-studio-api.md) for commands, idempotency,
rowversion guards, role projections and layout-only PUT behavior.

## Demo walkthrough

1. Start the packaged BE using `tools/run-demo-local.ps1` and the FE using
   `npm run dev -- --port 5173`. Use credentials from the owner's ignored
   local Demo playbook, not a committed credentials file.
2. Log in as Coordinator, open My Cases, select an approved case, then
   Operational Plans. The Studio appears above the classic draft form.
3. Add Origin and Destination, or load the international template. The
   template provides structure only; enter real location/country, +07
   times and distance for each wire. Connect output to input by drag or
   two clicks. Click a node or capsule to open its inspector.
4. Increase rest/transfer duration and check downstream times. Zoom is
   limited to 50–150%; Fit and Auto-align work on the local canvas.
5. Compile and save. Reload the resulting plan and verify the same nodes
   and leg IDs. Saved drafts permit moving nodes and saving the layout;
   semantic pins are locked. Use a new draft for route/schedule changes.
6. Select a saved ROAD wire to check vehicle/driver availability and
   assign resources. Select AIR to check stalls, shared candidates and
   actual flight reservation evidence. Quarantine/insurance metadata is
   planning information; record real reservations and evidence using the
   existing forms before attempting confirmation.
7. Confirm only after the existing gates permit it. Confirmation is
   separate from departure authorization and physical horse receipt.
8. An assigned Driver sees their ROAD actions; an owning Customer sees
   the current published graph and shared welfare logs. The latest
   recorded checkpoint uses actual execution timestamps. Planned times
   never imply current presence or continuous GPS tracking.

On 22 October 2026, the dedicated DEMO/SIMULATION order10/plan11 passed
the browser chain: three-node ROAD/AIR compile/reload, resource assignments,
flight/stall/quarantine/insurance, confirmation, three gates and authorization,
Driver receipt/departure/milestones/welfare/ROAD completion, Coordinator AIR
start/completion and Customer tracking. Accepted quote12's persisted
installment identity was used by Manager UI to create a payment request;
simulation receipt/allocation and compliance prerequisites used existing APIs.
Actual times, shared/unshared visibility and private graph-field redaction
were checked. Internal-plan access by Customer returned403. The final phases
had zero page errors or off-order writes; recovered local runner failures
remain in attempt history without duplicate facts.

Evidence is ignored locally: FE `artifacts/studio-e2e-browser-proof.json`,
`studio-e2e-payment-request-proof.json`, screenshots and BE
`.local/journey-studio/studio-e2e-manifest.json`. This verifies the stated
simulation chain, not real carrier/bank/authority confirmations, two-order
stall pooling, final quarantine release/delivery or financial closure.
The added Demo compliance rule is route-wide: other VN-SG checklists may
need re-evaluation. No production writes, DDL or restore were used.

Manager supplier pickers now use the existing catalogue; Coordinator keeps
the documented ID fallback because catalogue access is Manager-only.
Actual reservations retain the existing forms and server validation.

## Verification commands

```powershell
# BE: Java 21
.\mvnw.cmd -B test-compile checkstyle:check
.\mvnw.cmd -B test
# SQL test is opt-in: set EQ_PLAN_TEST_ENABLED and the exact guarded test
# catalog URL before selecting the graph integration method.
.\mvnw.cmd -B '-Dtest=OperationalPlanIntegrationTest#graphCompilationAndLayoutPreserveOperationalFacts' '-DargLine=-Duser.timezone=UTC' test
# FE: use one worker on this machine to avoid memory exhaustion
npx vitest run --maxWorkers=1
npx eslint src/features/plans/studio
npm run build
```

Build still reports the existing large bootstrap bundle warning. Production
promotion of migration018 remains a separate reviewed deployment decision.
