package com.learnium.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void fromPageCopiesContentAndMetadata() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.getContent()).containsExactly("a", "b");
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(5);
        assertThat(response.getTotalPages()).isEqualTo(3);
    }

    @Test
    void fromPageWithMapperMapsContentAndKeepsMetadata() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 3), 3);

        PageResponse<String> response = PageResponse.from(page, i -> "n" + i);

        assertThat(response.getContent()).containsExactly("n1", "n2", "n3");
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(3);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    void emptyPageHasZeroTotals() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    void contentIsUnmodifiableAndIndependentOfSourceList() {
        List<String> source = new java.util.ArrayList<>(List.of("x"));
        Page<String> page = new PageImpl<>(source, PageRequest.of(0, 10), 1);

        PageResponse<String> response = PageResponse.from(page);
        source.add("y");

        assertThat(response.getContent()).containsExactly("x");
        assertThatThrownBy(() -> response.getContent().add("z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
