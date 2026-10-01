package com.asms.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hashing of secret tokens (refresh, activation, reset) before they are stored or looked up (NFR-AUTH-04).
 * Also used to hash emails in Redis rate-limit keys so that no personal data is stored there.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class TokenHasher {

    private TokenHasher() {}

    /** Lowercase hex SHA-256 of {@code value} (64 characters). */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every Java platform
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
