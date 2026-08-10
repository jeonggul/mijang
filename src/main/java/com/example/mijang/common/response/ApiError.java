package com.example.mijang.common.response;

/**
 * 실패 응답의 error 객체.
 *
 * <p>[[미장-API명세서]] 1.1 — {@code {"code","message","field"}}
 * <p>{@code message} 는 사용자에게 그대로 보여줄 수 있는 한국어여야 한다.
 * 스택 트레이스·내부 용어를 담지 않는다.
 * <p>{@code field} 는 폼 검증 실패일 때만 채우고, 그 외에는 null 이다.
 */
public record ApiError(String code, String message, String field) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field);
    }
}
