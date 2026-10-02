package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.oauth.SocialAuthHandlers.Pending;
import com.example.mijang.user.controller.SocialSignupController;
import com.example.mijang.user.controller.SocialSignupController.SocialSignupForm;
import com.example.mijang.user.dto.SignupForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가입 확정이 세션 신원만 믿는지 잠근다.
 *
 * <p>화면이 보낸 값으로 이메일이 정해지면, 아무 주소나 적어 남의 이메일로
 * 계정을 만들 수 있다. 폼 조립은 그래서 순수 함수로 떼어 두고 여기서 고정한다.
 */
class SocialSignupTest {

    @Test
    @DisplayName("가입 폼의 이메일은 세션 값이다 — 화면이 보낸 값은 쓰지 않는다")
    void emailComesFromSession() {
        var pending = new Pending(Pending.Kind.SIGNUP, "KAKAO", "kakao-1",
                "session@mijang.app", "추천닉");
        var submitted = new SocialSignupForm("내가정한닉", "pass1234");

        SignupForm form = SocialSignupController.toSignupForm(pending, submitted);

        assertThat(form.getEmail()).isEqualTo("session@mijang.app");
        assertThat(form.getNickname()).isEqualTo("내가정한닉");
        assertThat(form.getPassword()).isEqualTo("pass1234");
    }
}
