package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.oauth.SocialProfile;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 제공자 프로필 해석.
 *
 * <p>구글과 카카오는 응답 모양이 다르다. 여기서 흡수하지 않으면 서비스에 제공자별 if 가
 * 생기고, 제공자가 늘 때마다 그 if 가 자란다.
 *
 * <p>특히 카카오는 <b>동의 항목이 통째로 없을 수 있다.</b> 없는 것을 꺼내려다 터지면
 * 로그인 자체가 실패한다.
 */
class SocialProfileTest {

    @Nested
    @DisplayName("구글")
    class 구글 {

        @Test
        @DisplayName("sub 를 식별자로, email 과 name 을 그대로 읽는다")
        void 정상() {
            SocialProfile p = SocialProfile.of("google", Map.of(
                    "sub", "10769150350006150715113082367",
                    "email", "a@example.com",
                    "name", "정하"));

            assertThat(p.provider()).isEqualTo("GOOGLE");
            assertThat(p.providerUserId()).isEqualTo("10769150350006150715113082367");
            assertThat(p.email()).isEqualTo("a@example.com");
            assertThat(p.nickname()).isEqualTo("정하");
        }

        /* 이메일이 없으면 회원을 만들 수 없다. 그 사실이 드러나야 서비스가 거절한다 */
        @Test
        @DisplayName("이메일이 없으면 hasEmail 이 거짓이다")
        void 이메일없음() {
            SocialProfile p = SocialProfile.of("google", Map.of("sub", "1", "name", "정하"));

            assertThat(p.hasEmail()).isFalse();
        }
    }

    @Nested
    @DisplayName("카카오")
    class 카카오 {

        @Test
        @DisplayName("두 겹 안의 이메일과 닉네임을 꺼낸다")
        void 정상() {
            SocialProfile p = SocialProfile.of("kakao", Map.of(
                    "id", 1234567890L,
                    "kakao_account", Map.of(
                            "email", "b@example.com",
                            "is_email_verified", true,
                            "profile", Map.of("nickname", "미장이"))));

            assertThat(p.provider()).isEqualTo("KAKAO");
            assertThat(p.providerUserId()).isEqualTo("1234567890");
            assertThat(p.email()).isEqualTo("b@example.com");
            assertThat(p.nickname()).isEqualTo("미장이");
        }

        /* 미인증 이메일을 그대로 받으면 남의 주소를 적어 그 계정에 붙을 수 있다 */
        @Test
        @DisplayName("인증되지 않은 이메일은 쓰지 않는다")
        void 미인증이메일() {
            SocialProfile p = SocialProfile.of("kakao", Map.of(
                    "id", 1L,
                    "kakao_account", Map.of(
                            "email", "spoof@example.com",
                            "is_email_verified", false)));

            assertThat(p.hasEmail()).isFalse();
        }

        /* 동의를 안 받으면 kakao_account 가 통째로 없다. 꺼내려다 터지면 안 된다 */
        @Test
        @DisplayName("동의 항목이 통째로 없어도 터지지 않는다")
        void 동의없음() {
            SocialProfile p = SocialProfile.of("kakao", Map.of("id", 7L));

            assertThat(p.providerUserId()).isEqualTo("7");
            assertThat(p.hasEmail()).isFalse();
            assertThat(p.nickname()).isNull();
        }

        @Test
        @DisplayName("profile 만 없어도 이메일은 읽는다")
        void 프로필만없음() {
            SocialProfile p = SocialProfile.of("kakao", Map.of(
                    "id", 8L,
                    "kakao_account", Map.of("email", "c@example.com",
                                            "is_email_verified", true)));

            assertThat(p.email()).isEqualTo("c@example.com");
            assertThat(p.nickname()).isNull();
        }
    }
}
