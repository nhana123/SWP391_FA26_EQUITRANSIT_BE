/*
 * ResetPasswordRequest
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Receive reset input.
 */
package com.equitransit.backend.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.dto.ValidationLimits;


/** Contains a one-time password reset request. */
public class ResetPasswordRequest {
    @NotBlank
    @Size(max = ValidationLimits.TOKEN_MAX_LENGTH)
    private String token;

    @NotBlank
    @Size(min = ValidationLimits.PASSWORD_MIN_LENGTH,
            max = ValidationLimits.PASSWORD_MAX_LENGTH)
    private String password;

    @NotBlank
    private String confirmPassword;

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

    /**
     * Returns password.
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets password.
     * @param password the value to store
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns confirmPassword.
     * @return the confirmPassword
     */
    public String getConfirmPassword() {
        return confirmPassword;
    }

    /**
     * Sets confirmPassword.
     * @param confirmPassword the value to store
     */
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }


}
