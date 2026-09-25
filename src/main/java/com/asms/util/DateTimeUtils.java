package com.asms.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Date/time helpers for BR13: store in UTC, display in the user's time zone.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
public final class DateTimeUtils {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    private DateTimeUtils() {}

    /** A date-only deadline means 23:59 of that day in the user's time zone; returns the UTC instant. */
    public static Instant endOfDay(LocalDate date, ZoneId zone) {
        return date.atTime(END_OF_DAY).atZone(zone).toInstant();
    }

    public static Instant endOfDay(LocalDate date) {
        return endOfDay(date, DEFAULT_ZONE);
    }
}
