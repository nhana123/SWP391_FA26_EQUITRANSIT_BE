/*
 * CustomerResponse
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Return safe response data.
 */
package com.equitransit.backend.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.ApiPatterns;





/** Contains safe API response data. */
public class CustomerResponse {
    @Schema(example = "12", minimum = "1")
    private Long customerId;

    @Schema(example = "101", minimum = "1")
    private Long userId;

    @Schema(example = "customer@equitransit.com", format = "email")
    private String email;

    @Schema(example = "Alex Nguyen")
    private String fullName;

    @Schema(example = "+84901234567", nullable = true,
            pattern = ApiPatterns.PHONE)
    private String phone;

    @Schema(example = "12 Nguyen Hue, Ho Chi Minh City",
            nullable = true)
    private String address;

    /**
     * Returns customerId.
     * @return the customerId
     */
    public Long getCustomerId() {
        return customerId;
    }

    /**
     * Sets customerId.
     * @param customerId the value to store
     */
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    /**
     * Returns userId.
     * @return the userId
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets userId.
     * @param userId the value to store
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

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
