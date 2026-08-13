package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.JwtProperties;
import com.example.mijang.security.JwtProvider;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.LoginForm;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.AuthService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로그인 규칙 잠금. DB 없이 UserMapper 만 가짜로 세운다.
 *
 * <p>여기서 검증하는 것은 "계정 존재 여부가 새지 않는가" 하나다.
 * 이 규칙이 깨지면 이메일 목록을 긁어갈 수 있다.
 */
class AuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AuthService authService;
    private StubUserMapper mapper;

    /**
     * 매 테스트마다 새로 세운다.
     *
     * <p>BCrypt 는 실물을 쓴다. 해시를 흉내 내면 정작 검증 로직이 맞는지 확인하지 못한다.
     * secret 은 테스트 전용 값이라 32바이트만 넘기면 된다.
     */
    @BeforeEach
    void setUp() {
        var props = new JwtProperties();
        props.setSecret("test-secret-key-for-mijang-authentication-32+");
        mapper = new StubUserMapper();
        authService = new AuthService(mapper, encoder, new JwtProvider(props));
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호가 같은 오류로 나간다")
    void sameErrorForUnknownEmailAndWrongPassword() {
        mapper.stored = user("known@mijang.app", encoder.encode("correct-password"));

        var unknownEmail = login("unknown@mijang.app", "correct-password");
        var wrongPassword = login("known@mijang.app", "wrong-password");

        assertThatThrownBy(() -> authService.login(unknownEmail))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);

        assertThatThrownBy(() -> authService.login(wrongPassword))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("정지 계정도 같은 오류로 막는다 — 상태를 알려 주지 않는다")
    void suspendedAccountGetsSameError() {
        mapper.stored = new User(1L, "sus@mijang.app", encoder.encode("pw12345678"), 0,
                "정지", null, "USER", "KRW", "SYSTEM", "SUSPENDED", LocalDateTime.now());

        assertThatThrownBy(() -> authService.login(login("sus@mijang.app", "pw12345678")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("옳은 자격이면 access·refresh 가 함께 발급된다")
    void issuesBothTokens() {
        mapper.stored = user("known@mijang.app", encoder.encode("correct-password"));

        var tokens = authService.login(login("known@mijang.app", "correct-password"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.accessToken()).isNotEqualTo(tokens.refreshToken());
        assertThat(tokens.user().nickname()).isEqualTo("정하");
    }

    @Test
    @DisplayName("소셜 전용 계정(비밀번호 없음)은 비밀번호 로그인이 안 된다")
    void socialOnlyAccountCannotPasswordLogin() {
        mapper.stored = new User(1L, "social@mijang.app", null, 0, "소셜",
                null, "USER", "KRW", "SYSTEM", "ACTIVE", LocalDateTime.now());

        assertThatThrownBy(() -> authService.login(login("social@mijang.app", "anything")))
                .isInstanceOf(BusinessException.class);
    }

    /** ACTIVE·비밀번호 있는 기본 사용자. 각 테스트는 달라지는 부분만 따로 만든다. */
    private User user(String email, String hash) {
        return new User(1L, email, hash, 0, "정하", null,
                "USER", "KRW", "SYSTEM", "ACTIVE", LocalDateTime.now());
    }

    /** LoginForm 은 setter 만 있어 매번 세 줄이 된다. 여기로 모았다. */
    private LoginForm login(String email, String password) {
        var form = new LoginForm();
        form.setEmail(email);
        form.setPassword(password);
        return form;
    }

    /**
     * DB 없이 돌리기 위한 스텁. 사용자 한 명만 들고 있으면 이 테스트에는 충분하다.
     *
     * <p>목 프레임워크 대신 직접 구현한 이유 — 인터페이스 메서드가 다섯 개뿐이고,
     * 목을 쓰면 "무엇을 돌려주도록 시켰는가"가 테스트마다 흩어져 읽기 어려워진다.
     */
    static class StubUserMapper implements UserMapper {

        /** 저장돼 있다고 칠 사용자. 각 테스트가 직접 세팅한다. */
        User stored;

        /** 저장된 사용자와 이메일이 같으면 1. */
        @Override public int countByEmail(String email) {
            return stored != null && stored.email().equals(email) ? 1 : 0;
        }

        /** 저장된 사용자와 닉네임이 같으면 1. */
        @Override public int countByNickname(String nickname) {
            return stored != null && stored.nickname().equals(nickname) ? 1 : 0;
        }

        /** 이메일이 맞을 때만 돌려준다. 틀리면 null — 실제 매퍼와 같은 계약이다.
            탈퇴 계정을 거르는 것도 XML 과 같게 맞춘다. */
        @Override public User findByEmail(String email) {
            return live() != null && stored.email().equals(email) ? stored : null;
        }

        /** 갱신 경로에서 쓴다. 식별자는 보지 않고 탈퇴 여부만 XML 과 같게 본다. */
        @Override public User findById(Long id) {
            return live();
        }

        /** XML 의 status &lt;&gt; 'WITHDRAWN' 에 해당한다. */
        private User live() {
            return stored != null && !"WITHDRAWN".equals(stored.status()) ? stored : null;
        }

        /** MyBatis 가 하듯 생성된 id 를 파라미터에 되돌려 준다. */
        @Override public int insert(UserInsert param) {
            param.setId(1L);
            return 1;
        }

        /**
         * 실제 문장처럼 해시와 세대를 함께 바꾼다. 세대가 올라가야 이전 refresh 가 막힌다.
         * expectedHash 가 지금 값과 다르면 0 을 돌려주는 것까지 XML 과 같게 맞춘다.
         */
        @Override public int updatePassword(Long id, String passwordHash, String expectedHash) {
            if (stored == null || !java.util.Objects.equals(stored.passwordHash(), expectedHash)) {
                return 0;
            }
            stored = new User(stored.id(), stored.email(), passwordHash, stored.passwordVersion() + 1,
                    stored.nickname(), stored.profileImageUrl(), stored.role(),
                    stored.baseCurrency(), stored.theme(), stored.status(), stored.createdAt());
            return 1;
        }

        /** 지우지 않고 상태만 바꾼다. 이후 findById·findByEmail 은 null 을 돌려준다. */
        @Override public int withdraw(Long id) {
            if (stored == null) {
                return 0;
            }
            stored = new User(stored.id(), stored.email(), stored.passwordHash(), stored.passwordVersion(),
                    stored.nickname(), stored.profileImageUrl(), stored.role(),
                    stored.baseCurrency(), stored.theme(), "WITHDRAWN", stored.createdAt());
            return 1;
        }
    }
}
