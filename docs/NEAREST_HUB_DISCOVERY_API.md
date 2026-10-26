# Nearest Hub Discovery — Backend contract

POST `/api/routes/match` remains the single public routing endpoint.
No FE changes or new DB schema are required by this feature. It uses active HUB
rows and coordinates from migration027 in LocationCatalog.

Request additions (both optional positive integers): `originHubId`,
`destinationHubId`. Existing 8/10-argument Java constructors remain available.

Example:
```json
{
  "originCountryCode": "VN",
  "destinationCountryCode": "CN",
  "transportMode": "ROAD",
  "pickupAddress": "Quận 1, TP. Hồ Chí Minh",
  "deliveryAddress": "Nanning",
  "originHubId": null,
  "destinationHubId": null
}
```

Response adds `originHubOptions`, `destinationHubOptions`, `originHubId`,
`destinationHubId`. Each option contains locationId, locationName, locationCode,
address, latitude, longitude, isPrimaryHub, distanceKm, domesticCost and
isRecommendedNearest. Arrays are present for matched, custom and unsupported
responses; available-country options are retained if the other country lacks hubs.

BE resolves the customer point once per country, measures all hubs with known
coordinates, and sorts increasing road km. Unknown distances/costs are null and
sort last. Ties prefer primary, then lowest ID. Exactly one measured option is
recommended. Without a resolved customer location, primary is selected; if no
primary exists, lowest ID wins. An explicit valid choice overrides selection,
not the nearest recommendation. Delivery routing runs hub -> customer;
pickup runs customer -> hub (road directions can differ).

An unknown, inactive, non-HUB or foreign-country ID returns
422 `INVALID_HUB_SELECTION`. Nonpositive IDs fail bean validation with 400.
Country without active hubs returns the existing unsupported response. Legacy
schema without hub columns retains old behavior when no hub IDs were supplied;
explicit IDs require migration027 and return 503 `HUB_SCHEMA_PENDING` otherwise.

Domestic pricing: <=100.00 km is 0.00 USD; >100.00 km is the entire distance
multiplied by 2.00 USD/km, rounded to cents. Both ranked option prices and applied
first/last-mile prices use RouteTemplatePolicy.calculateDomesticCost.
Main international leg distance/rate still comes from the matched RouteTemplate;
this feature does not create a different international template for every hub pair.
Unknown endpoint distance is not a verified free transfer: the options show null;
legacy matched aggregate fields still contain the main-leg estimate only.
OSRM road results can fall back to existing Haversine x1.3 estimates; these are
indicative distances, not guaranteed measured truck itineraries.

For order submission send the same hub IDs under `route`; normalization and
server-side recomputation retain both applied IDs and ranked options inside
OrderRevision snapshot.routeMatch. Existing snapshots remain readable.
No automatic order approval, price acceptance or new TripLeg is created.

Tests: HubDiscoveryTest uses deterministic mocks, covering ranking, manual
selection, unavailable coordinates, invalid selection, direction, ties, fee
boundaries and DTO serialization. RouteTemplateSqlTest checks public API IDs,
invalid/hidden/foreign choices and selected IDs in submitted revision snapshots.
SQL fixture writes are restricted to Demo/Test and rolled back.

Compatibility: legacy order submissions omitting `route` are not blocked when
no endpoint hub exists; their routeMatch stays absent. The public match endpoint
and explicit route selections remain strict. This keeps pre-selector Compliance
and Document workflows working without bypassing selected-hub validation.

## Verification (2026-10-26)

`mvnw.cmd -B test-compile checkstyle:check test` with native JDBC and explicit
Demo datasource flags: BUILD SUCCESS, 449 discovered, 280 executed/passed,
169 gated/disabled tests skipped, zero failures/errors, zero Checkstyle errors.
The enabled native groups include routes, hubs, document templates, document
workflow, compliance and Journey. Full evidence: `.local/nearest-full.log`.
HubDiscoveryTest: 6 passed; RouteTemplatePolicyTest: 11 passed;
RouteTemplateSqlTest: 7 passed; CompanyHubSqlTest: 4 passed.
No new migration or persistent fixture data is required for discovery itself.
No writes/DDL were executed against the real EquiTransit database.

Live verification: Demo runs on http://127.0.0.1:8082 because 8081 is occupied
by another runtime. Health UP; automatic origin hub 26; manual selection 42;
two VN choices, one CN choice; first-mile cost 0.00 and main total 1950.00 USD.
Evidence: `.local/nearest-http-smoke.json`. Existing runtimes are preserved.
Package: `.local/nearest-hub-package/backend-0.1.0-SNAPSHOT.jar`.
Reproduce with:
```powershell
.\tools\package-backend-isolated.ps1 -SkipTests `
    -OutputDirectory '.local/nearest-hub-package'
.\tools\run-journey-v2-demo.ps1 -Port 8082 `
    -JarPath '.local/nearest-hub-package/backend-0.1.0-SNAPSHOT.jar'
# Stop only this managed Demo:
.\tools\run-journey-v2-demo.ps1 -Stop -Port 8082
```
Packaging uses the already verified source; -SkipTests applies only to that
packaging command. The full test run above executed separately without skipping
its enabled suites. No commit/push was requested for this implementation task.
