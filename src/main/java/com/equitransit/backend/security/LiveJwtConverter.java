/*
 * LiveJwtConverter
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Reject stale credentials and enforce current account roles.
 */
package com.equitransit.backend.security;


import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.
        JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.repository.auth.UserAccountRepository;


/** Rechecks account status, role and credentials on protected requests. */
@Component
public class LiveJwtConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAccountRepository users;

    /**
     * Creates the account-aware token converter.
     * @param users the account repository
     */
    public LiveJwtConverter(UserAccountRepository users) {
        this.users = users;
    }

    /**
     * Rejects tokens for locked accounts or changed passwords.
     * @param jwt the cryptographically validated access token
     * @return authentication based on the current account role
     */
    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long id;
        UserAccount user;

        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException error) {
            throw new OAuth2AuthenticationException("invalid_token");
        }
        user = users.findById(id).orElseThrow(() ->
                new OAuth2AuthenticationException("invalid_token"));
        if (!"ACTIVE".equals(user.getStatus())
                || !"ACCESS".equals(jwt.getClaimAsString("purpose"))
                || !TokenTools.hash(user.getPasswordHash()).equals(
                        jwt.getClaimAsString("credential"))) {
            throw new OAuth2AuthenticationException("invalid_token");
        }
        return new JwtAuthenticationToken(jwt, List.of(
                new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole().getRoleCode())));
    }
}
