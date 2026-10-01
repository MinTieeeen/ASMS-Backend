package com.asms.util;

import java.util.Locale;

/**
 * Normalizes emails before they are stored or compared: trimmed and lowercase (BR-AUTH-02, FR-AUTH-01).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class EmailNormalizer {

    private EmailNormalizer() {}

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
