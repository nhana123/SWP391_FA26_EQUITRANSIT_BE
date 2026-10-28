package com.equitransit.backend.exception;

import org.springframework.http.HttpStatus;

/** Public throttle receipt never reveals whether an account exists. */
public class AuthThrottleException extends ApiException {
    private final long seconds;

    /** @param seconds safe retry delay */
    public AuthThrottleException(long seconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "AUTH_RATE_LIMITED",
                "Too many requests. Please try again later.");
        this.seconds = Math.max(1, seconds);
    }

    /** @return remaining throttle window */
    public long retryAfter() {
        return seconds;
    }
}
