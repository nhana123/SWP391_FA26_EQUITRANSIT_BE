package com.equitransit.backend.service;

import com.equitransit.backend.repository.LocationCatalogRepository.Location;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Versioned offline subdivision data; no geocoder/network needed to load. */
@Component
public class SubdivisionCatalog {
    private final Map<String, List<Location>> countries = new HashMap<>();
    private final Map<String, Location> names = new HashMap<>();
    private final Set<String> ambiguous = new HashSet<>();

    /** Load and validate the packaged dataset; fail fast on corruption. */
    public SubdivisionCatalog() {
        var resource = new ClassPathResource("geo/country-subdivisions.json");
        try (var stream = resource.getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(stream);
            if (root.path("schemaVersion").asInt() != 1) {
                throw new IllegalStateException("Unsupported geo schema.");
            }
            Set<Integer> ids = new HashSet<>();
            for (JsonNode row : root.path("subdivisions")) {
                load(row, ids, true);
            }
            for (JsonNode row : root.path("lookupPoints")) {
                load(row, ids, false);
            }
            for (String country : List.of("VN", "LA", "CN", "TH", "US")) {
                int expected = Map.of("VN", 63, "LA", 18, "CN", 34,
                        "TH", 77, "US", 51).get(country);
                if (list(country).size() != expected) {
                    throw new IllegalStateException("Incomplete geo country.");
                }
            }
            countries.replaceAll((country, rows) -> sorted(rows));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load subdivision data.",
                    error);
        }
    }

    /** @param country ISO code
     * @return immutable representative region points */
    public List<Location> list(String country) {
        return countries.getOrDefault(country.toUpperCase(Locale.ROOT),
                List.of());
    }

    /** @param country ISO code @param name name, alias or ISO subdivision code
     * @return unambiguous region, scoped to the country */
    public Optional<Location> find(String country, String name) {
        if (country == null || name == null) {
            return Optional.empty();
        }
        String key = key(country, name);
        return ambiguous.contains(key) ? Optional.empty()
                : Optional.ofNullable(names.get(key));
    }

    /** SQL identity and configured coordinates win; missing points are filled.
     * @param country ISO code @param sql active SQL CITY rows
     * @return alphabetically sorted union, without canonical duplicates */
    public List<Location> merge(String country, List<Location> sql) {
        Map<String, Location> merged = new LinkedHashMap<>();
        for (Location row : list(country)) {
            merged.put(key(country, row.locationCode()), row);
        }
        for (Location row : sql) {
            Location standard = find(country, row.locationName())
                    .or(() -> find(country, row.locationCode())).orElse(null);
            Location value = enrich(row, standard);
            String key = key(country, standard == null ? row.locationName()
                    : standard.locationCode());
            merged.putIfAbsent(key, value);
            if (standard != null) {
                merged.put(key, value);
            }
        }
        return sorted(new ArrayList<>(merged.values()));
    }

    /** @param row SQL row @return SQL values with missing CITY point filled */
    public Location enrich(Location row) {
        return enrich(row, "CITY".equals(row.locationType())
                ? find(row.countryCode(), row.locationName())
                        .or(() -> find(row.countryCode(), row.locationCode()))
                        .orElse(null) : null);
    }

    private Location enrich(Location row, Location standard) {
        if (standard == null || row.latitude() != null
                && row.longitude() != null) {
            return row;
        }
        return new Location(row.locationId(), row.countryCode(),
                row.locationType(), row.locationName(), row.locationCode(),
                row.address(), standard.latitude(), standard.longitude(),
                row.isPrimaryHub(), row.domesticRatePerKm(), row.isActive());
    }

    private void load(JsonNode row, Set<Integer> ids, boolean include) {
        int id = row.path("id").asInt();
        String country = row.path("countryCode").asText();
        String code = row.path("code").asText();
        if (id <= 0 || !ids.add(id) || !country.matches("[A-Z]{2}")
                || !code.startsWith(country + "-")
                || !row.path("latitude").isNumber()
                || !row.path("longitude").isNumber()
                || row.path("latitude").asDouble() < -90
                || row.path("latitude").asDouble() > 90
                || row.path("longitude").asDouble() < -180
                || row.path("longitude").asDouble() > 180) {
            throw new IllegalStateException("Invalid subdivision row.");
        }
        var location = new Location(-id, country, "CITY",
                row.path("name").asText(), code, null,
                row.path("latitude").decimalValue()
                        .setScale(7, RoundingMode.HALF_UP),
                row.path("longitude").decimalValue()
                        .setScale(7, RoundingMode.HALF_UP), false, true);
        if (include) {
            countries.computeIfAbsent(country, ignored -> new ArrayList<>())
                    .add(location);
        }
        index(country, location.locationName(), location);
        index(country, code, location);
        index(country, code.substring(3), location);
        for (JsonNode alias : row.path("aliases")) {
            index(country, alias.asText(), location);
        }
    }

    private void index(String country, String name, Location value) {
        String key = key(country, name);
        Location old = names.putIfAbsent(key, value);
        if (old != null && old.locationId() != value.locationId()) {
            ambiguous.add(key);
        }
    }

    private List<Location> sorted(List<Location> rows) {
        return rows.stream().sorted(Comparator.comparing(
                (Location row) -> key(row.countryCode(), row.locationName()))
                .thenComparingInt(Location::locationId)).toList();
    }

    private String key(String country, String text) {
        String value = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replace('đ', 'd').strip();
        if ("VN".equalsIgnoreCase(country)) {
            value = value.replaceFirst("^(tp\\.?\\s*|thanh pho\\s+|tinh\\s+)",
                    "");
        }
        return country.toUpperCase(Locale.ROOT) + ":"
                + value.replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
