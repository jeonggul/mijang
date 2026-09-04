/*
 * SocialLoginService — 소셜 계정을 우리 회원에 잇는다
 *
 * 이 파일이 하는 일
 *   제공자가 준 신원 하나를 받아, 세 갈래 중 하나로 답한다.
 *
 *     이미 연결돼 있다        → 그 회원으로 로그인
 *     처음이고 이메일도 처음  → 회원을 만들고 연결
 *     처음인데 이메일이 이미 있다 → 연결하지 않고 "비밀번호로 확인해 달라" 고 돌려보낸다
 *
 *   왜 마지막을 자동으로 잇지 않는가
 *     제공자가 이메일을 검증하지 않으면, 남의 주소를 적은 소셜 계정으로 그 사람의
 *     기록에 들어갈 수 있다. 여기는 매매 원장이 들어 있는 곳이라 그 길을 열지 않는다.
 *     한 단계 번거롭더라도 기존 비밀번호를 한 번 받는다.
 *
 *   이메일이 없는 경우도 거절한다. users.email 이 NOT NULL·UNIQUE 라 만들 수가 없고,
 *   가짜 주소를 지어 넣으면 비밀번호 찾기가 영영 막힌다.
 */
package com.example.mijang.user.oauth;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.mapper.OAuthAccountMapper;
import com.example.mijang.user.mapper.UserMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 로그인 연결. {@code AUTH-07} */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    /** 닉네임 길이 상한. SignupPolicy 의 규칙(2~10자)과 같다. */
    private static final int NICKNAME_MAX = 10;

    private final UserMapper userMapper;
    private final OAuthAccountMapper oauthMapper;

    /**
     * 결과 세 갈래.
     *
     * @param user      로그인시킬 회원. 연결이 필요하면 null
     * @param linkEmail 연결을 기다리는 이메일. 로그인되면 null
     */
    public record Result(User user, String linkEmail, String provider) {

        public boolean needsLink() {
            return user == null;
        }

        static Result loggedIn(User user) {
            return new Result(user, null, null);
        }

        static Result needsLink(String email, String provider) {
            return new Result(null, email, provider);
        }
    }

    @Transactional
    public Result resolve(SocialProfile profile) {
        /* 이미 이어 둔 계정이면 이메일을 보지 않는다. 제공자에서 이메일을 바꿨어도
           같은 사람이다 — 그래서 provider_user_id 로 먼저 찾는다 */
        Long linkedId = oauthMapper.findUserId(profile.provider(), profile.providerUserId());
        if (linkedId != null) {
            User linked = userMapper.findById(linkedId);
            if (linked == null || !linked.isActive()) {
                throw new BusinessException(ErrorCode.AUTH_REQUIRED);
            }
            return Result.loggedIn(linked);
        }

        if (!profile.hasEmail()) {
            throw new BusinessException(ErrorCode.SOCIAL_EMAIL_REQUIRED);
        }
        String email = profile.email().trim().toLowerCase(Locale.ROOT);

        User existing = userMapper.findByEmail(email);
        if (existing != null) {
            /* 탈퇴한 계정에는 잇지 않는다. 소셜로 되살리는 뒷문이 된다 */
            if (!existing.isActive()) {
                throw new BusinessException(ErrorCode.AUTH_REQUIRED);
            }
            return Result.needsLink(email, profile.provider());
        }

        Long userId = createUser(email, profile.nickname());
        oauthMapper.insert(userId, profile.provider(), profile.providerUserId());
        log.info("[소셜] 새 회원 생성·연결 — {} {}", profile.provider(), mask(email));
        return Result.loggedIn(userMapper.findById(userId));
    }

    /**
     * 확인이 끝난 기존 회원에 이 제공자를 잇는다.
     *
     * <p>비밀번호를 맞힌 <b>뒤에만</b> 불러야 한다. 이 메서드는 확인하지 않는다 —
     * 확인은 부르는 쪽(AuthService)의 몫이다.
     */
    @Transactional
    public void link(Long userId, String provider, String providerUserId) {
        if (oauthMapper.existsByUserAndProvider(userId, provider)) {
            return;   // 이미 이어져 있다. 두 번 눌러도 같은 결과여야 한다
        }
        oauthMapper.insert(userId, provider, providerUserId);
        log.info("[소셜] 기존 회원에 연결 — {} userId={}", provider, userId);
    }

    /**
     * 소셜 전용 회원을 만든다. 비밀번호는 없다(스키마가 NULL 을 허용한다).
     *
     * <p>닉네임이 겹치면 뒤에 숫자를 붙인다. 제공자가 준 이름은 남과 겹치기 쉬운데,
     * 거기서 가입을 막으면 사용자는 이유를 알 수 없다.
     */
    private Long createUser(String email, String rawNickname) {
        var insert = new UserMapper.UserInsert(email, null, uniqueNickname(rawNickname, email));
        userMapper.insert(insert);
        return insert.getId();
    }

    private String uniqueNickname(String rawNickname, String email) {
        String base = sanitize(rawNickname);
        if (base.isBlank()) {
            base = sanitize(email.split("@")[0]);
        }
        if (base.isBlank()) {
            base = "회원";
        }
        String candidate = base;
        for (int i = 1; userMapper.countByNickname(candidate) > 0; i++) {
            String suffix = String.valueOf(i);
            int keep = Math.min(base.length(), NICKNAME_MAX - suffix.length());
            candidate = base.substring(0, keep) + suffix;
            if (i > 999) {
                throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "nickname");
            }
        }
        return candidate;
    }

    /** 닉네임 규칙(한글·영문·숫자 2~10자)에 맞게 다듬는다. */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[^가-힣a-zA-Z0-9]", "");
        return cleaned.length() > NICKNAME_MAX ? cleaned.substring(0, NICKNAME_MAX) : cleaned;
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
