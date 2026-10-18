/*
 * OrderException
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement single-horse transport request lifecycle.
 */
package com.equitransit.backend.exception.order;

import com.equitransit.backend.exception.ApiException;



import org.springframework.http.HttpStatus;


/** Safe lifecycle failure with a stable code for frontend handling. */
public class OrderException extends ApiException {

    private final String code;

    /**
     * Creates a lifecycle failure without exposing SQL or owner data.
     * @param status the HTTP status
     * @param code the machine-readable code
     * @param message the safe message
     */
    public OrderException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    /**
     * Returns the domain code.
     * @return the safe code
     */
    public String getCode() {
        return code;
    }
}
