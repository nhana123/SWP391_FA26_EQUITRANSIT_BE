package com.equitransit.backend.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Anonymous account/action throttle, shared across application instances. */
@Entity
@Table(name = "AuthRequestBudget", schema = "dbo")
public class AuthRequestBudget {
    @Id
    @Column(name = "BudgetKey", length = 64, nullable = false)
    String key;
    @Column(name = "WindowEnd", nullable = false)
    LocalDateTime until;
    @Column(name = "Requests", nullable = false)
    int requests;
}
