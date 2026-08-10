package com.example.mijang.common.response;

/**
 * 공통 API 응답 봉투. 성공/실패를 같은 모양으로 감싼다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.response
 */
public record ApiResponse<T>(boolean success, T data, String errorCode, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> fail(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message);
    }
}
