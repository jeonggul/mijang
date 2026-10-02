package com.example.mijang.user.dto;

/**
 * 중복·사용 가능 확인 응답. 가입 전에 이메일·닉네임을 미리 물어볼 때 쓴다.
 *
 * <p>사용할 수 없는 경우에도 <b>성공 응답</b>으로 내려간다. 확인 요청 자체는 정상 처리됐고,
 * "쓸 수 없음"은 오류가 아니라 답이기 때문이다. 화면은 available 만 보면 된다.
 */
public record AvailabilityResponse(boolean available, String message) {

    public static AvailabilityResponse ok(String message) {
        return new AvailabilityResponse(true, message);
    }

    public static AvailabilityResponse no(String message) {
        return new AvailabilityResponse(false, message);
    }
}
