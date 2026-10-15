/*
 * TokenTools
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Create and hash high-entropy authentication tokens.
 */
package com.equitransit.backend.security;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;


/** Creates random tokens and fingerprints without storing raw tokens. */
public final class TokenTools {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenTools() {
    }

    /**
     * Creates a cryptographically random one-time token.
     * @return the URL-safe token
     */
    public static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];

        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes high-entropy tokens or credential fingerprints.
     * @param value the input, never a raw password
     * @return the SHA-256 hexadecimal hash
     */
    public static String hash(String value) {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }
}
