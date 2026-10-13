/*
 * UserAccount
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
import java.time.LocalDateTime;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;


/** Maps the `User` table. */
@Entity
@Table(name = "`User`", schema = "dbo")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserID", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RoleID", nullable = false)
    private AccountRole role;

    @Column(name = "Email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "PasswordHash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "Status", length = 20, nullable = false)
    private String status;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

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
     * Returns role.
     * @return the role
     */
    public AccountRole getRole() {
        return role;
    }

    /**
     * Sets role.
     * @param role the value to store
     */
    public void setRole(AccountRole role) {
        this.role = role;
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
     * Returns passwordHash.
     * @return the passwordHash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Sets passwordHash.
     * @param passwordHash the value to store
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Returns status.
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets status.
     * @param status the value to store
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns createdAt.
     * @return the createdAt
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets createdAt.
     * @param createdAt the value to store
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }


}
