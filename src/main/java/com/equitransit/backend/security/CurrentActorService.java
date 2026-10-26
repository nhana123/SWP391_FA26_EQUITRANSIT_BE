/*
 * CurrentActorService
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: EquiTransit project contributors.
 * Purpose: Resolve an active actor from bearer authentication and profiles.
 */
package com.equitransit.backend.security;


import java.util.Set;
import org.springframework.security.authentication.
        AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.
        authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.assessment.StaffProfile;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.assessment.StaffProfileRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;


/** Resolves domain identities without accepting client-supplied identifiers. */
@Service
@SuppressWarnings("null")
public class CurrentActorService {

    private static final Set<String> STAFF_ROLES = Set.of(
            "MANAGER", "SPECIALIST", "COORDINATOR", "DRIVER");

    private final UserAccountRepository users;
    private final CustomerRepository customers;
    private final StaffProfileRepository staff;

    /**
     * Creates the actor resolver.
     * @param users the account repository
     * @param customers the customer identity repository
     * @param staff the read-only staff identity repository
     */
    public CurrentActorService(UserAccountRepository users,
            CustomerRepository customers, StaffProfileRepository staff) {
        this.users = users;
        this.customers = customers;
        this.staff = staff;
    }

    /**
     * Requires live bearer credentials and the appropriate domain profile.
     * @return the current account and domain identity
     */
    @Transactional(readOnly = true)
    public CurrentActor current() {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        Jwt jwt;
        Long userId;
        UserAccount user;
        String role;
        Customer customer;
        StaffProfile profile;

        if (!(authentication instanceof JwtAuthenticationToken bearer)
                || !authentication.isAuthenticated()) {
            throw unavailable();
        }
        jwt = bearer.getToken();
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException error) {
            throw unavailable();
        }
        user = users.findById(userId).orElseThrow(this::unavailable);
        if (!"ACTIVE".equals(user.getStatus())
                || !"ACCESS".equals(jwt.getClaimAsString("purpose"))
                || !TokenTools.hash(user.getPasswordHash()).equals(
                        jwt.getClaimAsString("credential"))) {
            throw unavailable();
        }
        role = user.getRole().getRoleCode();
        if ("CUSTOMER".equals(role)) {
            customer = customers.findByUserUserId(userId)
                    .orElseThrow(this::unavailable);
            return new CurrentActor(userId, role,
                    customer.getCustomerId(), null);
        }
        if (!STAFF_ROLES.contains(role)) {
            throw unavailable();
        }
        profile = staff.findByUserUserId(userId)
                .orElseThrow(this::unavailable);
        return new CurrentActor(userId, role, null, profile.getStaffId());
    }

    private AuthenticationCredentialsNotFoundException unavailable() {
        return new AuthenticationCredentialsNotFoundException(
                "An active account and domain profile are required.");
    }
}
