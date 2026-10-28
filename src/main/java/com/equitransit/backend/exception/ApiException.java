/*
 * ApiException
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Represent safe business errors.
 */
package com.equitransit.backend.exception;


import org.springframework.http.HttpStatus;


/** Represents an expected API business failure. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * Creates an expected failure.
     * @param status the HTTP status
     * @param message the safe public message
     */
    public ApiException(HttpStatus status, String message) {
        this(status, null, message);
    }

    /** @param status HTTP status @param code safe code @param message text */
    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /** @return stable public code, when provided */
    public String getCode() {
        return code;
    }

    /**
     * Returns the HTTP status.
     * @return the status
     */
    public HttpStatus getStatus() {
        return status;
    }
}
