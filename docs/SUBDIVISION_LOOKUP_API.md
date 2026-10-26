# Administrative subdivision lookup — Backend / Frontend contract

Existing public endpoint is retained:
`GET /api/lookups/locations?country=VN&type=CITY`

The response remains an array of Location: locationId, countryCode,
locationType, locationName, locationCode, address, latitude, longitude,
isPrimaryHub, domesticRatePerKm, isActive. No FE API fork or new DB migration.
AIRPORT/HUB lookup and Hub management continue to use SQL rows only.

CITY lookup returns the alphabetically sorted **union** of active SQL cities and
packaged subdivisions, canonicalized by country + normalized name/alias/code.
The standard core counts are VN63, LA18, CN34, TH77 and US51. SQL-only cities can
increase the returned count (e.g. Nanning/Pingxiang are cities in addition to the
34 CN subdivisions); this is intentional. Existing SQL IDs, labels, addresses,
rates and complete configured coordinates take precedence. Missing coordinates
on matched SQL cities are enriched from the standard region without a DB write.

Resource entries have stable **negative** IDs derived from source IDs, a full
subdivision code (e.g. VN-DN / US-TX), active=true and primary=false. They are
read-only lookup options, not dbo.LocationCatalog identities: do not send their
IDs to Manager location CRUD or use them as Hub IDs. Keep sending selected
locationName or locationCode as originCity/destinationCity on routes/match.
Existing custom SQL city coordinates remain the Manager's responsibility if no
standard name/alias can match; never invent unknown coordinates.

Examples:
- VN: `Đà Nẵng`, `da nang`, `VN-DN` resolve identically.
- VN: `TP. Hồ Chí Minh` / `Ho Chi Minh City` canonicalize to the same region.
- LA: `Vientiane` (capital, LA-VT) and `Vientiane Province` (LA-VI) stay distinct.
- CN: `Quảng Tây` and `Côn Minh` aliases are supported; Kunming is a lookup-only
  city point, not a 35th province in the standard dropdown.
- US: `Texas` / `US-TX` work without live geocoding.

LocationCatalogRepository.findByNameOrCode first reads SQL; if absent it resolves
country-scoped resource names/codes/aliases. Missing SQL CITY coordinates are
filled using the standard region. HubDiscoveryService therefore measures all
active hubs from a selected province even without a street. GeoDistanceService
also receives the offline catalog directly. A successfully geocoded street wins;
failed street geocoding falls back to the selected region point. No network
request is needed to list regions or to resolve their offline fallback points.
Nearest-Hub sorting, explicit Hub selection and <=100 km free / >100 km x2 USD
remain unchanged. Selecting a region in an unsupported country does not create
company Hubs or enable transport service to that country.

The VN63 edition is historical, as explicitly requested, not current VN34.
See `src/main/resources/geo/README.md` for date, source commit, licenses,
coordinate meaning, corrections and extension instructions.

Verification: SubdivisionCatalogTest covers counts, bounds, aliases, merge,
SQL precedence and offline fallback. SubdivisionSqlTest verifies public HTTP
coverage, repository fallback and nearest Hub from Đà Nẵng with no street or an
unknown street. Native fixture writes are Demo/Test only and roll back.

## Verification — 2026-10-26

Full backend run: `mvnw.cmd -B test-compile checkstyle:check test` with native
JDBC and explicitly configured Demo URLs: BUILD SUCCESS, 455 discovered,
286 executed/PASS, 169 gated/disabled tests skipped, zero errors/failures,
zero Checkstyle violations. Evidence: `.local/subdivision-full.log`.
New tests: SubdivisionCatalogTest (4), SubdivisionSqlTest (2), all PASS.

Packaged separately after the full test run using -SkipTests; artifact:
`.local/subdivision-package/backend-0.1.0-SNAPSHOT.jar`.
The managed Demo runtime on http://127.0.0.1:8082 is updated to this artifact.
Existing other runtimes are preserved.

Live HTTP smoke: VN63 / LA18 / CN36 / TH77 / US51, every returned core-country
CITY has coordinates. CN36 = 34 standard subdivisions + 2 SQL-only cities.
Province-only Đà Nẵng recommends hub26, measured estimate 849.65 km and
1699.30 USD domestic transfer under the existing fee rule. Route values can vary
with the road provider; full evidence: `.local/subdivision-http-smoke.json`.
No DDL, persistent seed or real EquiTransit DB write was needed for this feature.
