package com.example.mijang.common.response;

import java.util.List;

/** 페이징 응답 봉투다. 파생값이 어긋나지 않게 생성자 대신 {@link #of} 를 쓴다. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    /** 파생값(totalPages·hasNext)을 계산해 페이지 응답을 만든다. */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages, page + 1 < totalPages);
    }

    /** 빈 페이지 응답을 만든다. */
    public static <T> PageResponse<T> empty(int page, int size) {
        return of(List.of(), page, size, 0);
    }
}
