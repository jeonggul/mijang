/*
 * SocialProfile — 제공자가 준 프로필을 한 모양으로 맞춘 값
 *
 * 이 파일이 하는 일
 *   구글과 카카오의 응답 모양이 달라서, 서비스가 두 갈래를 알지 않도록 여기서 흡수한다.
 *
 *   구글   { sub, email, name, picture }
 *   카카오 { id, kakao_account: { email, profile: { nickname, profile_image_url } } }
 *
 *   같은 이름을 쓰지 않기 때문에 서비스에 if 를 두면 제공자가 늘 때마다 그 if 가 자란다.
 *   해석은 여기서 끝내고, 바깥에는 언제나 같은 모양으로 내보낸다.
 */
package com.example.mijang.user.oauth;

import java.util.Map;

/**
 * 제공자에서 받은 최소한의 신원.
 *
 * @param provider       {@code GOOGLE} 또는 {@code KAKAO}. oauth_accounts 의 enum 과 같다
 * @param providerUserId 제공자 쪽 고유 id. <b>이메일이 아니다</b> — 이메일은 바뀔 수 있다
 * @param email          없을 수 있다. 카카오는 동의를 안 받았거나 비즈 앱이 아니면 안 준다
 * @param nickname       없으면 이메일 앞부분을 쓴다
 */
public record SocialProfile(String provider,
                            String providerUserId,
                            String email,
                            String nickname) {

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

    /**
     * 카카오는 두 겹 안에 들어 있다.
     *
     * <p>{@code kakao_account} 는 동의 항목이라 <b>통째로 없을 수 있다.</b>
     * 없는 것을 꺼내려다 터지지 않게 단계마다 확인한다.
     */
    @SuppressWarnings("unchecked")
    private static SocialProfile fromKakao(Map<String, Object> a) {
        Map<String, Object> account = a.get("kakao_account") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        Map<String, Object> profile = account.get("profile") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        /* 이메일은 동의를 받았을 때만, 그리고 인증된 것만 쓴다.
           미인증 이메일을 그대로 받으면 남의 주소를 적어 그 계정에 붙을 수 있다 */
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
