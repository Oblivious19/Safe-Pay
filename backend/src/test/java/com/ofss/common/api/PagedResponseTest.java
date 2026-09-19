package com.ofss.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PagedResponseTest {

    @Test
    void mapsItemsAndPreservesPaginationMetadata() {
        Page<SourceRow> sourcePage = new PageImpl<>(
                List.of(
                        new SourceRow(101L, "first"),
                        new SourceRow(102L, "second")),
                PageRequest.of(1, 2),
                5);

        PagedResponse<ItemResponse> response =
                PagedResponse.from(
                        sourcePage,
                        row -> new ItemResponse(
                                row.id(),
                                row.label().toUpperCase()));

        assertThat(response.items()).containsExactly(
                new ItemResponse(101L, "FIRST"),
                new ItemResponse(102L, "SECOND"));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
    }

    private record SourceRow(Long id, String label) {
    }

    private record ItemResponse(Long id, String label) {
    }
}