package com.asms.dto.common;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Page of a keyset-paginated list (CursorMeta of the Module 2 API spec).
 *
 * @param nextCursor send it back as {@code cursor} for the next page; {@code null} on the last page
 * @param <T> item type
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public record CursorResponse<T>(List<T> items, @Nullable String nextCursor) {}
