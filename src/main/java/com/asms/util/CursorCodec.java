package com.asms.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Cursor of the keyset pagination used by long, append-only lists (section 8.4 of the Module 2 spec): the position is
 * the {@code (created_at, id)} pair of the last row of a page, sent as opaque base64url text.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public final class CursorCodec {

    private static final char SEPARATOR = '|';

    private CursorCodec() {}

    public static String encode(Instant createdAt, long id) {
        String raw = createdAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Empty for a missing cursor; a cursor that cannot be read is reported as invalid by the caller */
    public static Optional<Position> decode(@Nullable String cursor) throws IllegalArgumentException {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(SEPARATOR);
            return Optional.of(new Position(
                    Instant.parse(raw.substring(0, separator)), Long.parseLong(raw.substring(separator + 1))));
        } catch (IllegalArgumentException | DateTimeParseException | StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    /** Created time and id of the last row already returned */
    public record Position(Instant createdAt, long id) {}
}
