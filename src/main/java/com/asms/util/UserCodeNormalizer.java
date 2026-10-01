package com.asms.util;

import java.util.Locale;

/**
 * Normalizes user IDs (user_code), the sign-in identifier, before they are stored or compared: trimmed and uppercase,
 * so that "se170001" and "SE170001" are the same account.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-01
 * @modified 2026-10-01
 */
public final class UserCodeNormalizer {

    private UserCodeNormalizer() {}

    public static String normalize(String userCode) {
        return userCode.strip().toUpperCase(Locale.ROOT);
    }
}
