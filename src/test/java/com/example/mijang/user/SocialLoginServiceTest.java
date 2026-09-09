package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.UserResponse;
import com.example.mijang.user.mapper.OAuthAccountMapper;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.oauth.SocialLoginService;
import com.example.mijang.user.oauth.SocialProfile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 소셜 신원을 어느 갈래로 보내는지 잠근다. DB 없이 매퍼만 가짜로 세운다.
 *
 * <p>여기서 지키는 것 하나 — <b>처음 보는 사람에게 계정을 만들어 주지 않는다.</b>
 * 만들어 주면 비밀번호 없는 계정이 생기고, 연동을 끊는 순간 잠긴다.
 */
class SocialLoginServiceTest {

    private StubUserMapper users;
    private StubOAuthMapper oauth;
    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        users = new StubUserMapper();
        oauth = new StubOAuthMapper();
        /* resolve() 는 AuthService 를 쓰지 않는다. 가입 확정(signupAndLink)만 쓰는데
           이 테스트는 갈래 판정만 본다 — 가짜를 세우면 무엇을 검증하는지 흐려진다 */
        service = new SocialLoginService(users, oauth, null);
    }

    @Test
    @DisplayName("처음 보는 소셜 신원에 계정을 만들지 않고 가입 화면으로 보류한다")
    void newIdentityIsHeldForSignup() {
        var profile = new SocialProfile("KAKAO", "kakao-1", "new@mijang.app", "새사람");

        var result = service.resolve(profile);

        assertThat(result.kind()).isEqualTo(SocialLoginService.Result.Kind.NEEDS_SIGNUP);
        assertThat(result.email()).isEqualTo("new@mijang.app");
        assertThat(result.provider()).isEqualTo("KAKAO");
        assertThat(result.nickname()).isEqualTo("새사람");
        assertThat(users.inserted).isEmpty();      // 계정을 만들지 않았다
        assertThat(oauth.inserted).isEmpty();      // 연결도 하지 않았다
    }

    @Test
    @DisplayName("닉네임이 이미 있으면 겹치지 않는 추천값을 준다")
    void suggestsFreeNickname() {
        users.takenNicknames.add("새사람");
        var profile = new SocialProfile("KAKAO", "kakao-1", "new@mijang.app", "새사람");

        var result = service.resolve(profile);

        assertThat(result.nickname()).isEqualTo("새사람1");
    }

    @Test
    @DisplayName("이메일이 이미 가입돼 있으면 연결 확인 갈래로 보낸다")
    void existingEmailNeedsLink() {
        users.byEmail = user(7L, "old@mijang.app");
        var profile = new SocialProfile("GOOGLE", "google-1", "old@mijang.app", "옛사람");

        var result = service.resolve(profile);

        assertThat(result.kind()).isEqualTo(SocialLoginService.Result.Kind.NEEDS_LINK);
        assertThat(users.inserted).isEmpty();
    }

    @Test
    @DisplayName("이미 연결된 신원이면 그대로 로그인시킨다")
    void linkedIdentityLogsIn() {
        oauth.linkedUserId = 7L;
        users.byId = user(7L, "old@mijang.app");
        var profile = new SocialProfile("GOOGLE", "google-1", "old@mijang.app", "옛사람");

        var result = service.resolve(profile);

        assertThat(result.kind()).isEqualTo(SocialLoginService.Result.Kind.LOGGED_IN);
        assertThat(result.user().id()).isEqualTo(7L);
    }

    /** User 는 필드 11개다 — id·email·passwordHash·passwordVersion·nickname·
     *  profileImageUrl·role·baseCurrency·theme·status·createdAt 순서. */
    private static User user(Long id, String email) {
        return new User(id, email, "$2a$10$hash", 0, "닉네임", null,
                "USER", "KRW", "SYSTEM", "ACTIVE", LocalDateTime.now());
    }

    /** 필요한 메서드만 채우고 나머지는 기본값을 돌려준다. */
    private static class StubUserMapper implements UserMapper {
        User byEmail;
        User byId;
        final List<String> takenNicknames = new ArrayList<>();
        final List<UserInsert> inserted = new ArrayList<>();

        @Override public int countByEmail(String email) { return 0; }
        @Override public int countByNickname(String nickname) {
            return takenNicknames.contains(nickname) ? 1 : 0;
        }
        @Override public User findByEmail(String email) { return byEmail; }
        @Override public User findById(Long id) { return byId; }
        @Override public int updatePassword(Long id, String passwordHash, String expectedHash) { return 1; }
        @Override public int withdraw(Long id, String expectedHash) { return 1; }
        @Override public int findPasswordVersion(Long id) { return 0; }
        @Override public void promoteToAdminForTest(Long id) { }
        @Override public String findWithdrawnEmailForTest(Long id) { return null; }
        @Override public int countByNicknameExcluding(String nickname, Long excludeUserId) { return 0; }
        @Override public int updateProfile(Long id, String nickname, String profileImageUrl,
                                           String baseCurrency, String theme) { return 1; }
        @Override public UserResponse findProfile(Long id) { return null; }
        @Override public int insert(UserInsert param) { inserted.add(param); return 1; }
    }

    private static class StubOAuthMapper implements OAuthAccountMapper {
        Long linkedUserId;
        final List<String> inserted = new ArrayList<>();

        @Override public Long findUserId(String provider, String providerUserId) { return linkedUserId; }
        @Override public boolean existsByUserAndProvider(Long userId, String provider) { return false; }
        @Override public int insert(Long userId, String provider, String providerUserId) {
            inserted.add(provider); return 1;
        }
        @Override public List<com.example.mijang.user.dto.SocialAccountResponse> findByUser(Long userId) {
            return List.of();
        }
        @Override public int deleteByUserAndProvider(Long userId, String provider) { return 0; }
        @Override public int deleteByUser(Long userId) { return 0; }
    }
}
