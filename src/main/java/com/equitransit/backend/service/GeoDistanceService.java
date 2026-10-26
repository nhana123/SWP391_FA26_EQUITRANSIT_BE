package com.equitransit.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 3-tier resilient geocoding and road routing engine:
 * 1. OpenStreetMap Nominatim (Geocoding)
 * 2. OSRM Driving Route API (Road Distance & Hours)
 * 3. Deterministic Coordinate Matrix + Haversine x 1.3 fallback
 */
@Service
public class GeoDistanceService {
    private static final String USER_AGENT = "EquiTransit-SWP391/1.0";
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double ROAD_WINDING_FACTOR = 1.30;
    private static final double AVG_TRUCK_SPEED_KMH = 50.0;
    private static final Map<String, Coordinates> KNOWN_COORDINATES =
            createKnownCoordinates();

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Map<String, Coordinates> geocodeCache =
            new ConcurrentHashMap<>();
    private final Map<String, RoadLeg> routeCache =
            new ConcurrentHashMap<>();
    private volatile boolean externalNetworkEnabled = true;
    private SubdivisionCatalog subdivisions;
    private AddressGeocodingService addressGeocoder;

    /** Creates the default HTTP-backed distance service. */
    public GeoDistanceService() {
        this(new ObjectMapper(), true);
    }

    /**
     * Constructor with configurable network toggle for isolated unit tests.
     * @param mapper JSON parser
     * @param externalNetworkEnabled true to query Nominatim/OSRM first
     */
    public GeoDistanceService(ObjectMapper mapper,
            boolean externalNetworkEnabled) {
        this.mapper = mapper;
        this.externalNetworkEnabled = externalNetworkEnabled;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2000))
                .build();
    }

    /** @param subdivisions offline country-scoped region coordinates */
    @Autowired
    public void configureSubdivisions(SubdivisionCatalog subdivisions) {
        this.subdivisions = subdivisions;
    }

    /** @param geocoder shared rate-limited Nominatim gateway */
    @Autowired
    public void configureAddressGeocoder(AddressGeocodingService geocoder) {
        addressGeocoder = geocoder;
    }

    /**
     * Enables or disables live external Nominatim/OSRM HTTP calls.
     * @param enabled true to call live APIs, false to use deterministic math
     */
    public void setExternalNetworkEnabled(boolean enabled) {
        this.externalNetworkEnabled = enabled;
    }

    /**
     * Geographic coordinate pair.
     * @param latitude decimal latitude
     * @param longitude decimal longitude
     */
    public record Coordinates(double latitude, double longitude) { }

    /**
     * Computed road segment distance and duration.
     * @param distanceKm road distance in km
     * @param durationHours driving duration in hours
     */
    public record RoadLeg(BigDecimal distanceKm, BigDecimal durationHours) { }

    /**
     * Resolves coordinates for an address, city, or country code.
     * @param address optional street/district address
     * @param city optional city or airport name
     * @param countryCode ISO-2 country code
     * @param catalogCoords optional coordinates from LocationCatalog DB
     * @return resolved coordinates if found
     */
    public Optional<Coordinates> resolveCoordinates(String address,
            String city, String countryCode, Coordinates catalogCoords) {
        String cleanAddr = clean(address);
        String cleanCity = clean(city);
        String cleanCountry = clean(countryCode);

        if (catalogCoords == null && subdivisions != null && cleanCity != null
                && cleanCountry != null) {
            catalogCoords = subdivisions.find(cleanCountry, cleanCity)
                    .map(row -> new Coordinates(row.latitude().doubleValue(),
                            row.longitude().doubleValue())).orElse(null);
        }

        if (cleanAddr != null) {
            if (externalNetworkEnabled) {
                String query = cleanCity != null
                        && !cleanAddr.toLowerCase(Locale.ROOT).contains(
                                cleanCity.toLowerCase(Locale.ROOT))
                        ? cleanAddr + ", " + cleanCity : cleanAddr;
                Optional<Coordinates> geocoded = geocodeNominatim(
                        query, cleanCountry);
                if (geocoded.isPresent()) {
                    return geocoded;
                }
            }
            Optional<Coordinates> known = matchKnown(cleanAddr, cleanCountry);
            if (known.isPresent() && (catalogCoords == null
                    || KNOWN_COORDINATES.containsKey(
                            normalizeKey(cleanAddr)))) {
                return known;
            }
        }
        if (catalogCoords != null) {
            return Optional.of(catalogCoords);
        }
        if (cleanCity != null) {
            Optional<Coordinates> knownCity = matchKnown(
                    cleanCity, cleanCountry);
            if (knownCity.isPresent()) {
                return knownCity;
            }
            if (externalNetworkEnabled) {
                Optional<Coordinates> geocodedCity = geocodeNominatim(
                        cleanCity, cleanCountry);
                if (geocodedCity.isPresent()) {
                    return geocodedCity;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Computes road distance and driving hours between two coordinates.
     * Queries OSRM first when network is enabled, falling back to Haversine.
     * @param from origin coordinates
     * @param to destination coordinates
     * @return road distance (km) and duration (hours)
     */
    public RoadLeg calculateRoadLeg(Coordinates from, Coordinates to) {
        if (from == null || to == null) {
            return new RoadLeg(BigDecimal.ZERO.setScale(2,
                    RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
        }
        double haversineKm = haversineKm(from.latitude(), from.longitude(),
                to.latitude(), to.longitude());
        if (haversineKm < 0.25) {
            return new RoadLeg(BigDecimal.ZERO.setScale(2,
                    RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
        }
        String key = String.format(Locale.ROOT, "%.4f,%.4f->%.4f,%.4f",
                from.latitude(), from.longitude(),
                to.latitude(), to.longitude());
        RoadLeg cached = routeCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (externalNetworkEnabled) {
            Optional<RoadLeg> osrm = queryOsrm(from, to);
            if (osrm.isPresent()) {
                routeCache.put(key, osrm.get());
                return osrm.get();
            }
        }
        RoadLeg fallback = fallbackHaversineLeg(haversineKm);
        if (externalNetworkEnabled) {
            routeCache.put(key, fallback);
        }
        return fallback;
    }

    /**
     * Deterministic Haversine road approximation (Haversine * 1.30).
     * @param straightKm great-circle distance in km
     * @return road leg
     */
    public RoadLeg fallbackHaversineLeg(double straightKm) {
        double roadKm = Math.max(0.0, straightKm * ROAD_WINDING_FACTOR);
        double hours = roadKm > 0
                ? Math.max(0.2, roadKm / AVG_TRUCK_SPEED_KMH) : 0.0;
        return new RoadLeg(
                BigDecimal.valueOf(roadKm).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(hours).setScale(1, RoundingMode.HALF_UP));
    }

    private Optional<Coordinates> geocodeNominatim(String query,
            String countryCode) {
        if (addressGeocoder != null) {
            try {
                return addressGeocoder.search(query, countryCode, 1).stream()
                        .findFirst().map(row -> new Coordinates(
                                row.latitude().doubleValue(),
                                row.longitude().doubleValue()));
            } catch (com.equitransit.backend.exception.OrderException error) {
                return Optional.empty();
            }
        }
        String cacheKey = (countryCode == null ? "" : countryCode)
                + ":" + query.toLowerCase(Locale.ROOT);
        Coordinates hit = geocodeCache.get(cacheKey);
        if (hit != null) {
            return Optional.of(hit);
        }
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String cc = countryCode == null ? ""
                    : "&countrycodes="
                    + countryCode.toLowerCase(Locale.ROOT);
            String url = "https://nominatim.openstreetmap.org/search?q="
                    + encoded + cc + "&format=json&limit=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(2500))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode first = root.get(0);
                    double lat = Double.parseDouble(
                            first.path("lat").asText());
                    double lon = Double.parseDouble(
                            first.path("lon").asText());
                    Coordinates coords = new Coordinates(lat, lon);
                    geocodeCache.put(cacheKey, coords);
                    return Optional.of(coords);
                }
            }
        } catch (Exception ignored) {
            // Gracefully fall back to catalog/known coordinates
        }
        return Optional.empty();
    }

    private Optional<RoadLeg> queryOsrm(Coordinates from, Coordinates to) {
        try {
            String url = String.format(Locale.ROOT,
                    "https://router.project-osrm.org/route/v1/driving/"
                    + "%.6f,%.6f;%.6f,%.6f?overview=false",
                    from.longitude(), from.latitude(),
                    to.longitude(), to.latitude());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(2500))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                JsonNode routes = root.path("routes");
                if ("Ok".equalsIgnoreCase(root.path("code").asText())
                        && routes.isArray() && !routes.isEmpty()) {
                    JsonNode r0 = routes.get(0);
                    double meters = r0.path("distance").asDouble(0.0);
                    double seconds = r0.path("duration").asDouble(0.0);
                    if (meters > 0) {
                        BigDecimal km = BigDecimal.valueOf(meters / 1000.0)
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal hours = BigDecimal.valueOf(
                                Math.max(0.2, seconds / 3600.0))
                                .setScale(1, RoundingMode.HALF_UP);
                        return Optional.of(new RoadLeg(km, hours));
                    }
                }
            }
        } catch (Exception ignored) {
            // Gracefully fall back to Haversine
        }
        return Optional.empty();
    }

    private Optional<Coordinates> matchKnown(String text, String countryCode) {
        String normalized = normalizeKey(text);
        Coordinates exact = KNOWN_COORDINATES.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        for (Map.Entry<String, Coordinates> entry
                : KNOWN_COORDINATES.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private double haversineKm(double lat1, double lon1,
            double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String normalizeKey(String value) {
        return value == null ? ""
                : value.strip().toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ");
    }

    private static Map<String, Coordinates> createKnownCoordinates() {
        Map<String, Coordinates> map = new ConcurrentHashMap<>();
        // Vietnam cities & districts
        map.put("tp. hồ chí minh", new Coordinates(10.7769, 106.7009));
        map.put("hồ chí minh", new Coordinates(10.7769, 106.7009));
        map.put("ho chi minh", new Coordinates(10.7769, 106.7009));
        map.put("quận 1", new Coordinates(10.7754, 106.6996));
        map.put("quan 1", new Coordinates(10.7754, 106.6996));
        map.put("củ chi", new Coordinates(11.0067, 106.5132));
        map.put("cu chi", new Coordinates(11.0067, 106.5132));
        map.put("hà nội", new Coordinates(21.0285, 105.8542));
        map.put("ha noi", new Coordinates(21.0285, 105.8542));
        map.put("lạng sơn", new Coordinates(21.8537, 106.7615));
        map.put("lang son", new Coordinates(21.8537, 106.7615));
        map.put("đà nẵng", new Coordinates(16.0544, 108.2022));
        map.put("da nang", new Coordinates(16.0544, 108.2022));
        map.put("tây ninh", new Coordinates(11.3100, 106.0983));
        map.put("quảng trị", new Coordinates(16.7500, 107.2000));
        map.put("bình dương", new Coordinates(10.9804, 106.6519));
        map.put("đồng nai", new Coordinates(10.9574, 106.8427));
        // China
        map.put("nam ninh (nanning)", new Coordinates(22.8170, 108.3665));
        map.put("nam ninh", new Coordinates(22.8170, 108.3665));
        map.put("nanning", new Coordinates(22.8170, 108.3665));
        map.put("bằng tường (pingxiang)", new Coordinates(22.0967, 106.7550));
        map.put("bằng tường", new Coordinates(22.0967, 106.7550));
        map.put("pingxiang", new Coordinates(22.0967, 106.7550));
        map.put("quảng châu", new Coordinates(23.1291, 113.2644));
        map.put("guangzhou", new Coordinates(23.1291, 113.2644));
        // ASEAN & International
        map.put("vientiane", new Coordinates(17.9757, 102.6331));
        map.put("phnom penh", new Coordinates(11.5564, 104.9282));
        map.put("bangkok", new Coordinates(13.7563, 100.5018));
        map.put("singapore", new Coordinates(1.3521, 103.8198));
        map.put("kuala lumpur", new Coordinates(3.1390, 101.6869));
        map.put("paris", new Coordinates(48.8566, 2.3522));
        return map;
    }
}
