package com.equitransit.backend.exception;


import org.springframework.http.HttpStatus;


/** Safe compliance failure with a stable domain code. */
public class ComplianceException extends OrderException {

    /**
     * Creates a safe F08 failure.
     * @param status the HTTP status
     * @param code the stable error code
     * @param message the public message
     */
    public ComplianceException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
