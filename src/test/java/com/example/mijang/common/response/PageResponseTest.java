package com.example.mijang.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [[미장-API명세서]] 1.4 페이징 응답. */
class PageResponseTest {

    @Test
    @DisplayName("명세서 1.4 예시 그대로 — 138건 / 20건씩이면 7페이지, 첫 장에는 다음이 있다")
    void matchesSpecExample() {
        var page = PageResponse.of(List.of("a"), 0, 20, 138);

        assertThat(page.totalPages()).isEqualTo(7);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지에서는 hasNext 가 꺼진다")
    void lastPageHasNoNext() {
        var page = PageResponse.of(List.of("a"), 6, 20, 138);

        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("나누어떨어지면 올림이 한 장을 더 만들지 않는다")
    void exactDivisionDoesNotAddPage() {
        assertThat(PageResponse.of(List.of(), 0, 20, 140).totalPages()).isEqualTo(7);
    }

    @Test
    @DisplayName("빈 결과는 0페이지이고 다음이 없다")
    void emptyResult() {
        var page = PageResponse.empty(0, 20);

        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.content()).isEmpty();
    }

    @Test
    @DisplayName("size 가 0이어도 0으로 나누지 않는다")
    void zeroSizeDoesNotDivideByZero() {
        assertThat(PageResponse.of(List.of(), 0, 0, 10).totalPages()).isZero();
    }
}
