/*
 * HorseException
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement customer-owned horse profiles.
 */
package com.equitransit.backend.exception.horse;

import com.equitransit.backend.exception.ApiException;



import org.springframework.http.HttpStatus;


/** Safe domain failure with a stable frontend error code. */
public class HorseException extends ApiException {

    private final String code;

    /**
     * Creates an expected profile failure without persistence details.
     * @param status the HTTP status
     * @param code the machine-readable code
     * @param message the public message
     */
    public HorseException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    /**
     * Returns the frontend error code.
     * @return the stable code
     */
    public String getCode() {
        return code;
    }
}
