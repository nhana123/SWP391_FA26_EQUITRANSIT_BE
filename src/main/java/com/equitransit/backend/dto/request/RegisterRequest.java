/*
 * RegisterRequest
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Receive registration input.
 */
package com.equitransit.backend.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.equitransit.backend.dto.ValidationLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiPatterns;
import com.equitransit.backend.dto.validation.OptionalText;


/** Contains customer registration input. */
public class RegisterRequest {
    @NotBlank
    @Email
    @Size(max = ValidationLimits.EMAIL_MAX_LENGTH)
    @Schema(example = "customer@equitransit.com", format = "email")
    private String email;

    @NotBlank
    @Size(min = ValidationLimits.PASSWORD_MIN_LENGTH,
            max = ValidationLimits.PASSWORD_MAX_LENGTH)
    @Schema(example = "ExamplePass!2026", format = "password",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Documentation example only, not a real password.")
    private String password;

    @NotBlank
    @Size(max = ValidationLimits.PASSWORD_MAX_LENGTH)
    @Schema(example = "ExamplePass!2026", format = "password",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Must match password.")
    private String confirmPassword;

    @NotBlank
    @Size(max = ValidationLimits.NAME_MAX_LENGTH)
    @Schema(example = "Alex Nguyen")
    private String fullName;

    @OptionalText
    @Pattern(regexp = ApiPatterns.PHONE)
    @Size(max = ValidationLimits.PHONE_MAX_LENGTH)
    @Schema(example = "+84901234567", nullable = true,
            pattern = ApiPatterns.PHONE)
    private String phone;

    @OptionalText
    @Size(max = ValidationLimits.ADDRESS_MAX_LENGTH)
    @Schema(example = "12 Nguyen Hue, Ho Chi Minh City",
            nullable = true)
    private String address;

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

    /**
     * Returns fullName.
     * @return the fullName
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets fullName.
     * @param fullName the value to store
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns phone.
     * @return the phone
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets phone.
     * @param phone the value to store
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Returns address.
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets address.
     * @param address the value to store
     */
    public void setAddress(String address) {
        this.address = address;
    }


}
