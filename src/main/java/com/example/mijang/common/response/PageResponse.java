package com.example.mijang.common.response;

import java.util.List;

/**
 * 페이징 응답. 목록 API 는 이 형태로 감싼다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.response
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
