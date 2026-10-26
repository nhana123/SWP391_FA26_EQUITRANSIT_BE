# Offline administrative subdivisions

## Version and coverage

`country-subdivisions.json` contains 4,953 coordinate-bearing subdivision rows,
plus one lookup-only Kunming city point. Core country subsets are validated at
startup and by tests: VN 63, LA 18, CN 34, TH 77 (including Bangkok, excluding
Pattaya as a separate province), US 51 (50 states + DC, excluding territories).
Other countries use the coordinate-bearing subset of the same source snapshot;
coverage outside the five core countries is available, not guaranteed exhaustive.

VN intentionally uses the requested **historical 63-province edition**. It must
not be described as today's Vietnamese administrative boundaries. Vietnam's
post-reorganization 34 units started operation on 2025-07-01; see the Government
announcement:
https://baochinhphu.vn/quoc-hoi-thong-qua-viec-sap-xep-don-vi-hanh-chinh-cap-tinh-ca-nuoc-co-34-tinh-thanh-102250612100156988.htm

CN retains the source dataset's 34-entry operational grouping, including the
source's HK/MO/TW entries; country-scoped lookup does not change ISO country
codes elsewhere.

## Attribution / data license

Base: Countries States Cities Database, by dr5hn and contributors:
https://github.com/dr5hn/countries-states-cities-database
Pinned source commit: a7ebcf64ea6184e78864c8ab429657c542931712
https://raw.githubusercontent.com/dr5hn/countries-states-cities-database/a7ebcf64ea6184e78864c8ab429657c542931712/json/states.json
Source states.json SHA256:
339BD58BB1B649FF75B94487C662D5457663263AB0498255E33FA7623F9E323D

The derived database is distributed under ODbL 1.0; full terms accompany it in
LICENSE-ODbL.txt. Attribution and share-alike apply to the dataset, independently
of the surrounding Java source. Adaptations include filtering, normalized codes,
Vietnamese aliases, disambiguation and coordinate corrections.

GeoNames corrections and lookup point are CC BY 4.0, attributed to GeoNames:
https://www.geonames.org/ ; https://creativecommons.org/licenses/by/4.0/
All 77 TH ADM1 coordinates use the GeoNames TH dump retrieved 2026-10-26:
https://download.geonames.org/export/dump/TH.zip
Archive SHA256:
4BDCCCEAF37186946A3F87FFC0E6F0AF9BDBA8EA743BB6555BF548EDC1289CE0
Sơn Đông/Shandong is corrected using:
https://www.geonames.org/1796328/shandong.html
Kunming/Côn Minh lookup-only city point:
https://www.geonames.org/1804651/kunming.html

Several upstream coordinates pointed to the wrong continent. All TH coordinates
were replaced with corresponding GeoNames ADM1 points; Shandong was corrected.
TH/CN bounding checks prevent those regressions. LA-VI (Vientiane Province) and
LA-VT (Vientiane capital) are separate entries, with distinct names/codes.

These are **representative region coordinates**, not certified locations of
province-government offices. They enable an indicative city/province fallback;
an accurately geocoded customer street takes precedence. OSRM/Haversine fallback
retains the existing distance-estimation limitations.

## Extending / rebuilding

No application-startup downloads occur. Bundle updates are reviewed versioned
changes. Add records/aliases with stable unique source IDs, ISO country scope,
valid coordinates and attribution; test before changing the edition.

`tools/build-subdivision-resource.ps1` transforms source artifacts in
`.local/subdivision-source/` (`states.json`, `commit.txt`, `LICENSE`,
`th-adm1.json`) into the packaged resource. The latter is extracted from TH.txt
in the archive: retain featureCode ADM1 and fields geonameId/name/latitude/
longitude/admin1Code. Raw download artifacts remain outside the repository.
The generated JSON and its license are included in the runnable JAR.

