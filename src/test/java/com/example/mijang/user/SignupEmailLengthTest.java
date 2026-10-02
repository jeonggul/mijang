package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.service.AuthService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 길이 상한을 잠근다(4.13 #5). 탈퇴 시 {id}.withdrawn. 접두를 붙여도
 * VARCHAR(255)를 넘지 않도록 가입 단계에서 막는다.
 */
class SignupEmailLengthTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("225자를 넘는 이메일은 가입 폼 검증에서 걸린다")
    void tooLongEmailRejected() {
        var form = new SignupForm();
        // 로컬파트를 길게 만들어 226자 이메일을 만든다
        form.setEmail("a".repeat(214) + "@example.com");   // 214+12 = 226
        form.setPassword("pass1234");
        form.setNickname("길이가드");
        var violations = validator.validate(form);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("225자 이하 이메일은 통과한다")
    void normalEmailAccepted() {
        var form = new SignupForm();
        form.setEmail("normal@example.com");
        form.setPassword("pass1234");
        form.setNickname("정상");
        var violations = validator.validate(form);
        assertThat(violations)
                .noneMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    /**
     * 서비스 메서드 수준의 검증. 소셜 가입 경로는 @Valid 를 거치지 않으므로
     * 서비스의 funnel 에서 길이를 다시 본다.
     */
    @SpringBootTest
    @Transactional
    @Nested
    class SignupServiceEmailLengthTest {

        @Autowired
        private AuthService authService;

        @Test
        @DisplayName("225자를 넘는 이메일은 서비스 레벨에서 거부된다")
        void tooLongEmailRejectedInService() {
            var form = new SignupForm();
            form.setEmail("a".repeat(214) + "@example.com");   // 226자
            form.setPassword("pass1234");
            form.setNickname("서비스가드");

            assertThatThrownBy(() -> authService.signup(form))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMON_INVALID_REQUEST);
        }
    }
}
