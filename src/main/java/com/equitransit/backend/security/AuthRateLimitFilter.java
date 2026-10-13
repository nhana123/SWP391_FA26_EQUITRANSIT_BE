/*
 * AuthRateLimitFilter
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Bound authentication request rates on a single server.
 */
package com.equitransit.backend.security;


import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


/** Limits account requests per remote address on this application node. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60000;
    private static final int MAX_ADDRESSES = 4096;

    private final Map<String, ArrayDeque<Long>> attempts =
            new ConcurrentHashMap<>();
    private final Clock clock;
    private final int limit;

    /**
     * Creates the bounded local rate limiter.
     * @param clock the request clock
     * @param limit the maximum requests per minute
     */
    public AuthRateLimitFilter(Clock clock,
            @Value("${app.auth-rate-limit:20}") int limit) {
        this.clock = clock;
        this.limit = limit;
    }

    /**
     * Limits public authentication requests before expensive hashing.
     * @param request the incoming request
     * @param response the outgoing response
     * @param chain the remaining filters
     * @throws ServletException if downstream processing fails
     * @throws IOException if the response cannot be written
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod())
                && request.getRequestURI().startsWith("/api/auth/")
                && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Too many requests. Try again later.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String address) {
        long now = clock.millis();
        ArrayDeque<Long> queue;

        attempts.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                || entry.getValue().peekLast() <= now - WINDOW_MILLIS);
        if (!attempts.containsKey(address)
                && attempts.size() >= MAX_ADDRESSES) {
            return false;
        }
        queue = attempts.computeIfAbsent(address, key -> new ArrayDeque<>());
        while (!queue.isEmpty() && queue.peekFirst() <= now - WINDOW_MILLIS) {
            queue.removeFirst();
        }
        if (queue.size() >= limit) {
            return false;
        }
        queue.addLast(now);
        return true;
    }
}
