package com.asms.dto.catalog;

import java.util.List;

/**
 * Response of API-USER-21: every active school, sorted by name, for the school picker.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record SchoolListResponse(List<SchoolRefResponse> items) {}
