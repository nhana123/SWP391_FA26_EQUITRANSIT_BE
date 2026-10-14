/*
 * LoginRequest
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Receive login input.
 */
package com.equitransit.backend.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import com.equitransit.backend.dto.ValidationLimits;
import io.swagger.v3.oas.annotations.media.Schema;


/** Contains sign-in credentials. */
public class LoginRequest {
    @NotBlank
    @Email
    @Size(min = 1, max = ValidationLimits.EMAIL_MAX_LENGTH)
    @Schema(example = "customer@equitransit.com", format = "email")
    private String email;

    @NotBlank
    @Size(min = 1, max = ValidationLimits.PASSWORD_MAX_LENGTH)
    @Schema(example = "ExamplePass!2026", format = "password",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Documentation example only, not a real password.")
    private String password;

    /**
     * Returns email.
     * @return the email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets email.
     * @param email the value to store
     */
    public void setEmail(String email) {
        this.email = email;
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


}
