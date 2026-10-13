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
import jakarta.validation.constraints.Pattern;
import com.equitransit.backend.dto.ValidationLimits;


/** Contains sign-in credentials. */
public class LoginRequest {
    @NotBlank
    @Email
    @Size(max = ValidationLimits.EMAIL_MAX_LENGTH)
    @Pattern(regexp = "[\\x00-\\x7F]+")
    private String email;

    @NotBlank
    @Size(max = ValidationLimits.PASSWORD_MAX_LENGTH)
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
