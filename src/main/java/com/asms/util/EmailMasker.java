package com.asms.util;

/**
 * Masks an email for display to someone who only holds a link, e.g. {@code nguyen@gmail.com -> ng***@gmail.com}
 * (UC-AUTH-04 step 6).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class EmailMasker {

    private static final int VISIBLE_CHARS = 2;
    private static final String MASK = "***";

    private EmailMasker() {}

    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return MASK;
        }
        // Very short local parts keep only one character so that something is always hidden
        int visible = at > VISIBLE_CHARS ? VISIBLE_CHARS : 1;
        return email.substring(0, visible) + MASK + email.substring(at);
    }
}
