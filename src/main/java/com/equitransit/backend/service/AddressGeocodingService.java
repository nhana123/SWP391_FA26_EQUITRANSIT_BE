package com.equitransit.backend.service;

import com.equitransit.backend.dto.route.AddressSuggestion;
import com.equitransit.backend.dto.route.AddressValidationResponse;
import com.equitransit.backend.exception.order.OrderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Explicit end-user searches only on the public provider, never keystrokes. */
@Service
public class AddressGeocodingService {
    private static final long TTL_MILLIS = 3_600_000;
    private final ObjectMapper json;
    private final HttpClient http;
    private final String endpoint;
    private final String userAgent;
    private final LongSupplier clock;
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private long nextRequest;

    private record Cached(long expiresAt, List<AddressSuggestion> rows) { }

    /** @param json JSON @param endpoint switchable Nominatim search URL
     * @param userAgent application identification */
    @Autowired
    public AddressGeocodingService(ObjectMapper json,
            @Value("${app.geocoding.search-url:"
                    + "https://nominatim.openstreetmap.org/search}")
            String endpoint,
            @Value("${app.geocoding.user-agent:EquiTransit-Address/1.0}")
            String userAgent) {
        this(json, endpoint, userAgent, System::currentTimeMillis);
    }

    /** @param json JSON @param endpoint configured provider URL
     * @param userAgent identification @param clock cache/rate-limit clock */
    public AddressGeocodingService(ObjectMapper json, String endpoint,
            String userAgent, LongSupplier clock) {
        URI provider = URI.create(endpoint);
        if (!List.of("http", "https").contains(provider.getScheme())
                || provider.getHost() == null || provider.getQuery() != null
                || provider.getFragment() != null || userAgent.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid geocoder configuration.");
        }
        this.json = json;
        this.endpoint = endpoint;
        this.userAgent = userAgent;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Cached and serialized to enforce <=1 outbound request/second per JVM.
     * @param query user-triggered search @param country optional country
     * @param limit requested count @return real provider matches, or empty */
    public List<AddressSuggestion> search(String query,
            String country, int limit) {
        if (!lock.tryLock()) {
            throw new OrderException(HttpStatus.TOO_MANY_REQUESTS,
                    "GEOCODING_RATE_LIMITED", "A search is in progress.");
        }
        try {
            return searchLocked(query, country, limit);
        } finally {
            lock.unlock();
        }
    }

    private List<AddressSuggestion> searchLocked(String query, String country,
            int limit) {
        String clean = query == null ? "" : query.strip()
                .replaceAll("\\s+", " ");
        if (clean.length() < 2 || clean.length() > 500
                || limit < 1 || limit > 10) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "INVALID_ADDRESS_SEARCH",
                    "Use 2-500 chars and limit 1-10.");
        }
        String iso = country(country);
        if (normalize(clean).isBlank()) {
            return List.of();
        }
        String key = clean.toLowerCase(Locale.ROOT) + "|" + iso + "|" + limit;
        long now = clock.getAsLong();
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt() > now) {
            return hit.rows();
        }
        if (now < nextRequest) {
            throw new OrderException(HttpStatus.TOO_MANY_REQUESTS,
                    "GEOCODING_RATE_LIMITED", "Retry after one second.");
        }
        nextRequest = now + 1000;
        List<AddressSuggestion> rows = fetch(clean, iso, limit);
        cache.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
        remember(key, rows, now);
        for (AddressSuggestion row : rows) {
            String label = row.displayName().strip().replaceAll("\\s+", " ")
                    .toLowerCase(Locale.ROOT);
            if (label.length() <= 500) {
                remember(label + "|" + iso + "|5", List.of(row), now);
                remember(label + "|" + row.countryCode() + "|5",
                        List.of(row), now);
            }
        }
        return rows;
    }

    private void remember(String key, List<AddressSuggestion> rows, long now) {
        if (cache.containsKey(key)) {
            return;
        }
        if (cache.size() >= 1000) {
            cache.remove(cache.keySet().iterator().next());
        }
        cache.put(key, new Cached(now + TTL_MILLIS, rows));
    }

    /** No city/known-coordinate fallback is permitted for validation.
     * @param address full address @param country optional ISO
     * @return false when no candidate contains the meaningful input tokens */
    public AddressValidationResponse validate(String address, String country) {
        String normalized = normalize(address == null ? "" : address);
        var found = search(address, country, 5).stream()
                .filter(row -> Arrays.stream(normalized.split(" "))
                        .filter(token -> !token.isBlank())
                        .allMatch(token -> (" " + normalize(row.displayName())
                                + " ").contains(" " + token + " ")))
                .findFirst();
        return new AddressValidationResponse(found.isPresent(),
                found.orElse(null), found.isPresent()
                        ? "Address matched by geocoder."
                        : "No matching address found; select a search result.");
    }

    private List<AddressSuggestion> fetch(String query, String country,
            int limit) {
        String url = endpoint + "?q=" + URLEncoder.encode(query,
                StandardCharsets.UTF_8) + "&format=jsonv2&addressdetails=1"
                + "&dedupe=1&limit=" + limit + (country == null ? ""
                        : "&countrycodes=" + country.toLowerCase(Locale.ROOT));
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4)).header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Accept-Language", "vi,en").GET().build();
        try {
            var response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw unavailable();
            }
            JsonNode root = json.readTree(response.body());
            if (root == null || !root.isArray()) {
                throw unavailable();
            }
            List<AddressSuggestion> rows = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode row : root) {
                AddressSuggestion item = suggestion(row, country);
                if (item != null && seen.add(item.displayName())) {
                    rows.add(item);
                }
                if (rows.size() == limit) {
                    break;
                }
            }
            return List.copyOf(rows);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | IllegalArgumentException error) {
            throw unavailable();
        }
    }

    private AddressSuggestion suggestion(JsonNode row, String country) {
        JsonNode address = row.path("address");
        String iso = text(address, "country_code");
        String display = text(row, "display_name");
        if (iso == null || display == null || !iso.matches("[A-Za-z]{2}")) {
            return null;
        }
        iso = iso.toUpperCase(Locale.ROOT);
        if (country != null && !country.equals(iso)) {
            return null;
        }
        try {
            BigDecimal lat = new BigDecimal(row.path("lat").asText());
            BigDecimal lon = new BigDecimal(row.path("lon").asText());
            if (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0
                    || lon.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
                return null;
            }
            String city = text(address, "city", "town", "village", "state");
            String district = text(address, "city_district", "district",
                    "county", "suburb");
            String road = text(address, "road", "pedestrian", "residential");
            Set<String> parts = new LinkedHashSet<>();
            String street = String.join(" ", text(address, "house_number")
                    == null ? "" : text(address, "house_number"),
                    road == null ? "" : road).strip();
            if (!street.isBlank()) {
                parts.add(street);
            }
            String ward = text(address, "neighbourhood", "quarter", "suburb");
            for (String value : new String[]{ward, district, city}) {
                if (value != null) {
                    parts.add(value);
                }
            }
            return new AddressSuggestion(display, parts.isEmpty() ? display
                    : String.join(", ", parts), lat, lon, iso, city, district,
                    road);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private String text(JsonNode row, String... fields) {
        for (String field : fields) {
            String value = row.path(field).asText("").strip();
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String country(String value) {
        if (value == null) {
            return null;
        }
        String iso = value.toUpperCase(Locale.ROOT);
        if (!Set.of(Locale.getISOCountries()).contains(iso)) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "INVALID_COUNTRY_CODE", "Use a valid ISO country code.");
        }
        return iso;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replace('đ', 'd').replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private OrderException unavailable() {
        return new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                "GEOCODING_UNAVAILABLE", "Address provider is unavailable.");
    }
}
