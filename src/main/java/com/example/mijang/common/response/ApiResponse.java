package com.example.mijang.common.response;

/** 공통 API 응답 봉투다. 성공이면 error 가 null, 실패면 data 가 null 이다. */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
