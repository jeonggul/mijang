package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.ProfileUpdateForm;
import com.example.mijang.user.dto.UserResponse;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.UserService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 프로필 수정.
 *
 * <p>DB 도 스프링도 부르지 않는다. 여기서 보려는 것은 <b>어떤 것을 거절하고, 무엇을 DB 로
 * 넘기는가</b> 둘뿐이다. 형식 검사는 DTO 애너테이션의 몫이라 여기 오지 않는다.
 */
class UserServiceTest {

    /** 넘어온 값을 그대로 붙잡아 두는 가짜 매퍼. */
    private static class Users implements UserMapper {
        int nicknameTaken;                 // countByNicknameExcluding 가 돌려줄 값
        boolean updateCalled;
        Long excludedId;
        String nickname;
        String profileImageUrl;
        String baseCurrency;
        String theme;

        @Override public int countByEmail(String email) { return 0; }
        @Override public int countByNickname(String n) { return 0; }
        @Override public User findByEmail(String email) { return null; }
        @Override public User findById(Long id) { return null; }
        @Override public int updatePassword(Long id, String passwordHash, String expectedHash) { return 1; }
        @Override public int withdraw(Long id) { return 1; }
        @Override public int insert(UserMapper.UserInsert p) { return 1; }

        @Override public int countByNicknameExcluding(String n, Long excludeId) {
            excludedId = excludeId;
            return nicknameTaken;
        }

        @Override public int updateProfile(Long id, String n, String url, String cur, String th) {
            updateCalled = true;
            nickname = n; profileImageUrl = url; baseCurrency = cur; theme = th;
            return 1;
        }

        @Override public UserResponse findProfile(Long id) {
            return new UserResponse(id, "me@mijang.app", nickname == null ? "정하" : nickname,
                    profileImageUrl, "USER", baseCurrency == null ? "KRW" : baseCurrency,
                    theme == null ? "SYSTEM" : theme, LocalDateTime.of(2026, 8, 1, 0, 0),
                    LocalDateTime.of(2026, 8, 1, 0, 0));
        }
    }

    private static ProfileUpdateForm form(String nickname, String url, String currency, String theme) {
        ProfileUpdateForm f = new ProfileUpdateForm();
        f.setNickname(nickname);
        f.setProfileImageUrl(url);
        f.setBaseCurrency(currency);
        f.setTheme(theme);
        return f;
    }

    private static UserService service(Users users) {
        return new UserService(users);
    }

    @Nested
    @DisplayName("닉네임 — 가입과 같은 규칙으로 본다")
    class 닉네임 {

        /* 여기만 느슨하면 가입에서 막힌 이름을 수정으로 우회할 수 있다(2.2) */
        @Test
        @DisplayName("금지어는 거절한다")
        void 금지어() {
            Users users = new Users();

            assertThatThrownBy(() -> service(users).updateProfile(1L, form("관리자", null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("사용할 수 없");
            assertThat(users.updateCalled).isFalse();
        }

        @Test
        @DisplayName("남이 쓰는 닉네임은 거절한다")
        void 중복() {
            Users users = new Users();
            users.nicknameTaken = 1;

            assertThatThrownBy(() -> service(users).updateProfile(1L, form("정하", null, null, null)))
                    .isInstanceOf(BusinessException.class);
            assertThat(users.updateCalled).isFalse();
        }

        /* 자기 자신을 빼지 않으면 닉네임을 그대로 두고 다른 항목만 바꿀 때
           "이미 사용 중" 이 뜬다(2.2) */
        @Test
        @DisplayName("중복 검사에서 자기 자신은 뺀다")
        void 자기자신제외() {
            Users users = new Users();

            service(users).updateProfile(7L, form("정하", null, null, null));

            assertThat(users.excludedId).isEqualTo(7L);
        }

        @Test
        @DisplayName("닉네임을 안 보내면 중복 검사를 하지 않는다")
        void 닉네임없으면검사안한다() {
            Users users = new Users();
            users.nicknameTaken = 1;          // 검사했다면 거절당했을 상태

            service(users).updateProfile(1L, form(null, null, "USD", null));

            assertThat(users.excludedId).isNull();
            assertThat(users.baseCurrency).isEqualTo("USD");
        }
    }

    @Nested
    @DisplayName("보낸 항목만 바꾼다")
    class 부분수정 {

        @Test
        @DisplayName("보낸 것만 넘기고 나머지는 null 로 둔다")
        void 일부만() {
            Users users = new Users();

            service(users).updateProfile(1L, form(null, null, null, "DARK"));

            assertThat(users.theme).isEqualTo("DARK");
            assertThat(users.nickname).isNull();
            assertThat(users.profileImageUrl).isNull();
            assertThat(users.baseCurrency).isNull();
        }

        /* XML 의 <set> 이 비면 UPDATE 문이 만들어지지 않는다. 부르지 않는 편이 맞다 */
        @Test
        @DisplayName("바꿀 것이 하나도 없으면 DB 를 부르지 않는다")
        void 빈본문() {
            Users users = new Users();

            UserResponse r = service(users).updateProfile(1L, form(null, null, null, null));

            assertThat(users.updateCalled).isFalse();
            assertThat(r).isNotNull();          // 그래도 지금 프로필은 돌려준다
        }

        @Test
        @DisplayName("네 항목을 한 번에 바꾼다")
        void 전부() {
            Users users = new Users();

            service(users).updateProfile(1L,
                    form("새이름", "https://img.example/a.png", "USD", "LIGHT"));

            assertThat(users.nickname).isEqualTo("새이름");
            assertThat(users.profileImageUrl).isEqualTo("https://img.example/a.png");
            assertThat(users.baseCurrency).isEqualTo("USD");
            assertThat(users.theme).isEqualTo("LIGHT");
        }

        @Test
        @DisplayName("수정 후 갱신된 프로필을 돌려준다")
        void 응답() {
            Users users = new Users();

            UserResponse r = service(users).updateProfile(1L, form("새이름", null, "USD", null));

            assertThat(r.nickname()).isEqualTo("새이름");
            assertThat(r.baseCurrency()).isEqualTo("USD");
            /* 이메일은 바꿀 수 없다(2.1) — 폼에 아예 없다 */
            assertThat(r.email()).isEqualTo("me@mijang.app");
        }
    }
}
