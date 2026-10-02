package com.example.mijang.user.dto;

/** 이메일·닉네임 사용 가능 확인 응답이다 — "쓸 수 없음"도 오류가 아니라 성공 응답으로 내려간다. */
public record AvailabilityResponse(boolean available, String message) {

    /** 사용 가능 응답을 만든다. */
    public static AvailabilityResponse ok(String message) {
        return new AvailabilityResponse(true, message);
    }

    /** 사용 불가 응답을 만든다. */
    public static AvailabilityResponse no(String message) {
        return new AvailabilityResponse(false, message);
    }
}
