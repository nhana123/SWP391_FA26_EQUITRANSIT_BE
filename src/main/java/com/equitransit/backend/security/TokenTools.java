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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


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

    /** Prevents offline OTP guessing without the server-side secret.
     * @param email normalized owner @param purpose flow @param otp code
     * @param secret server-side pepper @return HMAC-SHA256 fingerprint */
    public static String otpHash(String email, String purpose, String otp,
            String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose + "\n"
                    + email + "\n" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("OTP protection unavailable.",
                    error);
        }
    }

    /** @param expected stored hash @param actual candidate hash
     * @return constant-time comparison for equal-length fingerprints */
    public static boolean equalHashes(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Generates a random 6-digit one-time password (OTP).
     * @return 6-digit numeric string
     */
    public static String randomOtp() {
        int number = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(number);
    }
}
