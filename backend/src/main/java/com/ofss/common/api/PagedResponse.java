package com.ofss.common.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public PagedResponse {
        items = List.copyOf(
                Objects.requireNonNull(items, "items is required"));

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be zero or greater");
        }

        if (size < 1) {
            throw new IllegalArgumentException(
                    "size must be at least one");
        }

        if (totalElements < 0) {
            throw new IllegalArgumentException(
                    "totalElements must be zero or greater");
        }

        if (totalPages < 0) {
            throw new IllegalArgumentException(
                    "totalPages must be zero or greater");
        }
    }

    public static <S, T> PagedResponse<T> from(
            Page<S> source,
            Function<? super S, T> mapper) {

        Objects.requireNonNull(source, "source page is required");
        Objects.requireNonNull(mapper, "mapper is required");

        List<T> mappedItems = source.getContent()
                .stream()
                .map(item -> Objects.requireNonNull(
                        mapper.apply(item),
                        "mapper must not return null"))
                .toList();

        return new PagedResponse<>(
                mappedItems,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast());
    }
}