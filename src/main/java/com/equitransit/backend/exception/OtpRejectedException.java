package com.equitransit.backend.exception;

import org.springframework.http.HttpStatus;

/** Rejection before credential writes; commit only the failed-attempt count. */
public class OtpRejectedException extends ApiException {
    /** Never expose account existence, remaining attempts or OTP contents. */
    public OtpRejectedException() {
        super(HttpStatus.BAD_REQUEST, "AUTH_CHALLENGE_INVALID",
                "The link is invalid, expired or already used.");
    }
}
