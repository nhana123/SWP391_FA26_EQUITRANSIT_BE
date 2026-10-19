/*
 * LoginResponse
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Return safe response data.
 */
package com.equitransit.backend.dto.auth.response;


import io.swagger.v3.oas.annotations.media.Schema;





/** Contains safe API response data. */
public class LoginResponse {
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMDEifQ."
            + "example-signature", format = "jwt",
            description = "Illustrative token; use the issued access token.")
    private String accessToken;

    @Schema(example = "Bearer", allowableValues = {"Bearer"})
    private String tokenType;

    @Schema(example = "3600", minimum = "1",
            description = "Access-token lifetime in seconds.")
    private long expiresIn;

    @Schema(example = "CUSTOMER", allowableValues = {"CUSTOMER",
            "MANAGER", "SPECIALIST", "COORDINATOR", "DRIVER"})
    private String role;

    /**
     * Returns accessToken.
     * @return the accessToken
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Sets accessToken.
     * @param accessToken the value to store
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * Returns tokenType.
     * @return the tokenType
     */
    public String getTokenType() {
        return tokenType;
    }

    /**
     * Sets tokenType.
     * @param tokenType the value to store
     */
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    /**
     * Returns expiresIn.
     * @return the expiresIn
     */
    public long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Sets expiresIn.
     * @param expiresIn the value to store
     */
    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    /**
     * Returns role.
     * @return the role
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets role.
     * @param role the value to store
     */
    public void setRole(String role) {
        this.role = role;
    }


}
