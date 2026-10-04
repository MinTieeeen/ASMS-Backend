package com.asms.dto.catalog;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A school as attached to a user (schema SchoolRef): profile, public profile card, Admin detail, school picker.
 *
 * @param active false = deactivated: no longer offered in the picker, kept on the profiles that already have it
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record SchoolRefResponse(
        UUID id, String code, String name, @Nullable String shortName, boolean active) {}
