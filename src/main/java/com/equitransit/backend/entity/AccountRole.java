/*
 * AccountRole
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Map database account data.
 */
package com.equitransit.backend.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import org.hibernate.annotations.Nationalized;


/** Maps the Role table. */
@Entity
@Table(name = "Role", schema = "dbo")
public class AccountRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoleID", nullable = false)
    private Long roleId;

    @Column(name = "RoleCode", length = 30, nullable = false, unique = true)
    private String roleCode;

    @Nationalized
    @Column(name = "Description", length = 255, nullable = true)
    private String description;

    /**
     * Returns roleId.
     * @return the roleId
     */
    public Long getRoleId() {
        return roleId;
    }

    /**
     * Sets roleId.
     * @param roleId the value to store
     */
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    /**
     * Returns roleCode.
     * @return the roleCode
     */
    public String getRoleCode() {
        return roleCode;
    }

    /**
     * Sets roleCode.
     * @param roleCode the value to store
     */
    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    /**
     * Returns description.
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets description.
     * @param description the value to store
     */
    public void setDescription(String description) {
        this.description = description;
    }


}
