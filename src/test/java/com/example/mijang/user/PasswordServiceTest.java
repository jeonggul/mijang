package com.example.mijang.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.config.MailProperties;
import com.example.mijang.config.JwtProperties;
import com.example.mijang.config.PasswordResetProperties;
import com.example.mijang.user.domain.PasswordResetToken;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.UserResponse;
import com.example.mijang.security.PasswordVersionRegistry;
import com.example.mijang.user.mail.MailTransport;
import com.example.mijang.user.mapper.PasswordResetTokenMapper;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.PasswordService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 재설정과 변경.
 *
 * <p>DB 도 스프링도 부르지 않는다. 여기서 보려는 것은 넷이다 —
 * <b>계정을 알려 주지 않는가</b>(8.1.3), <b>링크가 정말 한 번만 듣는가</b>,
 * <b>재전송 간격이 걸리는가</b>, <b>원문이 어디에도 남지 않는가</b>(8.1.2).
 *
 * <p>지금까지 이 경로는 손으로 서버를 띄워 확인한 것이 전부였다. 고칠 때마다
 * 같은 확인을 되풀이할 수는 없다.
 */
class PasswordServiceTest {

    private static final String OLD_HASH = "enc:이전비밀번호";

    private static User user(Long id, String email, String nickname, String status, String hash) {
        return new User(id, email, hash, 1, nickname, null, "USER",
                "KRW", "light", status, LocalDateTime.of(2026, 8, 1, 0, 0));
    }

    private static User active() {
        return user(1L, "me@mijang.app", "정하", "ACTIVE", OLD_HASH);
    }

    /** 앞에 enc: 를 붙이는 것으로 암호화를 대신한다. 무엇이 저장됐는지 눈으로 본다. */
    private static class Encoder implements PasswordEncoder {
        @Override public String encode(CharSequence raw) { return "enc:" + raw; }
        @Override public boolean matches(CharSequence raw, String encoded) {
            return encoded != null && encoded.equals("enc:" + raw);
        }
    }

    /** 보낸 메일을 모아 두는 가짜 전송기. */
    private static class Mails implements MailTransport {
        final List<String> sent = new ArrayList<>();
        @Override public void sendResetLink(String toEmail, String resetUrl, long ttlMinutes) {
            sent.add(toEmail + "|" + resetUrl + "|" + ttlMinutes);
        }
    }

    /** 표를 흉내 내는 가짜 토큰 매퍼. 해시로 찾고, used_at 은 한 번만 찍힌다. */
    private static class Tokens implements PasswordResetTokenMapper {
        final Map<Long, PasswordResetToken> rows = new HashMap<>();
        long seq = 1;
        int deleteExpiredCalls;
        int invalidateCalls;
        /* 0 으로 두면 "찾을 때는 안 쓰였는데 갱신 순간 다른 요청이 먼저 가져간" 상황이 된다 */
        Integer markUsedOverride;
        PasswordResetToken latest;      // findLatestActiveByUserId 가 돌려줄 값

        @Override public PasswordResetToken findByTokenHash(String tokenHash) {
            return rows.values().stream()
                    .filter(r -> r.tokenHash().equals(tokenHash))
                    .findFirst().orElse(null);
        }

        @Override public PasswordResetToken findLatestActiveByUserId(Long userId) {
            return latest;
        }

        @Override public int insert(Long userId, String tokenHash, LocalDateTime expiresAt) {
            long id = seq++;
            rows.put(id, new PasswordResetToken(id, userId, tokenHash, expiresAt, null,
                    LocalDateTime.now()));
            return 1;
        }

        /* used_at IS NULL 조건이 붙은 갱신이다. 이미 찍혔으면 0 이 나가야 한다 */
        @Override public int markUsed(Long tokenId) {
            if (markUsedOverride != null) {
                return markUsedOverride;
            }
            PasswordResetToken row = rows.get(tokenId);
            if (row == null || row.usedAt() != null) {
                return 0;
            }
            rows.put(tokenId, new PasswordResetToken(row.tokenId(), row.userId(), row.tokenHash(),
                    row.expiresAt(), LocalDateTime.now(), row.createdAt()));
            return 1;
        }

        @Override public int invalidateActiveByUserId(Long userId) {
            invalidateCalls++;
            return 0;
        }

        @Override public int deleteExpired() {
            deleteExpiredCalls++;
            return 0;
        }
    }

    /** 사용자 표를 흉내 낸다. updatePassword 는 expectedHash 가 맞을 때만 듣는다. */
    private static class Users implements UserMapper {
        User byEmail;
        User byId;
        String savedHash;
        int updateResult = 1;

        @Override public User findByEmail(String email) { return byEmail; }
        @Override public User findById(Long id) { return byId; }

        @Override public int updatePassword(Long id, String passwordHash, String expectedHash) {
            if (updateResult == 0) {
                return 0;
            }
            savedHash = passwordHash;
            return 1;
        }

        @Override public int countByEmail(String email) { return 0; }
        @Override public int countByNickname(String nickname) { return 0; }
        @Override public int withdraw(Long id) { return 1; }
        @Override public int countByNicknameExcluding(String n, Long id) { return 0; }
        @Override public int updateProfile(Long id, String n, String img, String cur, String th) {
            return 1;
        }
        @Override public UserResponse findProfile(Long id) { return null; }
        @Override public int insert(UserInsert param) { return 1; }
    }

    private Tokens tokens;
    private Users users;
    private Mails mails;
    private PasswordVersionRegistry versions;

    private PasswordService service() {
        return service(Duration.ofMinutes(30), Duration.ofSeconds(60));
    }

    private PasswordService service(Duration ttl, Duration cooldown) {
        tokens = tokens == null ? new Tokens() : tokens;
        users = users == null ? new Users() : users;
        mails = mails == null ? new Mails() : mails;

        MailProperties mailProps = new MailProperties();
        mailProps.setBaseUrl("http://localhost:8080");
        PasswordResetProperties resetProps = new PasswordResetProperties();
        resetProps.setTokenTtl(ttl);
        resetProps.setResendCooldown(cooldown);

        JwtProperties jwtProps = new JwtProperties();
        versions = new PasswordVersionRegistry(jwtProps);

        return new PasswordService(users, tokens, new Encoder(), mails, mailProps, resetProps,
                versions);
    }

    /** 메일에 실려 나간 링크에서 토큰 원문만 뽑는다. */
    private String sentToken() {
        String url = mails.sent.get(0).split("\\|")[1];
        return url.substring(url.indexOf("token=") + 6);
    }

    @Nested
    @DisplayName("재설정 요청 — 계정을 알려 주지 않는다")
    class 요청 {

        /* 없는 이메일에 오류를 주면 이 화면이 계정 조회 도구가 된다(8.1.3) */
        @Test
        @DisplayName("가입되지 않은 이메일도 조용히 넘어간다")
        void 없는이메일() {
            users = new Users();
            users.byEmail = null;

            assertThatCode(() -> service().requestReset("nobody@mijang.app"))
                    .doesNotThrowAnyException();
            assertThat(mails.sent).isEmpty();          // 메일은 나가지 않는다
        }

        @Test
        @DisplayName("정지된 계정도 조용히 넘어간다")
        void 정지계정() {
            users = new Users();
            users.byEmail = user(1L, "me@mijang.app", "정하", "SUSPENDED", OLD_HASH);

            assertThatCode(() -> service().requestReset("me@mijang.app"))
                    .doesNotThrowAnyException();
            assertThat(mails.sent).isEmpty();
        }

        /* 소셜 전용 계정은 바꿀 비밀번호가 없다. 그 사실도 알려 주지 않는다 */
        @Test
        @DisplayName("비밀번호가 없는 계정도 조용히 넘어간다")
        void 소셜전용() {
            users = new Users();
            users.byEmail = user(1L, "me@mijang.app", "정하", "ACTIVE", null);

            assertThatCode(() -> service().requestReset("me@mijang.app"))
                    .doesNotThrowAnyException();
            assertThat(mails.sent).isEmpty();
        }

        @Test
        @DisplayName("가입된 계정에는 링크가 나간다")
        void 정상발송() {
            users = new Users();
            users.byEmail = active();

            service().requestReset("me@mijang.app");

            assertThat(mails.sent).hasSize(1);
            assertThat(mails.sent.get(0)).contains("me@mijang.app")
                    .contains("/password-reset?token=")
                    .contains("|30");                  // 유효 시간을 함께 알려 준다
        }

        /* 아무도 요청하지 않으면 만료 행이 쌓인다. 요청할 때마다 가볍게 치운다 */
        @Test
        @DisplayName("요청할 때 지나간 토큰을 정리한다")
        void 만료정리() {
            users = new Users();
            users.byEmail = null;

            service().requestReset("nobody@mijang.app");

            assertThat(tokens.deleteExpiredCalls).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("토큰 발급")
    class 발급 {

        /* 유효한 링크가 여러 개 떠 있으면 "최근 것만 듣는다"는 기대와 어긋난다 */
        @Test
        @DisplayName("새로 발급하면 이전 링크를 죽인다")
        void 이전무효화() {
            users = new Users();
            users.byEmail = active();

            service().requestReset("me@mijang.app");

            assertThat(tokens.invalidateCalls).isEqualTo(1);
        }

        @Test
        @DisplayName("재전송 간격 안이면 메일을 다시 보내지 않는다")
        void 쿨다운() {
            users = new Users();
            tokens = new Tokens();
            tokens.latest = new PasswordResetToken(9L, 1L, "해시",
                    LocalDateTime.now().plusMinutes(30), null,
                    LocalDateTime.now().minusSeconds(10));   // 10초 전에 이미 보냈다
            users.byEmail = active();

            service().requestReset("me@mijang.app");

            assertThat(mails.sent).isEmpty();
            assertThat(tokens.invalidateCalls).isZero();     // 살아 있는 링크를 죽이지도 않는다
        }

        @Test
        @DisplayName("간격이 지났으면 다시 보낸다")
        void 쿨다운경과() {
            users = new Users();
            tokens = new Tokens();
            tokens.latest = new PasswordResetToken(9L, 1L, "해시",
                    LocalDateTime.now().plusMinutes(30), null,
                    LocalDateTime.now().minusSeconds(120));
            users.byEmail = active();

            service().requestReset("me@mijang.app");

            assertThat(mails.sent).hasSize(1);
        }

        /* 표가 통째로 새어도 그것만으로는 남의 비밀번호를 바꿀 수 없어야 한다(8.1.2) */
        @Test
        @DisplayName("표에는 원문이 아니라 해시가 들어간다")
        void 원문미저장() {
            users = new Users();
            users.byEmail = active();

            service().requestReset("me@mijang.app");
            String raw = sentToken();

            assertThat(raw).hasSize(43);                     // 256비트를 URL 형태로
            assertThat(tokens.rows).hasSize(1);
            String stored = tokens.rows.values().iterator().next().tokenHash();
            assertThat(stored).isNotEqualTo(raw).hasSize(64);   // SHA-256 16진수
        }
    }

    @Nested
    @DisplayName("재설정 — 링크는 한 번만 듣는다")
    class 재설정 {

        /** 메일까지 보낸 상태를 만들고 원문 토큰을 돌려준다. */
        private String 발급한토큰() {
            users = new Users();
            users.byEmail = active();
            users.byId = active();
            PasswordService s = service();
            s.requestReset("me@mijang.app");
            return sentToken();
        }

        @Test
        @DisplayName("새 비밀번호가 암호화되어 저장된다")
        void 정상() {
            String raw = 발급한토큰();

            service().reset(raw, "newpass12");

            assertThat(users.savedHash).isEqualTo("enc:newpass12");
        }

        /*
         * 8.1.7 은 이미 나간 access 토큰이 만료까지 30분 사는 것을 받아들였다.
         * 그 30분이 곧 "비밀번호가 유출돼 급히 바꾼 사람" 에게 위험한 구간이라
         * 세대를 메모리에 적어 다음 요청에서 끊는다. 여기서는 적히는지만 본다 —
         * 끊는 쪽은 JwtAuthenticationFilter 의 몫이다.
         */
        @Test
        @DisplayName("재설정하면 이전 세대 토큰이 끊긴다")
        void 세대등록() {
            String raw = 발급한토큰();

            service().reset(raw, "newpass12");

            assertThat(versions.isStale(1L, 1)).isTrue();    // 발급 당시 세대는 1
            assertThat(versions.isStale(1L, 2)).isFalse();   // 바뀐 뒤 받은 토큰은 통과
            assertThat(versions.isStale(99L, 1)).isFalse();  // 남의 계정은 건드리지 않는다
        }

        @Test
        @DisplayName("같은 링크를 두 번 쓰면 막힌다")
        void 재사용() {
            String raw = 발급한토큰();
            PasswordService s = service();
            s.reset(raw, "newpass12");

            assertThatThrownBy(() -> s.reset(raw, "another12"))
                    .isInstanceOf(BusinessException.class);
        }

        /*
         * 앞의 "두 번 쓰면 막힌다" 는 findValidRow 가 used_at 을 보고 걸러 준다.
         * 그래서 그 테스트만으로는 markUsed 결과를 확인하는 부분이 죽어도 통과한다 —
         * 실제로 지우고 돌려 보니 아무 테스트도 실패하지 않았다.
         *
         * 여기서 보려는 것은 그 틈이다. 같은 링크로 동시에 둘이 들어오면 둘 다
         * findValidRow 를 지난다(그 시점엔 아직 안 쓰였다). 갈리는 곳은 used_at IS NULL
         * 조건이 붙은 갱신 하나뿐이고, 진 쪽은 0 을 받는다.
         */
        @Test
        @DisplayName("동시에 들어와 갱신에서 지면 비밀번호가 바뀌지 않는다")
        void 동시요청() {
            String raw = 발급한토큰();
            tokens.markUsedOverride = 0;        // 다른 요청이 방금 가져갔다

            assertThatThrownBy(() -> service().reset(raw, "newpass12"))
                    .isInstanceOf(BusinessException.class);
            assertThat(users.savedHash).isNull();
        }

        @Test
        @DisplayName("없는 토큰은 막힌다")
        void 없는토큰() {
            발급한토큰();

            assertThatThrownBy(() -> service().reset("aaaaaaaaaaaa", "newpass12"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("빈 토큰은 막힌다")
        void 빈토큰() {
            발급한토큰();

            assertThatThrownBy(() -> service().reset("  ", "newpass12"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service().reset(null, "newpass12"))
                    .isInstanceOf(BusinessException.class);
        }

        /* 만료된 링크로도 바꿀 수 있으면 유효 시간을 둔 의미가 없다 */
        @Test
        @DisplayName("만료된 토큰은 막힌다")
        void 만료() {
            users = new Users();
            users.byEmail = active();
            users.byId = active();
            PasswordService s = service(Duration.ofMinutes(-1), Duration.ofSeconds(60));
            s.requestReset("me@mijang.app");

            assertThatThrownBy(() -> s.reset(sentToken(), "newpass12"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("이전과 같은 비밀번호는 막힌다")
        void 같은비밀번호() {
            String raw = 발급한토큰();

            assertThatThrownBy(() -> service().reset(raw, "이전비밀번호"))
                    .isInstanceOf(BusinessException.class);
        }

        /* 지난번에 넣은 규칙이 재설정 경로에도 걸리는지 함께 본다 */
        @Test
        @DisplayName("닉네임이 들어간 비밀번호는 막힌다")
        void 추측가능() {
            String raw = 발급한토큰();

            assertThatThrownBy(() -> service().reset(raw, "정하12345"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("로그인 후 변경")
    class 변경 {

        @Test
        @DisplayName("현재 비밀번호가 맞으면 바뀐다")
        void 정상() {
            users = new Users();
            users.byId = active();

            service().change(1L, "이전비밀번호", "newpass12");

            assertThat(users.savedHash).isEqualTo("enc:newpass12");
        }

        @Test
        @DisplayName("바꾸면 이전 세대 토큰이 끊긴다")
        void 세대등록() {
            users = new Users();
            users.byId = active();

            service().change(1L, "이전비밀번호", "newpass12");

            assertThat(versions.isStale(1L, 1)).isTrue();
            assertThat(versions.isStale(1L, 2)).isFalse();
        }

        /* 실패한 변경으로 남의 세션을 끊을 수 있으면 안 된다 */
        @Test
        @DisplayName("실패하면 세대를 올리지 않는다")
        void 실패시미등록() {
            users = new Users();
            users.byId = active();

            assertThatThrownBy(() -> service().change(1L, "틀린값", "newpass12"))
                    .isInstanceOf(BusinessException.class);

            assertThat(versions.isStale(1L, 1)).isFalse();
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 막힌다")
        void 현재틀림() {
            users = new Users();
            users.byId = active();

            assertThatThrownBy(() -> service().change(1L, "틀린값", "newpass12"))
                    .isInstanceOf(BusinessException.class);
            assertThat(users.savedHash).isNull();
        }

        @Test
        @DisplayName("소셜 전용 계정은 바꿀 비밀번호가 없다")
        void 소셜전용() {
            users = new Users();
            users.byId = user(1L, "me@mijang.app", "정하", "ACTIVE", null);

            assertThatThrownBy(() -> service().change(1L, "아무값", "newpass12"))
                    .isInstanceOf(BusinessException.class);
        }

        /* 정지된 계정은 이미 나간 access 토큰으로 요청을 보낼 수 있다 */
        @Test
        @DisplayName("정지된 계정은 막힌다")
        void 정지계정() {
            users = new Users();
            users.byId = user(1L, "me@mijang.app", "정하", "SUSPENDED", OLD_HASH);

            assertThatThrownBy(() -> service().change(1L, "이전비밀번호", "newpass12"))
                    .isInstanceOf(BusinessException.class);
        }

        /* 그 사이 다른 요청이 먼저 바꿨다. 지금 받은 현재 비밀번호는 이미 옛것이다 */
        @Test
        @DisplayName("갱신이 0건이면 현재 비밀번호 오류로 돌려준다")
        void 경합() {
            users = new Users();
            users.byId = active();
            users.updateResult = 0;

            assertThatThrownBy(() -> service().change(1L, "이전비밀번호", "newpass12"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
