/*
 * StaffProfile
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: EquiTransit project contributors.
 * Purpose: Read the existing staff identity without exposing staff CRUD.
 */
package com.equitransit.backend.entity.assessment;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;
import com.equitransit.backend.entity.auth.UserAccount;


/** Read-only mapping of the existing StaffProfile table for authorization. */
@Entity
@Immutable
@Table(name = "StaffProfile", schema = "dbo")
public class StaffProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "StaffID", nullable = false)
    private Long staffId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserID", nullable = false, unique = true)
    private UserAccount user;

    @Nationalized
    @Column(name = "FullName", length = 150, nullable = false)
    private String fullName;

    @Column(name = "Phone", length = 30)
    private String phone;

    @Column(name = "LicenseNumber", length = 50)
    private String licenseNumber;

    /**
     * Returns the staff identifier, which is not the account identifier.
     * @return the staff primary key
     */
    public Long getStaffId() {
        return staffId;
    }
}
