/*
 * AuthenticationToken
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


/** Maps the AuthenticationToken table. */
@Entity
@Table(name = "AuthenticationToken", schema = "dbo")
public class AuthenticationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TokenID", nullable = false)
    private Long tokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserID", nullable = false)
    private UserAccount user;

    @Column(name = "TokenHash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "Purpose", length = 20, nullable = false)
    private String purpose;

    @Column(name = "ExpiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "UsedAt", nullable = true)
    private LocalDateTime usedAt;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Returns tokenId.
     * @return the tokenId
     */
    public Long getTokenId() {
        return tokenId;
    }

    /**
     * Sets tokenId.
     * @param tokenId the value to store
     */
    public void setTokenId(Long tokenId) {
        this.tokenId = tokenId;
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
     * Returns tokenHash.
     * @return the tokenHash
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * Sets tokenHash.
     * @param tokenHash the value to store
     */
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    /**
     * Returns purpose.
     * @return the purpose
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * Sets purpose.
     * @param purpose the value to store
     */
    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    /**
     * Returns expiresAt.
     * @return the expiresAt
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets expiresAt.
     * @param expiresAt the value to store
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Returns usedAt.
     * @return the usedAt
     */
    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    /**
     * Sets usedAt.
     * @param usedAt the value to store
     */
    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
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
