/*
 * TokenRequest
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Receive a verification token.
 */
package com.equitransit.backend.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.dto.ValidationLimits;


/** Contains a one-time token. */
public class TokenRequest {
    @NotBlank
    @Size(max = ValidationLimits.TOKEN_MAX_LENGTH)
    private String token;

    /**
     * Returns token.
     * @return the token
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets token.
     * @param token the value to store
     */
    public void setToken(String token) {
        this.token = token;
    }


}
