# Retail service pricing and read-only preview

This extension covers the price-management part of F15 and the pricing display
part of F05. No staff provisioning, shared-stall matching, payment or operational
confirmation is added. All routes produce application/json.

## Permissions and routes

| Method | Route | Role | Result |
|---|---|---|---|
| GET | /api/manager/service-tariffs/products | MANAGER | Fixed products, units, modes and current public rates |
| GET | /api/manager/service-tariffs?page=0&size=20 | MANAGER | Version history envelope |
| GET | /api/manager/service-tariffs/{id} | MANAGER | One version, provenance and rowversion |
| POST | /api/manager/service-tariffs | MANAGER | Publish a new active price family |
| PUT | /api/manager/service-tariffs/{id} | MANAGER | Publish a replacement of the latest version |
| GET | /api/transport-orders/{id}/services/rates | Owning CUSTOMER | All product option retail unit rates |
| POST | /api/transport-orders/{id}/services/preview | Owning CUSTOMER | Live-source estimate for unsaved preferences |

Manager role and live active StaffProfile are rechecked for publication. The
existing MANAGER role represents Logistics Manager; there is no extra role.
Public rates contain no private source reference or purchase cost. Source
references on the Manager routes are private business publication evidence.

## Manager publication

New price family (200). IDs, dates and rates below are documentation examples,
not a production tariff to seed:

```json
{
  "product": "GROOMING",
  "unitPrice": "1.25",
  "sourceReference": "Approved company tariff, October 2026",
  "validUntil": "2027-12-31T00:00:00Z",
  "operationId": "1f48f6d1-2280-4c4f-a56c-86dcab0af9cf",
  "recordVersion": null
}
```

PUT uses the same shape plus recordVersion from the latest GET, with a new
operationId. It returns the NEW tariffId, incremented family version and its
rowversion. Never overwrite UnitPrice on the old row or patch quote items.
An active family already present requires PUT, not another POST. Currency USD,
unit and cost group come from the fixed product definition, not the client.
Money is an exact decimal string with at most two fractional and fourteen
integer digits (SQL UnitPrice DECIMAL(18,4)). Zero is allowed only as an explicit
sourced rate; missing pricing is always null, never fabricated zero.

Publication starts at server UTC seconds. A same-second replacement activates
one second after the predecessor's start because SQL DATETIME2(0) cannot store
subsecond intervals. The previous effective interval is capped at the new start;
no overlapping active rate is allowed by this adapter. Existing ambiguous legacy
families are rejected for explicit repair rather than guessed. Optional expiry
must follow the actual activation instant. No scheduling editor or tariff delete
API is introduced in this scope.

Each catalog write holds a finite transaction-owned catalog application lock,
checks rowversion/latest version, and stores UserID-scoped UUID/hash/result in
AuditEvent in the same transaction. Publication provenance uses trusted StaffID.
Retry the identical operationId/path/input to receive the original receipt.
Changed input with the same UUID, stale versions or competing updates return409.
No automatic retry using a new version or UUID is allowed.

History is stable newest-first, page zero-based, size1..100. Expired ACTIVE rows
remain history; ACTIVE alone does not mean currently effective. Read the dates.
Legacy provenance remains null, sourceReviewRequired=true; unsupported legacy
fractional rates return unitPrice=null for explicit replacement, never a rounded
pretend source. No historical evidence is invented by migration.

## Products and modes

GET products/rates returns a bounded 23-item catalog. It includes unselected
options, so FE can show a unit rate inside every relevant card before choosing.
Use product/uiSection/unit/pricingMode from the response. Modes:

- RATE: a company rate; quantity still needs its actual source.
- EVIDENCE: a rate alone does not prove case applicability/supplier/insurer terms.
- INCLUDED: cannot publish a separate charge; display included, not zero-priced.
- EVENT: charged only after a sourced applicable event, never pre-added.

The supported identities are ROAD_TRANSPORT, AIRFARE, AIR_STALL, BASIC_CARE,
ENHANCED_CARE, GROOMING, BOARDING, VETERINARY_EXAM, VETERINARY_TEST,
VETERINARY_VACCINE, VETERINARY_CERTIFICATE, QUARANTINE_STANDARD,
QUARANTINE_ENHANCED, INSURANCE_MORTALITY, INSURANCE_COMBINED,
DOCUMENT_COORDINATION, DOCUMENT_REVIEW, AIRPORT_HANDLING, TERMINAL_HANDLING,
CUSTOMS_DECLARATION, GOVERNMENT_DUTY, WAITING and ESCORT.

BASIC_CARE and DOCUMENT_REVIEW remain included and cannot be priced again.
Air stall is slot quantity x sourced AIR km x SLOT_KM rate; Shared/Wide/Private
still represent requested configurations, not confirmed space. Existing insurance
and NONE do not sell a new premium. Missing/ambiguous/invalid rates are null.
Rates for evidence-dependent services do not bypass the F06 assessment rules.
The system still does not infer government duty, insurance premiums or flight
fares from arbitrary quantities or a declared horse value.

## Customer preview

Save/create an order first, GET services to obtain the observed order and
captured horse versions, and retain the entire preference object. POST preview:

```json
{
  "recordVersion": "000000000001A2B3",
  "horseRecordVersion": "000000000001A2B4",
  "services": {
    "roadArrangement": "EQUITRANSIT",
    "veterinaryArrangement": "EQUITRANSIT",
    "airStall": null,
    "enhancedCare": null,
    "groomingDays": 3,
    "boardingNights": null,
    "quarantinePackage": null,
    "insurance": "NONE",
    "declaredValue": null,
    "existingPolicyReference": null,
    "escortNotes": null,
    "roadProviderName": null,
    "roadProviderContact": null,
    "veterinaryProviderName": null,
    "veterinaryProviderContact": null
  }
}
```

Response is the existing ServiceDisclosureResponse: sections, airStalls,
quarantine, charges, knownSubtotal, totalEstimate, selected and observed versions.
Amounts/rates stay decimal strings; quantity retains up to three decimals and
money uses HALF_UP at two decimals. FE never sums or invents a payable total.
Preview reuses F05 source/selection rules; missing km/clinical/quarantine counts,
policy or commercial evidence remain pending. Client cannot supply rates or
mandatory days. Unknown input fields are rejected. Fit is not carrier acceptance.

Preview NEVER changes preferences, order rowversion, revision, quote, schedule,
audit event or allocations. It always has payable=false and NOT_CONFIRMED.
Use returned selected values as unsaved preview only; POST /services/confirm
remains the sole service-selection write and retains its existing UUID protocol.
Preview has no operationId because it has no persisted side effect.

GET services retains the usable F06 captured commercial breakdown when present.
POST preview deliberately calculates live-source unsaved choices without reusing
that quotation as authorization for changed inputs. Current catalog rates and a
previously issued/accepted quotation can differ; label them separately. Preview
cannot change a commitment, even if it can display a hypothetical estimate.

Debounce/cancel preview requests and discard responses from older preferences,
order/account generations or versions. On409 preserve input and ask for explicit
reload/compare; never silently recapture a horse or force a new order version.
On404 clear unavailable data; no foreign-order lookup or fallback fixture.

## Errors, migration and validation

400 malformed/unknown fields or formats;401/403 auth/role;404 ownership/missing;
409 stale versions, duplicate/ambiguous rates, included charges, precision,
expiry or retry conflicts;503 TARIFF_SCHEMA_PENDING before management schema.

006_service_tariff_management.sql follows001–005, adds provenance and rowversion
only, is transactional/reapply-safe and never seeds prices or fake policy. It
must be reviewed and explicitly deployed on each target database; code does not
automigrate real EquiTransit. Legacy rows remain valid but provenance is unknown.
Forward repair should preserve all tariff/quote/audit history; no destructive
rollback over real customer data. Existing quote rates and terms are immutable.

Native tests are opt-in in the exact isolated database
EquiTransit_FigmaMigrationTest_20261013_143729. They cover role/input validation,
publication/replacement/replay, concurrent replacement, rollback, read-only preview,
foreign/stale input, existing quotes and exact calculations. Fixtures are removed
by their created IDs. No load/capacity SLA is inferred from functional checks.

## Pricing update — 17/10/2026

Manager tariff writes accept optional `unitCode`. AIR_STALL supports SLOT_KM
or SHIPMENT; ENHANCED_CARE supports HORSE_KM or SHIPMENT; document coordination
supports ROUTE_KM or SHIPMENT. Omit the field on create for the legacy default;
omit it on replacement to retain the current unit. `/services/rates` includes
`allowedUnits` and reports the effective published unit. Other product units
remain fixed. PUT still creates a new version with rowversion/UUID protection.

Supplier offers accept optional `bundleCoverage`: ENTIRE_ROUTE, PARTIAL_ROUTE,
or UNKNOWN. Omission means UNKNOWN. This is stored in existing IncludedCharges
JSON, without a migration. Only verified ENTIRE_ROUTE inclusions automatically
waive the entire charge. Partial/legacy coverage needs assessment; separately
priced AIR_STALL/handling evidence may charge only the uncovered scope.
Existing agreed quote snapshots remain immutable.

F05 infers handling airports=2+transits, international declarations=2 and
requested escort legs=1+transits while sourced AIR=0 suppresses air quantities.
These are commercial estimates. F06 evidenced values override estimates.
Unchanged preview inputs retain the usable F06 line; changed inputs recalculate.
WIDE is a view default only after proven one-slot exclusion, and explicit
confirmation saves the request. Shared/wide requests remain SHARED 1/1.5 slots;
PRIVATE requests remain PRIVATE 3 slots. STALLION carries a compatibility review
warning, not an invented measurement rejection. Unsuitable writes return422.
This update does not implement automatic stall matching or confirm a booking.

Verified:119 focused tests (60 native SQL), zero failures/errors/skips and zero
Checkstyle violations. Test database only;14 checked business tables empty.
