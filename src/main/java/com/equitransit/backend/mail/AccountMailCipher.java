package com.equitransit.backend.mail;

import com.equitransit.backend.security.TokenTools;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-GCM protects queued OTP/link bodies and binds ciphertext to MailID. */
@Component
public class AccountMailCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    /** @param secret external key material, not a value from the database */
    public AccountMailCipher(@Value("${app.mail.outbox-secret:"
            + "${app.jwt.secret}}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Mail encryption key too short.");
        }
        key = new SecretKeySpec(HexFormat.of().parseHex(
                TokenTools.hash("account-mail:v1:" + secret)), "AES");
    }

    /** @param id row identity @param text private JSON @return ciphertext */
    public String seal(UUID id, String text) {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, id, nonce,
                text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(
                1 + nonce.length + encrypted.length).put((byte) 1)
                .put(nonce).put(encrypted).array());
    }

    /** @param id row identity @param encoded envelope @return private JSON */
    public String open(UUID id, String encoded) {
        try {
            byte[] envelope = Base64.getDecoder().decode(encoded);
            if (envelope.length < 29 || envelope[0] != 1) {
                throw new IllegalArgumentException("Invalid mail envelope.");
            }
            byte[] nonce = java.util.Arrays.copyOfRange(envelope, 1, 13);
            byte[] bytes = java.util.Arrays.copyOfRange(envelope, 13,
                    envelope.length);
            return new String(crypt(Cipher.DECRYPT_MODE, id, nonce, bytes),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Mail payload unavailable.");
        }
    }

    private byte[] crypt(int mode, UUID id, byte[] nonce, byte[] bytes) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(id.toString().getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(bytes);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Mail payload unavailable.");
        }
    }
}
