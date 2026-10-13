/*
 * CustomerResponse
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Return safe response data.
 */
package com.equitransit.backend.dto.response;





/** Contains safe API response data. */
public class CustomerResponse {
    private Long customerId;

    private Long userId;

    private String email;

    private String fullName;

    private String phone;

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
