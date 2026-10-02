package com.example.mijang.common.response;

/** 실패 응답의 error 객체다. message 는 사용자에게 그대로 보여줄 한국어, field 는 폼 검증 실패일 때만 채운다. */
public record ApiError(String code, String message, String field) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field);
    }
}
