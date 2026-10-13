/*
 * EmailRequest
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Receive an email address.
 */
package com.equitransit.backend.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.equitransit.backend.dto.ValidationLimits;


/** Contains a verification or recovery email. */
public class EmailRequest {
    @NotBlank
    @Email
    @Size(max = ValidationLimits.EMAIL_MAX_LENGTH)
    @Pattern(regexp = "[\\x00-\\x7F]+")
    private String email;

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


}
