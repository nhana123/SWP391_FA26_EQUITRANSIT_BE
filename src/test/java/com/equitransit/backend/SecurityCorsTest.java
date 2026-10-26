/*
 * SecurityCorsTest
 * Author: EquiTransit development team
 * Date: 2026-10-22
 * Purpose: Verify credentialed login CORS and origin restrictions.
 */
package com.equitransit.backend;


import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;
import com.equitransit.backend.config.SecurityConfig;
import static org.assertj.core.api.Assertions.assertThat;


/** Exercises browser preflight and actual requests through the CORS filter. */
@SuppressWarnings("null")
class SecurityCorsTest {
    private static final String ORIGIN = "http://localhost:5173";

    private final CorsFilter filter = new CorsFilter(new SecurityConfig()
            .corsConfigurationSource(ORIGIN));

    @Test
    void loginPreflightAllowsCredentials() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/api/auth/login");
        request.addHeader("Origin", ORIGIN);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin"))
                .isEqualTo(ORIGIN);
        assertThat(response.getHeader("Access-Control-Allow-Credentials"))
                .isEqualTo("true");
    }

    @Test
    void actualLoginResponseAllowsCookiesEvenOnAuthenticationFailure()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/auth/login");
        request.addHeader("Origin", ORIGIN);
        request.setCookies(new Cookie("refresh", "synthetic-test-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedEndpoint = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> {
            reachedEndpoint.set(true);
            response.setStatus(401);
        });
        assertThat(reachedEndpoint).isTrue();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Access-Control-Allow-Origin"))
                .isEqualTo(ORIGIN);
        assertThat(response.getHeader("Access-Control-Allow-Credentials"))
                .isEqualTo("true");
    }

    @Test
    void unconfiguredOriginIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/api/auth/login");
        request.addHeader("Origin", "http://untrusted.example");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(response.getHeader("Access-Control-Allow-Credentials"))
                .isNull();
    }
}
