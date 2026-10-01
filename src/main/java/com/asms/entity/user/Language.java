package com.asms.entity.user;

import java.util.Locale;
import lombok.Getter;

/**
 * Language a user prefers for emails and the UI (NFR14). Vietnamese is the default.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Getter
public enum Language {
    VI(Locale.of("vi")),
    EN(Locale.ENGLISH);

    public static final Language DEFAULT = VI;

    private final Locale locale;

    Language(Locale locale) {
        this.locale = locale;
    }
}
