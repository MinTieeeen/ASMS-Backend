package com.asms.entity.catalog;

/**
 * Kind of a holiday ({@code holidays.kind}).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public enum HolidayKind {
    /** Public holiday or Tết set by law */
    PUBLIC_HOLIDAY,
    /** Day off given back when a holiday falls on a weekend */
    COMPENSATORY,
    /** Any other day off decided for the whole system */
    OTHER
}
