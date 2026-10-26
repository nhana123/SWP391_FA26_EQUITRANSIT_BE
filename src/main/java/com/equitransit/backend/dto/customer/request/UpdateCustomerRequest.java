/*
 * UpdateCustomerRequest
 * Author: EquiTransit development team
 * Date: 2026-10-25
 * Copyright: see repository license.
 * Purpose: Receive customer profile update input.
 */
package com.equitransit.backend.dto.customer.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.equitransit.backend.dto.common.ValidationLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiPatterns;
import com.equitransit.backend.dto.validation.OptionalText;

/** Contains customer profile update input. */
public class UpdateCustomerRequest {

    @NotBlank
    @Size(min = 1, max = ValidationLimits.NAME_MAX_LENGTH)
    @Schema(example = "Tăng Gia Huy", description = "Customer full name")
    private String fullName;

    @OptionalText
    @Pattern(regexp = ApiPatterns.PHONE)
    @Size(min = 1, max = ValidationLimits.PHONE_MAX_LENGTH)
    @Schema(example = "+84901234567", nullable = true,
            pattern = ApiPatterns.PHONE)
    private String phone;

    @OptionalText
    @Size(min = 1, max = ValidationLimits.ADDRESS_MAX_LENGTH)
    @Schema(example = "Ho Chi Minh City, Vietnam", nullable = true)
    private String address;

    /**
     * Returns the full name.
     * @return the customer full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the full name.
     * @param fullName the customer full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns the phone.
     * @return the contact phone
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the phone.
     * @param phone the contact phone
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Returns the address.
     * @return the residential address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the address.
     * @param address the residential address
     */
    public void setAddress(String address) {
        this.address = address;
    }
}
