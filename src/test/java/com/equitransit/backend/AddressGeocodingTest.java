package com.equitransit.backend;

import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.service.AddressGeocodingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Local fake provider verifies HTTP contracts without querying public OSM. */
public class AddressGeocodingTest {
    private static final String RESULT = """
            [{"display_name":"123 Nguyễn Huệ, Quận 1, Hồ Chí Minh, Việt Nam",
              "lat":"10.773461","lon":"106.704078","address":{
              "house_number":"123","road":"Nguyễn Huệ",
              "city":"Hồ Chí Minh","city_district":"Quận 1",
              "country_code":"vn"}}]
            """;
    private HttpServer server;
    private AddressGeocodingService service;
    private final AtomicReference<String> body = new AtomicReference<>(RESULT);
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> agent = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicLong clock = new AtomicLong(1_000_000);

    /** @throws Exception if the local HTTP fixture cannot start */
    @BeforeEach
    public void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            query.set(exchange.getRequestURI().getRawQuery());
            agent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type",
                    "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        service = new AddressGeocodingService(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/search",
                "EquiTransit-Test/1.0", clock::get);
    }

    /** Release the local fixture. */
    @AfterEach
    public void cleanup() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Coordinates, Unicode, filters, headers and repeated cache hits. */
    @Test
    public void mappedResultsAreCountryScopedAndCached() {
        var results = service.search("Nguyễn Huệ", "vn", 5);
        assertThat(results).hasSize(1);
        var row = results.get(0);
        assertThat(row.latitude()).isEqualByComparingTo("10.773461");
        assertThat(row.countryCode()).isEqualTo("VN");
        assertThat(row.road()).isEqualTo("Nguyễn Huệ");
        assertThat(row.formattedAddress()).startsWith("123 Nguyễn Huệ");
        assertThat(query.get()).contains("countrycodes=vn", "addressdetails=1",
                "format=jsonv2", "limit=5");
        assertThat(agent.get()).isEqualTo("EquiTransit-Test/1.0");
        assertThat(service.search("Nguyễn Huệ", "VN", 5)).isEqualTo(results);
        assertThat(service.validate(row.displayName(), "VN").isValid())
                .isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }

    /** Nonsense and broad-only matches never pass strict validation. */
    @Test
    public void emptyResultsAndUnmatchedTokensAreInvalid() {
        body.set("[]");
        assertThat(service.search("asdfgh", "VN", 5)).isEmpty();
        assertThat(service.validate("asdfgh", "VN").isValid()).isFalse();
        assertThat(calls.get()).isEqualTo(1);
        clock.addAndGet(1000);
        body.set(RESULT);
        assertThat(service.validate("asdfgh Nguyễn Huệ", "VN").isValid())
                .isFalse();
        clock.addAndGet(1000);
        assertThat(service.validate("123 Nguyen Hue", "VN").isValid())
                .isTrue();
        assertThat(service.validate("!!", "VN").isValid()).isFalse();
    }

    /** Different queries share a rate limit; cached queries use no quota. */
    @Test
    public void rateLimitAndCacheExpiry() {
        service.search("Nguyễn Huệ", "VN", 5);
        assertThatThrownBy(() -> service.search("Lexington", "US", 5))
                .isInstanceOf(OrderException.class)
                .satisfies(error -> {
                    var failure = (OrderException) error;
                    assertThat(failure.getCode())
                            .isEqualTo("GEOCODING_RATE_LIMITED");
                });
        clock.addAndGet(3_600_001);
        service.search("Nguyễn Huệ", "VN", 5);
        assertThat(calls.get()).isEqualTo(2);
    }

    /** Provider outages and malformed JSON must not return []/false. */
    @Test
    public void providerFailuresAreUnavailable() {
        status.set(503);
        assertThatThrownBy(() -> service.validate("Nguyễn Huệ", "VN"))
                .isInstanceOf(OrderException.class)
                .satisfies(error -> {
                    var failure = (OrderException) error;
                    assertThat(failure.getCode())
                            .isEqualTo("GEOCODING_UNAVAILABLE");
                });
        clock.addAndGet(1000);
        status.set(200);
        body.set("{\"error\":\"broken\"}");
        assertThatThrownBy(() -> service.search("Nguyễn Huệ", "VN", 5))
                .isInstanceOf(OrderException.class);
    }

    /** Cross-country and malformed coordinates are filtered, inputs bounded. */
    @Test
    public void countryPrecisionAndInputGuards() {
        assertThat(service.search("Nguyễn Huệ", "US", 5)).isEmpty();
        clock.addAndGet(1000);
        body.set(RESULT.replace("10.773461", "190"));
        assertThat(service.search("Nguyễn Huệ", "VN", 5)).isEmpty();
        assertThatThrownBy(() -> service.search("a", "VN", 5))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> service.search("test", "ZZ", 5))
                .isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> service.search("test", "VN", 11))
                .isInstanceOf(OrderException.class);
    }
}

