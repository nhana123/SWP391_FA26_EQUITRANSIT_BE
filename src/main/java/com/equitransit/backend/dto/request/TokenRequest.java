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
import io.swagger.v3.oas.annotations.media.Schema;


/** Contains a one-time token. */
public class TokenRequest {
    @NotBlank
    @Size(max = ValidationLimits.TOKEN_MAX_LENGTH)
    @Schema(example = "demo_verification_token_from_email_link",
            description = "Replace with the one-time token from your email.",
            accessMode = Schema.AccessMode.WRITE_ONLY)
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
