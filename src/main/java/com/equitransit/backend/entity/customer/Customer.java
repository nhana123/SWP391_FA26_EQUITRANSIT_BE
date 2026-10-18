/*
 * Customer
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Map database account data.
 */
package com.equitransit.backend.entity.customer;

import com.equitransit.backend.entity.auth.UserAccount;



import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import org.hibernate.annotations.Nationalized;


/** Maps the Customer table. */
@Entity
@Table(name = "Customer", schema = "dbo")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CustomerID", nullable = false)
    private Long customerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserID", nullable = false, unique = true)
    private UserAccount user;

    @Nationalized
    @Column(name = "FullName", length = 150, nullable = false)
    private String fullName;

    @Column(name = "Phone", length = 30, nullable = true)
    private String phone;

    @Nationalized
    @Column(name = "Address", length = 500, nullable = true)
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
     * Returns user.
     * @return the user
     */
    public UserAccount getUser() {
        return user;
    }

    /**
     * Sets user.
     * @param user the value to store
     */
    public void setUser(UserAccount user) {
        this.user = user;
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
