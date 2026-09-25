package com.asms.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographically secure random codes: group join codes (BR04), invite tokens, email verification tokens.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class RandomCodeGenerator {

    // Excludes easily confused characters: 0/O, 1/I/L
    private static final char[] ALPHANUMERIC = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomCodeGenerator() {}

    public static String alphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC[RANDOM.nextInt(ALPHANUMERIC.length)]);
        }
        return sb.toString();
    }

    /** URL-safe token built from {@code byteLength} random bytes (32 bytes ~ 43 characters). */
    public static String urlSafeToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
