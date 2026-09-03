package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.config.DemoAccountProperties;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.LoginHintService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 로그인 화면 계정 안내 잠금.
 *
 * <p>여기서 지키는 규칙은 하나다 — <b>관리자 계정은 안내에 실리지 않는다.</b>
 * 로그인 화면은 비로그인 공개 화면이고, 안내에 실린 값은 페이지 소스에 그대로 나간다.
 * 2026-09-03 점검에서 체험 계정이 ADMIN 이라 누구나 운영 콘솔을 열 수 있었다
 * ([[4.10 브라우저 전수 점검 보고서]] 3.1).
 *
 * <p>설정 파일만 고쳐서는 같은 일이 또 난다 — 사람이 손으로 고치는 곳이다.
 */
class LoginHintServiceTest {

    @Test
    @DisplayName("관리자 계정은 안내에 실리지 않는다")
    void hidesAdminAccount() {
        var service = service(Map.of("admin@mijang.app", "ADMIN"), "admin@mijang.app");

        assertThat(service.visibleAccounts()).isEmpty();
    }

    @Test
    @DisplayName("일반 회원 계정은 그대로 실린다")
    void showsRegularAccount() {
        var service = service(Map.of("guest@mijang.app", "USER"), "guest@mijang.app");

        assertThat(service.visibleAccounts())
                .extracting(DemoAccountProperties.Account::getEmail)
                .containsExactly("guest@mijang.app");
    }

    @Test
    @DisplayName("관리자와 일반 회원이 섞여 있으면 일반 회원만 남는다")
    void keepsOnlyNonAdmin() {
        var service = service(
                Map.of("admin@mijang.app", "ADMIN", "guest@mijang.app", "USER"),
                "admin@mijang.app", "guest@mijang.app");

        assertThat(service.visibleAccounts())
                .extracting(DemoAccountProperties.Account::getEmail)
                .containsExactly("guest@mijang.app");
    }

    @Test
    @DisplayName("실재하지 않는 계정은 뺀다 — 눌러 채워도 로그인되지 않는다")
    void dropsUnknownAccount() {
        var service = service(Map.of(), "ghost@mijang.app");

        assertThat(service.visibleAccounts()).isEmpty();
    }

    /** 설정에 {@code emails} 를 적어 두고, DB 에는 {@code roles} 만 있다고 친다. */
    private LoginHintService service(Map<String, String> roles, String... emails) {
        var props = new DemoAccountProperties();
        props.setAccounts(List.of(emails).stream().map(email -> {
            var account = new DemoAccountProperties.Account();
            account.setEmail(email);
            account.setPassword("whatever");
            return account;
        }).toList());
        return new LoginHintService(props, stub(roles));
    }

    /**
     * {@code findByEmail} 만 답하는 매퍼.
     *
     * <p>나머지는 계약만 채운다 — 여기서 뭔가 돌려주면 안내 화면이 다른 경로에
     * 기대게 되어 경계가 흐려진다.
     */
    private UserMapper stub(Map<String, String> roles) {
        return new UserMapper() {
            @Override public User findByEmail(String email) {
                String role = roles.get(email);
                return role == null ? null
                        : new User(1L, email, "hash", 1, "닉", null, role,
                                   "KRW", "SYSTEM", "ACTIVE", LocalDateTime.now());
            }

            @Override public User findById(Long id) { return null; }
            @Override public int countByEmail(String email) { return 0; }
            @Override public int countByNickname(String nickname) { return 0; }
            @Override public int countByNicknameExcluding(String nickname, Long excludeId) { return 0; }
            @Override public int insert(UserInsert param) { return 0; }
            @Override public int updateProfile(Long id, String nickname, String profileImageUrl,
                                               String baseCurrency, String theme) { return 0; }
            @Override public com.example.mijang.user.dto.UserResponse findProfile(Long id) { return null; }
            @Override public int withdraw(Long id) { return 0; }
            @Override public int updatePassword(Long id, String passwordHash, String expectedHash) { return 0; }
        };
    }
}
