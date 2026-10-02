package com.example.mijang.common.response;

/**
 * 공통 API 응답 봉투. 성공/실패를 같은 모양으로 감싼다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.response
 * <p>[[미장-API명세서]] 1.1 의 세 필드({@code success} · {@code data} · {@code error})를 그대로 따른다.
 * 성공이면 {@code error} 가 null 이고, 실패면 {@code data} 가 null 이다. 둘 다 값이 있는 상태는 없다.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
