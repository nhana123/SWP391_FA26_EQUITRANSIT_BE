package com.equitransit.backend.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** Private durable delivery metadata; encrypted content is never an API DTO. */
@Entity
@Table(name = "AccountMailOutbox", schema = "dbo")
public class AccountMail {
    @Id
    @Column(name = "MailID", nullable = false)
    UUID id;
    @Column(name = "UserID", nullable = false)
    Long userId;
    @Column(name = "Purpose", nullable = false, length = 32)
    String purpose;
    @Column(name = "ChallengeHash", length = 64)
    String challengeHash;
    @Column(name = "EncryptedPayload", columnDefinition = "nvarchar(max)")
    String payload;
    @Column(name = "Status", nullable = false, length = 20)
    String status;
    @Column(name = "Attempts", nullable = false)
    int attempts;
    @Column(name = "CreatedAt", nullable = false)
    LocalDateTime createdAt;
    @Column(name = "ExpiresAt", nullable = false)
    LocalDateTime expiresAt;
    @Column(name = "AvailableAt", nullable = false)
    LocalDateTime availableAt;
    @Column(name = "LeaseUntil")
    LocalDateTime leaseUntil;
    @Column(name = "LeaseToken")
    UUID leaseToken;
    @Column(name = "CompletedAt")
    LocalDateTime completedAt;
    @Column(name = "LastErrorCode", length = 40)
    String errorCode;
}
