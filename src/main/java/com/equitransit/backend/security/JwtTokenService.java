/*
 * JwtTokenService
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Issue signed short-lived access credentials.
 */
package com.equitransit.backend.security;


import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import com.equitransit.backend.dto.response.LoginResponse;
import com.equitransit.backend.entity.UserAccount;


/** Issues short-lived access tokens for authenticated accounts. */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final long ttlSeconds;

    /**
     * Creates the token issuer.
     * @param encoder the configured signing encoder
     * @param clock the UTC clock
     * @param issuer the expected issuer
     * @param ttlSeconds the access-token lifetime
     */
    public JwtTokenService(JwtEncoder encoder, Clock clock,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.ttl-seconds}") long ttlSeconds) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Issues a token bound to the current password hash.
     * @param user the authenticated account
     * @return the safe sign-in response
     */
    public LoginResponse issue(UserAccount user) {
        return issue(user.getUserId(), user.getPasswordHash(),
                user.getRole().getRoleCode());
    }

    /** @param user account key @param hash current password hash
     * @param role current role @return short-lived access credential */
    public LoginResponse issue(long user, String hash, String role) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(Long.toString(user))
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .claim("credential", TokenTools.hash(hash))
                .claim("purpose", "ACCESS")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(
                JwtEncoderParameters.from(header, claims)).getTokenValue();
        LoginResponse response = new LoginResponse();

        response.setAccessToken(value);
        response.setTokenType("Bearer");
        response.setExpiresIn(ttlSeconds);
        response.setRole(role);
        return response;
    }
}
