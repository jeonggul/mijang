package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.policy.SignupPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 비밀번호에 공개된 정보가 들어갔는지 보는 규칙.
 *
 * <p>형식 규칙({@code PASSWORD_REGEX})은 영문·숫자 8~16자만 본다. {@code mijang12} 는
 * 그 검사를 통과하지만 닉네임 그대로다. 여기서 보려는 것은 그 틈이다.
 */
class PasswordPolicyTest {

    @Nested
    @DisplayName("닉네임이 들어가면 막는다")
    class 닉네임 {

        @Test
        @DisplayName("닉네임을 그대로 품으면 막힌다")
        void 그대로() {
            assertThat(SignupPolicy.containsProfileInfo("mijang12", "mijang", "a@b.com")).isTrue();
        }

        /* Mijang 과 mijang 을 다르게 보면 막는 의미가 없다 */
        @Test
        @DisplayName("대소문자가 달라도 막힌다")
        void 대소문자() {
            assertThat(SignupPolicy.containsProfileInfo("MiJaNg12", "mijang", "a@b.com")).isTrue();
        }

        @Test
        @DisplayName("한글 닉네임도 본다")
        void 한글() {
            assertThat(SignupPolicy.containsProfileInfo("정하12345", "정하", "a@b.com")).isTrue();
        }

        /* 앞뒤에 뭘 붙여도 포함이면 막는다. 안 그러면 규칙을 두나 마나다 */
        @Test
        @DisplayName("가운데 끼어 있어도 막힌다")
        void 부분() {
            assertThat(SignupPolicy.containsProfileInfo("ab1mijangXy", "mijang", "a@b.com")).isTrue();
        }
    }

    @Nested
    @DisplayName("이메일 아이디가 들어가면 막는다")
    class 이메일 {

        @Test
        @DisplayName("@ 앞부분을 품으면 막힌다")
        void 아이디() {
            assertThat(SignupPolicy.containsProfileInfo("dlwjdgkw1", "다른닉", "dlwjdgkw@gmail.com"))
                    .isTrue();
        }

        /* 도메인은 개인 정보가 아니다. gmail 을 못 쓰게 하면 애먼 사람이 걸린다 */
        @Test
        @DisplayName("도메인은 보지 않는다")
        void 도메인() {
            assertThat(SignupPolicy.containsProfileInfo("gmail123", "다른닉", "dlwjdgkw@gmail.com"))
                    .isFalse();
        }

        @Test
        @DisplayName("@ 로 시작하면 아이디가 없는 것으로 본다")
        void 아이디없음() {
            assertThat(SignupPolicy.containsProfileInfo("abcd1234", "다른닉", "@gmail.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("막지 않아야 하는 것")
    class 통과 {

        @Test
        @DisplayName("겹치지 않으면 통과한다")
        void 정상() {
            assertThat(SignupPolicy.containsProfileInfo("Qk3z8vLp", "mijang", "dlwjdgkw@gmail.com"))
                    .isFalse();
        }

        /* 1자까지 막으면 닉네임이 "정" 인 사람은 비밀번호에 "정" 을 못 쓴다 */
        @Test
        @DisplayName("한 글자 닉네임은 우연히 겹치므로 보지 않는다")
        void 짧은닉네임() {
            assertThat(SignupPolicy.containsProfileInfo("aBcd1234", "a", "x@b.com")).isFalse();
        }

        @Test
        @DisplayName("한 글자 이메일 아이디도 보지 않는다")
        void 짧은아이디() {
            assertThat(SignupPolicy.containsProfileInfo("aBcd1234", "다른닉", "a@b.com")).isFalse();
        }

        @Test
        @DisplayName("값이 없으면 판단하지 않는다")
        void 널() {
            assertThat(SignupPolicy.containsProfileInfo(null, "mijang", "a@b.com")).isFalse();
            assertThat(SignupPolicy.containsProfileInfo("  ", "mijang", "a@b.com")).isFalse();
            assertThat(SignupPolicy.containsProfileInfo("abcd1234", null, null)).isFalse();
        }
    }

    /* 새 규칙이 기존 형식 규칙을 건드리지 않았는지 함께 본다 */
    @Nested
    @DisplayName("형식 규칙은 그대로다")
    class 형식 {

        @Test
        @DisplayName("영문·숫자 8~16자만 통과한다")
        void 형식검사() {
            assertThat(SignupPolicy.isValidPassword("abcd1234")).isTrue();
            assertThat(SignupPolicy.isValidPassword("abcdefgh")).isFalse();   // 숫자가 없다
            assertThat(SignupPolicy.isValidPassword("12345678")).isFalse();   // 영문이 없다
            assertThat(SignupPolicy.isValidPassword("abc1234")).isFalse();    // 7자
        }
    }
}
