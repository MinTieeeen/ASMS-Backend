package com.asms.dto.admin;

import java.util.UUID;

/**
 * Short reference to a user (schema UserRef): who created, locked or acted.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record UserRefResponse(UUID id, String fullName, String email) {}
