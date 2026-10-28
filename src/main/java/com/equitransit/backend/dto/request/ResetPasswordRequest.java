/*
 * ResetPasswordRequest
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Receive reset input.
 */
package com.equitransit.backend.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.dto.ValidationLimits;
import io.swagger.v3.oas.annotations.media.Schema;


/** Contains a one-time password reset request. */
public class ResetPasswordRequest {
    @NotBlank
    @Size(min = 1, max = ValidationLimits.TOKEN_MAX_LENGTH)
    @Schema(example = "demo_reset_token_from_email_link",
            description = "Replace with the one-time token from your email.",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String token;

    @NotBlank
    @Size(min = ValidationLimits.PASSWORD_MIN_LENGTH,
            max = ValidationLimits.PASSWORD_MAX_LENGTH)
    @Schema(example = "ExamplePass!2026", format = "password",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Documentation example only, not a real password.")
    private String password;

    @NotBlank
    @Size(min = 1, max = ValidationLimits.PASSWORD_MAX_LENGTH)
    @Schema(example = "ExamplePass!2026", format = "password",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Must match password.")
    private String confirmPassword;

    @Email @Size(min = 1, max = ValidationLimits.EMAIL_MAX_LENGTH)
    @Schema(description = "Required when token is a six-digit OTP.",
            example = "customer@example.invalid")
    private String email;

    /** @return OTP owner email; optional for high-entropy email links */
    public String getEmail() {
        return email;
    }

    /** @param value OTP owner email */
    public void setEmail(String value) {
        email = value;
    }

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
