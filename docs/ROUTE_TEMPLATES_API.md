# Predefined routes and custom-route intake

## Deployment and data

Migration026: `database/migrations/026_predefined_route_templates.sql` creates
dbo.RouteTemplate, uniqueness/check constraints and an active country/mode
lookup index. CreatedBy references StaffProfile.StaffID, not a client-supplied
user ID. Dates use SQL Server SYSDATETIMEOFFSET().

Three project-supplied indicative routes/rates are seeded idempotently:

| Code | Mode | Road km | Air km | Hours | Road USD/km | Base air USD/horse |
|---|---|---:|---:|---:|---:|---:|
| ROUTE-VN-CN-ROAD | ROAD | 1950 | 0 | 35.5 | 1 | 0 |
| ROUTE-VN-LA-ROAD | ROAD | 1100 | 0 | 20 | 1 | 0 |
| ROUTE-VN-FR-AIR | AIR (HAN–CDG) | 0 | 9200 | 14.5 | 1 | 1200 |

These are reference estimates supplied for this project, not verified live
road routing, carrier schedules or quotations. Road examples are country-level
defaults with their described HCMC-origin summaries; configure city-specific
templates when actual pickup geography needs a different estimate. Flight
hours do not represent the entire approval/document/clearance lead time.

The migration is restricted to EquiTransit_Demo / EquiTransit_Test and records
026_predefined_route_templates in SchemaMigrationHistory. It does not overwrite
existing templates or reactivate hidden rows on rerun. Applied twice on Demo
after COPY_ONLY/CHECKSUM backup and RESTORE VERIFYONLY:
`C:\Program Files\Microsoft SQL Server\MSSQL15.MSSQLSERVER\MSSQL\Backup\EquiTransit_Demo_before_026_20261025_171302.bak`.
No DDL was issued to the real EquiTransit database.

## Coordinator catalog API

Bearer authentication and current COORDINATOR or MANAGER role are required.
MANAGER is the existing project's equivalent of the requested Logistics
Manager; this module does not invent another Role row.

- GET `/api/coordinator/route-templates`
  optional includeInactive=false, page=0, size=100 (maximum 200).
- GET `/api/coordinator/route-templates/{id}` returns active or hidden row.
- POST `/api/coordinator/route-templates` -> 201, RouteTemplateResponse.
- PUT `/api/coordinator/route-templates/{id}` -> 200, full replacement.
- DELETE `/api/coordinator/route-templates/{id}` -> 204, sets IsActive=false.
  No physical delete; submitted snapshots remain intact.

Create/update example:

```json
{
  "routeCode": "ROUTE-VN-CN-ROAD-CUSTOM",
  "routeName": "HCMC to Nanning",
  "originCountryCode": "VN",
  "originCity": "TP. Hồ Chí Minh",
  "destinationCountryCode": "CN",
  "destinationCity": "Nam Ninh",
  "transportMode": "ROAD",
  "estimatedRoadKm": 1950.00,
  "estimatedAirKm": 0.00,
  "estimatedHours": 35.5,
  "routeSummary": "HCMC → Hữu Nghị Quan → Nam Ninh",
  "baseRoadRate": 1.00,
  "baseAirRate": 0.00,
  "isActive": true
}
```

Optional originAirportCode/destinationAirportCode use three-letter IATA codes.
RouteTemplateResponse includes the request fields plus routeTemplateId,
createdBy, createdAt and updatedAt. Omitted distances default to 0, rates to
1.00/0.00, isActive to true. ROAD requires positive road/zero air distance
and no air rate; AIR positive air/zero road distance; MULTIMODAL positive
distances for both. Distances/rates permit 2 decimal places, hours 1.
Country codes must be distinct valid ISO codes. CreatedBy never changes on PUT.

Errors: 400 invalid DTO/precision, 401 anonymous, 403 wrong role,
404 ROUTE_TEMPLATE_NOT_FOUND, 409 ROUTE_CODE_EXISTS,
422 invalid countries or ROUTE_DISTANCE_MODE_MISMATCH,
503 ROUTE_TEMPLATE_SCHEMA_PENDING when migration026 is missing.

## Public match and indicative price

POST `/api/routes/match`, no authentication required:

```json
{
  "originCountryCode": "VN",
  "originAirportCode": "HAN",
  "destinationCountryCode": "FR",
  "destinationAirportCode": "CDG",
  "transportMode": "AIR",
  "airStallPreference": "WIDE"
}
```

Country codes are mandatory. Cities, airports, mode and stall are optional.
Only active routes in the requested direction are considered; reverse travel
does not reuse the forward route automatically. Supplied mode must match.
Supplied cities/airports must match a specific template or a NULL wildcard.
Cities compare without case/accent sensitivity. More specific matches beat
country defaults; equal specificity uses ascending RouteTemplateID.
If city/airport is omitted, country-level/default selection is allowed.

All amounts are indicative USD. There is no horseCount in this request:
airfare represents one horse; road is the configured route segment charge.

- estimatedRoadCost = estimatedRoadKm × baseRoadRate.
- estimatedAirCost = baseAirRate × StallFitEvaluator.slots(preference).
- SHARED_ONE = 1, WIDE = 1.5, PRIVATE = 3; omitted stall defaults SHARED_ONE.
- Each component rounds HALF_UP to cents; total sums those components.
- Airfare is not multiplied by air distance. A slot preference is not a
  reservation or proof that the horse/carrier passes existing fit checks.

The example returns matched=true, 0 road km/cost, 9200 air km, 14.5 hours,
1800 air/total cost and requiresCustomCoordinatorPlan=false, plus templateId,
transportMode, routeSummary and an indicative-price message.

Unmatched response has matched=false, templateId=null,
requiresCustomCoordinatorPlan=true, the requested mode (or null), and the
specified Coordinator-planning message. Distances/hours/costs are NULL because
they are unknown, not zero/free. The match endpoint itself creates no order,
assignment, TripLeg or financial commitment.

## Persist on actual customer submission

The existing OrderDraftRequest gains optional `route: RouteMatchRequest`.
It is accepted by existing create/update/revise endpoints and stored in the
draft payload. Example fragment alongside existing order fields:

```json
{
  "originCountryCode": "VN",
  "destinationCountryCode": "FR",
  "route": {
    "originCountryCode": "VN",
    "originAirportCode": "HAN",
    "destinationCountryCode": "FR",
    "destinationAirportCode": "CDG",
    "transportMode": "AIR",
    "airStallPreference": "WIDE"
  }
}
```

Existing service selections still use `services` (for example airStall=WIDE);
if both provide a stall preference they must agree. The route selection does
not bypass existing horse-fit, service-choice or quotation validation.

At submit/revise, after ownership, concurrency and completeness checks, Backend
re-matches current catalog data and freezes the result in
`OrderSnapshot.routeMatch`, persisted inside dbo.OrderRevision.OrderSnapshot.
No client-supplied template ID, price or matched flag is accepted. Endpoint
countries must agree with the outer order or submission fails with
422 ROUTE_SELECTION_MISMATCH without inserting a revision.

When route is omitted, upgraded databases classify from order countries and
the existing stall preference. On legacy databases without migration026,
omitting route retains the prior snapshot behavior. Explicit new route
selection requires the new schema. Null route is omitted from JSON so legacy
draft fingerprints do not change. Old snapshots still deserialize.

Submitted snapshots do not change when a rate is edited or a template hidden.
Identical submission retries return the original revision/price, not a fresh
estimate. New revisions compute a new snapshot.

The order keeps its existing SUBMITTED status. Manager still reviews and
assigns Coordinator via the existing case allocation flow. Assigned Coordinator
reads `revision.snapshot.routeMatch.requiresCustomCoordinatorPlan` through
GET `/api/staff/transport-orders/{id}` and creates the needed F10 plan/graph.
The custom flag records the intake classification; completing a graph does not
rewrite that historical flag. A template summary does not auto-create or
confirm a structured operational plan, and estimates do not issue an F05/F06
quotation. Existing plan, legal and financial gates remain authoritative.

## Verification and runtime

`tools/verify-route-templates.ps1` runs RouteTemplatePolicyTest (7 cases) and
RouteTemplateSqlTest (5 cases), checking that native tests are not skipped.
Fixtures, rate edits, customer orders and assignments roll back on Demo/Test.

Full regression: 433 discovered; 264 executed and passed, 169 other gated tests
skipped. Zero Checkstyle violations. Logs: `.local/route-templates-final.log`.

The standard target JAR was locked by another runtime at repackage time;
`tools/package-backend-isolated.ps1 -SkipTests` completed Maven verify/package
on the same tested source using an isolated output directory. It leaves pom.xml
unchanged. The full test run above was not skipped. Packaged JAR:
`.local/route-package/backend-0.1.0-SNAPSHOT.jar`.
Launch with `tools/run-journey-v2-demo.ps1 -JarPath` pointing to that relative
path. The helper still validates the Demo datasource and refuses to kill an
unrecognized process on its port. Other runtimes and the real DB stay intact.

Database check: three seed rows, zero TEST-* fixtures, one migration026 entry,
zero disabled/untrusted foreign keys. Backup: `.local/route-migration.json`.
Live Demo smoke on 8081: health UP, VN–CN ROAD total 1950 USD, HAN–CDG WIDE
total 1800 USD, VN–IS custom flag true with NULL price. Swagger references
RouteMatchResponse. Evidence: `.local/route-http-smoke.json`.
No FE edits, Git commit, push or PR update are part of this implementation task.
