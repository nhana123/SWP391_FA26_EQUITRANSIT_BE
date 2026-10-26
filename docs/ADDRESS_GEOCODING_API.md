# Address search and validation — BE contract

## Search

`GET /api/routes/address/search?query=Nguyen%20Hue&countryCode=VN&limit=5`
Public endpoint; query required, trimmed 2-500 characters; optional ISO alpha-2
countryCode (case normalized); limit 1-10, default5. Invalid parameters return400.
Response is an array of AddressSuggestion:
```json
[{
  "displayName": "123 Nguyễn Huệ, Quận 1, Hồ Chí Minh, Việt Nam",
  "formattedAddress": "123 Nguyễn Huệ, Quận 1, Hồ Chí Minh",
  "latitude": 10.773461,
  "longitude": 106.704078,
  "countryCode": "VN",
  "city": "Hồ Chí Minh",
  "district": "Quận 1",
  "road": "Nguyễn Huệ"
}]
```
Labels above illustrate the DTO, not a certified real property address. Optional
road/district/city components can be null. Formatted address composes house/road,
ward, district, city, deduplicating identical components. Full provider label
is retained separately. Returned coordinates are range checked. Country filters
are applied both in Nominatim and to its returned country_code.
No matching provider results ->200 `[]`. No offline/province/known-coordinate
fallback is used on this endpoint.

## Validation

`POST /api/routes/address/validate`
```json
{"address":"123 Nguyen Hue","countryCode":"VN"}
```
Response:
```json
{"isValid":false,"matchedAddress":null,
 "message":"No matching address found; select a search result."}
```
If a provider candidate contains all normalized input tokens, isValid=true and
matchedAddress contains AddressSuggestion. Accents/case/punctuation normalize;
extra nonsense tokens or an unmatched house number do not pass merely because
the provider recognized the city. Search and validation share cached queries. Full displayName results are also
cached so selecting a result can be validated immediately without another
provider request.
Prefer letting the customer select a returned displayName, then validate that
address. False means no matching indexed address was found, not conclusive proof
that a location does not exist. True is geocoder matching, not proof of property
ownership, deliverability, farm access or a truck-suitable pickup point.

FE must block progression unless validation succeeds and handle provider errors
separately. This task supplies the API; it does not change the FE or retrofit a
mandatory remote check into legacy order-submit workflows.

## Errors, provider limits and privacy

- 400 INVALID_ADDRESS_SEARCH / INVALID_COUNTRY_CODE or binding validation error.
- 429 GEOCODING_RATE_LIMITED: an outbound request is in progress or the next
  1-second slot has not arrived; retry later, do not spin.
- 503 GEOCODING_UNAVAILABLE: upstream HTTP failure, malformed payload, connection
  failure, interruption or timeout. Never convert this into []/isValid=false.

HTTP connect timeout2s/request timeout4s. Bounded in-memory cache: up to1000
query+country+limit entries, 1-hour TTL, including empty results. No address
queries are logged or persisted by this implementation. All Spring-managed
Nominatim calls in GeoDistanceService use this shared rate-limited gateway;
distance estimates may still fall back to a province, validation may not.

IMPORTANT: the public nominatim.openstreetmap.org policy **forbids keystroke
API autocomplete**, even when debounced. Use an explicit Search button/Enter
for modest user-triggered searches. The application must show attribution
“© OpenStreetMap contributors” linked to https://www.openstreetmap.org/copyright.
Do not send personal/confidential information (names, phone numbers, private
customer instructions) to the public service. A geocoding result is not a
replacement for the customer's consent to transmit address data to a provider.

Public provider traffic must stay <=1 request/second **for the entire app**.
The limiter here covers one JVM. Use a single active gateway instance with the
public provider; multi-instance deployment requires shared throttling or your
own provider. Do not use periodic/bulk requests. For real keystroke autocomplete
or private-address processing, use a self-hosted Nominatim or a provider whose
terms explicitly support that workload. This adapter expects Nominatim JSON.

Official policy: https://operations.osmfoundation.org/policies/nominatim/
Search API: https://nominatim.org/release-docs/latest/api/Search/
OSM data/attribution: https://www.openstreetmap.org/copyright (ODbL).

## Configuration

No API key. Provider can be switched without a code change:
```powershell
$env:EQ_NOMINATIM_SEARCH_URL='http://localhost:7070/search'
$env:EQ_NOMINATIM_USER_AGENT='EquiTransit-Address/1.0'
```
Default URL is https://nominatim.openstreetmap.org/search. Configure the complete
HTTP(S) search path without query/fragment. Configure an identifying User-Agent
appropriate to the operating organization. Switching config requires a process
restart, not a software update. The conservative1rps cap remains for self-hosted
providers too. UI autocomplete is not implemented in this BE-only task.

## Verification

AddressGeocodingTest (5 cases): local fake HTTP server, deterministic clock;
checks Unicode/headers/query parameters, country filtering, mapping, cache/TTL,
nonsense/strict validation, quota, upstream failure and malformed coordinates.
AddressHttpTest (2 cases): actual HTTP/security/validation stack with mock
provider, public endpoints, bounds, false validation and503 response semantics.
Tests do not call the public Nominatim service and do not modify real DB data.


## Verified build and live smoke — 2026-10-26

Full command `mvnw.cmd -B test-compile checkstyle:check test` with explicit native
Demo datasource flags: BUILD SUCCESS, 462 discovered, 293 executed/PASS,
169 gated/disabled cases skipped, zero errors/failures and zero Checkstyle
violations. Log: `.local/address-full.log`. New address suites:7/7 PASS.

The JAR was then packaged separately with -SkipTests (the full test run above
was not skipped): `.local/address-package/backend-0.1.0-SNAPSHOT.jar`.
Managed Demo8082 now runs this JAR; Swagger:
http://127.0.0.1:8082/swagger-ui/index.html

Live provider smoke: asdfgh/VN ->200 [] and validation false; Nguyen Hue/VN ->
5 mapped suggestions, selected displayName validation true from cache.
Health UP; both endpoints present in generated OpenAPI. Only2 user-triggered
public provider searches were needed; validations used cached results.
Evidence: `.local/address-http-smoke.json`. No FE edits or real DB DDL/writes.
No new migration is required. Native tests only used Demo with rollback fixtures.
