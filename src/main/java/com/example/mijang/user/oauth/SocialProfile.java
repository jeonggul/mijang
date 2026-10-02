package com.example.mijang.user.oauth;

import java.util.Map;

/** 구글·카카오의 응답 모양 차이를 흡수해 한 모양으로 맞춘 최소 신원이다 — providerUserId는 바뀔 수 있는 이메일이 아니라 제공자 쪽 고유 id다. */
public record SocialProfile(String provider,
                            String providerUserId,
                            String email,
                            String nickname) {

    /** 이메일이 있는지 판정한다. */
    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }

    /** 제공자 응답을 우리 모양으로 옮긴다. */
    public static SocialProfile of(String registrationId, Map<String, Object> attributes) {
        return "kakao".equalsIgnoreCase(registrationId)
                ? fromKakao(attributes)
                : fromGoogle(attributes);
    }

    private static SocialProfile fromGoogle(Map<String, Object> a) {
        return new SocialProfile("GOOGLE",
                str(a.get("sub")),
                str(a.get("email")),
                str(a.get("name")));
    }

    /** 카카오 응답을 옮긴다 — kakao_account는 동의 항목이라 통째로 없을 수 있어 단계마다 확인한다. */
    @SuppressWarnings("unchecked")
    private static SocialProfile fromKakao(Map<String, Object> a) {
        Map<String, Object> account = a.get("kakao_account") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        Map<String, Object> profile = account.get("profile") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        /* 인증된 이메일만 쓴다 — 미인증 이메일을 받으면 남의 주소로 그 계정에 붙을 수 있다 */
        Object verified = account.get("is_email_verified");
        String email = Boolean.TRUE.equals(verified) ? str(account.get("email")) : null;

        return new SocialProfile("KAKAO",
                str(a.get("id")),
                email,
                str(profile.get("nickname")));
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
