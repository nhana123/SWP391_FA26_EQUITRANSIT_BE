package com.equitransit.backend.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Anonymous account/action throttle, shared across application instances. */
@Entity
@Table(name = "AuthRequestBudget", schema = "dbo")
public class AuthRequestBudget implements Persistable<String> {
    @Id
    @Column(name = "BudgetKey", length = 64, nullable = false)
    String key;
    @Column(name = "WindowEnd", nullable = false)
    LocalDateTime until;
    @Column(name = "Requests", nullable = false)
    int requests;
    @Transient
    private boolean newRecord = true;

    /** @return assigned anonymous fingerprint */
    @Override
    public String getId() {
        return key;
    }

    /** Assigned IDs must INSERT, never merge over a concurrent reservation.
     * @return true until first persistence or load */
    @Override
    public boolean isNew() {
        return newRecord;
    }

    @PostLoad
    @PostPersist
    private void persisted() {
        newRecord = false;
    }
}
