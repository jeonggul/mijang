package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.service.LoginAttemptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 로그인 시도 제한.
 *
 * <p>여기서 보려는 것은 넷이다 — <b>한도를 넘겨야만 막는가</b>,
 * <b>성공하면 그 계정은 풀리는가</b>, <b>성공이 IP 누적까지 지우지는 않는가</b>,
 * <b>이메일 표기가 달라도 같은 계정으로 세는가</b>.
 */
class LoginAttemptServiceTest {

    private static final String IP = "203.0.113.9";

    @Nested
    @DisplayName("이메일 한도")
    class 이메일 {

        @Test
        @DisplayName("한도 전에는 막지 않는다")
        void 한도전() {
            LoginAttemptService s = new LoginAttemptService();
            for (int i = 0; i < 4; i++) {
                s.recordFailure("a@mijang.app", IP);
            }

            assertThat(s.isBlocked("a@mijang.app", IP)).isFalse();
        }

        @Test
        @DisplayName("다섯 번 실패하면 막는다")
        void 한도도달() {
            LoginAttemptService s = new LoginAttemptService();
            for (int i = 0; i < 5; i++) {
                s.recordFailure("a@mijang.app", IP);
            }

            assertThat(s.isBlocked("a@mijang.app", IP)).isTrue();
        }

        /* 대소문자·공백이 다르면 다른 계정으로 세어져 한도가 무의미해진다 */
        @Test
        @DisplayName("대소문자와 공백이 달라도 같은 계정으로 센다")
        void 표기다름() {
            LoginAttemptService s = new LoginAttemptService();
            for (int i = 0; i < 5; i++) {
                s.recordFailure(" A@Mijang.App ", IP);
            }

            assertThat(s.isBlocked("a@mijang.app", IP)).isTrue();
        }

        @Test
        @DisplayName("성공하면 그 계정 기록이 풀린다")
        void 성공후해제() {
            LoginAttemptService s = new LoginAttemptService();
            for (int i = 0; i < 5; i++) {
                s.recordFailure("a@mijang.app", IP);
            }

            s.recordSuccess("a@mijang.app");

            assertThat(s.isBlocked("a@mijang.app", IP)).isFalse();
        }

        @Test
        @DisplayName("다른 계정은 영향을 받지 않는다")
        void 다른계정() {
            LoginAttemptService s = new LoginAttemptService();
            for (int i = 0; i < 5; i++) {
                s.recordFailure("a@mijang.app", IP);
            }

            assertThat(s.isBlocked("b@mijang.app", "198.51.100.2")).isFalse();
        }
    }

    @Nested
    @DisplayName("IP 한도")
    class 아이피 {

        /* 계정을 바꿔 가며 흔한 비밀번호를 뿌리면 이메일 한도로는 안 걸린다 */
        @Test
        @DisplayName("계정을 바꿔 가며 뿌려도 IP 한도에 걸린다")
        void 계정순회() {
            LoginAttemptService s = new LoginAttemptService();
            for (int i = 0; i < 30; i++) {
                s.recordFailure("user" + i + "@mijang.app", IP);
            }

            assertThat(s.isBlocked("fresh@mijang.app", IP)).isTrue();
        }

        /* 자기 계정으로 한 번 로그인해 IP 누적을 초기화할 수 있으면 한도가 무의미하다 */
        @Test
        @DisplayName("성공해도 IP 누적은 남는다")
        void 성공이IP를풀지않음() {
            LoginAttemptService s = new LoginAttemptService();
            for (int i = 0; i < 30; i++) {
                s.recordFailure("user" + i + "@mijang.app", IP);
            }

            s.recordSuccess("user0@mijang.app");

            assertThat(s.isBlocked("user0@mijang.app", IP)).isTrue();
        }
    }
}
