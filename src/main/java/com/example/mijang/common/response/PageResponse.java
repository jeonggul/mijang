package com.example.mijang.common.response;

import java.util.List;

/**
 * 페이징 응답. 목록 API 는 이 형태로 감싼다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.response
 * <p>[[미장-API명세서]] 1.4 가 {@code totalPages} · {@code hasNext} 까지 응답에 요구한다.
 * 파생값이지만 메서드로 두면 record 직렬화에서 빠지므로 컴포넌트로 두고 {@link #of} 에서 계산한다.
 * 직접 생성자를 부르면 값이 어긋날 수 있으니 {@link #of} 를 쓴다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages, page + 1 < totalPages);
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return of(List.of(), page, size, 0);
    }
}
