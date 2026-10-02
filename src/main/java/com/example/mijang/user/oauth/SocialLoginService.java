package com.example.mijang.user.oauth;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.mapper.OAuthAccountMapper;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.AuthService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 신원을 받아 로그인·연결 확인·가입 보류 세 갈래로 판정하고 연동을 잇는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    /** 닉네임 길이 상한이다 — SignupPolicy 규칙(2~10자)과 같다. */
    private static final int NICKNAME_MAX = 10;

    private final UserMapper userMapper;
    private final OAuthAccountMapper oauthMapper;
    private final AuthService authService;

    /** 판정 결과 세 갈래다 — user는 로그인 갈래에서만, email·nickname은 보류 갈래에서만 채워진다. */
    public record Result(Kind kind, User user, String email, String provider, String nickname) {

        public enum Kind {
            /** 이미 이어져 있다 */
            LOGGED_IN,
            /** 같은 이메일로 이미 가입돼 있다 — 비밀번호 확인이 필요하다 */
            NEEDS_LINK,
            /** 처음 보는 사람이다 — 가입 화면에서 비밀번호를 받아야 한다 */
            NEEDS_SIGNUP
        }

        static Result loggedIn(User user) {
            return new Result(Kind.LOGGED_IN, user, null, null, null);
        }

        static Result needsLink(String email, String provider) {
            return new Result(Kind.NEEDS_LINK, null, email, provider, null);
        }

        static Result needsSignup(String email, String provider, String nickname) {
            return new Result(Kind.NEEDS_SIGNUP, null, email, provider, nickname);
        }
    }

    /** 소셜 신원을 세 갈래 중 하나로 판정한다 — 이메일 없는 프로필과 탈퇴 계정은 거절한다. */
    @Transactional
    public Result resolve(SocialProfile profile) {
        /* 제공자에서 이메일을 바꿔도 같은 사람이도록 provider_user_id로 먼저 찾는다 */
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
            /* 탈퇴한 계정에는 잇지 않는다 — 소셜로 되살리는 뒷문이 된다 */
            if (!existing.isActive()) {
                throw new BusinessException(ErrorCode.AUTH_REQUIRED);
            }
            return Result.needsLink(email, profile.provider());
        }

        /* 여기서 계정을 만들지 않는다 — 비밀번호 없는 계정은 연동을 끊으면 복구 불능이라 가입 화면에서 비밀번호를 받은 뒤에 만든다 */
        log.info("[소셜] 새 회원 — 가입 화면으로 보류 {} {}", profile.provider(), mask(email));
        return Result.needsSignup(email, profile.provider(),
                uniqueNickname(profile.nickname(), email));
    }

    /** 기존 회원에 이 제공자를 잇는다 — 비밀번호 확인은 하지 않으므로 반드시 확인이 끝난 뒤에만 불러야 한다. */
    @Transactional
    public void link(Long userId, String provider, String providerUserId) {
        if (oauthMapper.existsByUserAndProvider(userId, provider)) {
            return;   // 이미 이어져 있다 — 두 번 눌러도 같은 결과여야 한다
        }
        /* 일반 SELECT는 스냅샷을 읽어 동시 탈퇴를 못 본다 — FOR UPDATE로 최신 커밋 상태를 읽어야 withdraw와 순서가 맞물린다 */
        String status = userMapper.lockUserStatusForUpdate(userId);
        if (!"ACTIVE".equals(status)) {
            log.warn("[소셜] 비활성 사용자라 연동을 건너뜀 — {} userId={}", provider, userId);
            return;
        }
        // 진짜 경합 차단은 위 FOR UPDATE 잠금이 한다 — insert의 WHERE status='ACTIVE'는 방어적 여분이다
        int inserted = oauthMapper.insert(userId, provider, providerUserId);
        if (inserted == 0) {
            log.warn("[소셜] 비활성 사용자라 연동을 건너뜀 — {} userId={}", provider, userId);
            return;
        }
        log.info("[소셜] 기존 회원에 연결 — {} userId={}", provider, userId);
    }

    /** 가입을 확정하고 같은 트랜잭션에서 소셜 연동까지 이어 만들어진 회원 id를 돌려준다 — 가입 검사는 AuthService.signup()을 그대로 쓴다. */
    @Transactional
    public Long signupAndLink(SignupForm form, String provider, String providerUserId) {
        Long userId = authService.signup(form);
        link(userId, provider, providerUserId);
        log.info("[소셜] 새 회원 가입·연결 — {} userId={}", provider, userId);
        return userId;
    }

    /** 가입 화면에 미리 채울, 겹치지 않는 추천 닉네임을 고른다 — 최종 판정은 가입 시 signup()이 한다. */
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
