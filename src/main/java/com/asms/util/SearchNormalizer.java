package com.asms.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * Text helpers for accent-insensitive Vietnamese search (BR-USER-17, section 8.4 of the Module 2 spec).
 *
 * <p>{@link #toSearchKey} fills {@code users.full_name_search} and {@code schools.name_search}; migration V9 computes
 * the same value in SQL with {@code lower(unaccent(...))}, so both must stay in step: no accents, {@code đ → d},
 * lowercase, single spaces.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public final class SearchNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final char LIKE_ESCAPE = '\\';

    private SearchNormalizer() {}

    /** "  Nguyễn  Văn Đạt " → "nguyen van dat" */
    public static String toSearchKey(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String ascii = withoutMarks.replace('đ', 'd').replace('Đ', 'D');
        String collapsed = collapseWhitespace(ascii);
        return collapsed == null ? "" : collapsed.toLowerCase(Locale.ROOT);
    }

    /** Trims and collapses inner whitespace (BR-USER-01); blank becomes {@code null} for optional fields. */
    @Nullable
    public static String collapseWhitespace(@Nullable String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return WHITESPACE.matcher(text.strip()).replaceAll(" ");
    }

    /** Escapes LIKE wildcards so that user input is matched literally; use with {@link #likeEscape()}. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static char likeEscape() {
        return LIKE_ESCAPE;
    }
}
