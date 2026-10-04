package com.asms.dto.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Standard paginated response (PageMeta of the Module 2 API spec): the page items plus the total number of records.
 * {@code page} starts at 1, like the {@code page} request parameter ({@code one-indexed-parameters} in
 * application.yml).
 *
 * @param <T> item type
 * @author MinhTien
 * @version 2.0.0
 * @since 2026-09-26
 * @modified 2026-10-04
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
