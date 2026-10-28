package com.harsh.taskmanager.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Stable, serialisation-friendly page envelope. Spring's {@code PageImpl} is not meant to be exposed as JSON
 * (and cannot be deserialised from a cache), so pages are converted to this record instead.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        List<String> sort
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        List<String> sort = page.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase(Locale.ROOT))
                .toList();
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                sort);
    }
}
