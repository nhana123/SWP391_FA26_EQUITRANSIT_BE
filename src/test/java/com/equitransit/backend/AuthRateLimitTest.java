/*
 * AuthRateLimitTest
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify abuse protection and recovery after the rate window.
 */
package com.equitransit.backend;


import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.equitransit.backend.security.AuthRateLimitFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/** Checks request limits without sleeping or sending email. */
public class AuthRateLimitTest {

    /**
     * Limits one address, keeps others independent and permits window expiry.
     * @throws Exception if the filter cannot handle a request
     */
    @Test
    public void excessiveAuthRequestsAreBoundedPerAddressAndRecover()
            throws Exception {
        Clock clock = mock(Clock.class);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(clock, 2);
        MockHttpServletResponse blocked;

        when(clock.millis()).thenReturn(0L);
        assertThat(request(filter, "192.0.2.1").getStatus()).isEqualTo(200);
        assertThat(request(filter, "192.0.2.1").getStatus()).isEqualTo(200);
        blocked = request(filter, "192.0.2.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(request(filter, "192.0.2.2").getStatus()).isEqualTo(200);
        when(clock.millis()).thenReturn(60001L);
        assertThat(request(filter, "192.0.2.1").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse request(
            AuthRateLimitFilter filter, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setRemoteAddr(address);
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
