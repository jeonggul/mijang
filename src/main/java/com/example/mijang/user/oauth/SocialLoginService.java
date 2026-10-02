/*
 * SocialLoginService — 소셜 계정을 우리 회원에 잇는다
 *
 * 이 파일이 하는 일
 *   제공자가 준 신원 하나를 받아, 세 갈래 중 하나로 답한다.
 *
 *     이미 연결돼 있다            → 그 회원으로 로그인
 *     처음이고 이메일도 처음      → 계정을 만들지 않고 가입 화면으로 보류한다
 *     처음인데 이메일이 이미 있다 → 연결하지 않고 "비밀번호로 확인해 달라" 고 돌려보낸다
 *
 *   왜 처음 보는 사람에게 계정을 바로 만들어 주지 않는가
 *     비밀번호 없이 계정을 만들면 편하긴 하지만, 그 계정은 소셜 연동이 유일한 문이 된다.
 *     연동을 끊는 순간 들어올 방법이 없어지고, 비밀번호 찾기로도 복구되지 않는다 —
 *     {@code PasswordService.reset()} 은 {@code hasPassword()} 가 false 면 토큰 자체를
 *     무효로 본다. 그래서 가입 화면에서 비밀번호를 직접 받은 뒤에만 계정을 만든다.
 *
 *   왜 이메일이 겹치는 경우를 자동으로 잇지 않는가
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
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.mapper.OAuthAccountMapper;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.service.AuthService;
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
    private final AuthService authService;

    /**
     * 결과 세 갈래.
     *
     * <p>{@code boolean needsLink()} 하나로는 갈래가 셋이 된 지금을 담지 못한다.
     * 갈래를 이름으로 두면 부르는 쪽이 빠뜨린 경우를 컴파일러가 짚어 준다.
     *
     * @param user     로그인시킬 회원. 보류 갈래에서는 null
     * @param email    보류 중인 이메일. 로그인 갈래에서는 null
     * @param nickname 가입 화면에 미리 채울 추천 닉네임. 가입 보류에서만 채운다
     */
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

        /* 여기서 계정을 만들지 않는다. 비밀번호 없이 만들면 연동을 끊는 순간
           들어올 문이 사라지고, 비밀번호 찾기로도 복구되지 않는다(PasswordService.reset
           은 hasPassword() 가 false 면 토큰을 무효로 본다). 가입 화면에서 비밀번호를
           받은 뒤에 만든다 */
        log.info("[소셜] 새 회원 — 가입 화면으로 보류 {} {}", profile.provider(), mask(email));
        return Result.needsSignup(email, profile.provider(),
                uniqueNickname(profile.nickname(), email));
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
        /* 위 existsByUserAndProvider 가 이미 이 트랜잭션의 읽기 스냅샷을 잡아 버린다.
           그 뒤 다른 트랜잭션이 탈퇴를 커밋해도 일반 SELECT 로는 못 본다 — FOR UPDATE 로
           행을 잠그고 최신 커밋 상태를 읽어야 withdraw 와 순서가 맞물린다 */
        String status = userMapper.lockUserStatusForUpdate(userId);   // 최신 커밋 상태를 잠그고 읽는다
        if (!"ACTIVE".equals(status)) {
            log.warn("[소셜] 비활성 사용자라 연동을 건너뜀 — {} userId={}", provider, userId);
            return;
        }
        // insert 의 WHERE status='ACTIVE' 는 위 잠금과 같은 문장 안이라 방어적 여분일 뿐,
        // 진짜 경합 차단은 FOR UPDATE 잠금이 한다 — 0행은 이제 사실상 나오지 않는다
        int inserted = oauthMapper.insert(userId, provider, providerUserId);
        if (inserted == 0) {
            log.warn("[소셜] 비활성 사용자라 연동을 건너뜀 — {} userId={}", provider, userId);
            return;
        }
        log.info("[소셜] 기존 회원에 연결 — {} userId={}", provider, userId);
    }

    /**
     * 소셜로 처음 온 사람의 가입을 확정하고 그 자리에서 잇는다.
     *
     * <p>가입 검사를 여기서 새로 짜지 않고 {@code AuthService.signup()} 을 그대로 쓴다.
     * 그쪽에는 가입 잠금(SIGNUP_ENABLED)·이메일 중복·닉네임 금지어·닉네임 중복·
     * 추측 가능한 비밀번호 검사가 이미 모여 있다. 여기에 따로 두면 언젠가 한쪽만
     * 느슨해진다 — 그리고 느슨해지는 쪽은 늘 나중에 만든 쪽이다.
     *
     * <p>한 트랜잭션으로 묶는 이유 — 계정만 생기고 연결이 빠지면 사용자는 방금 만든
     * 소셜로 다시 들어왔을 때 비밀번호 확인 화면을 만난다. 틀린 상태는 아니지만 놀란다.
     *
     * @return 만들어진 회원 id
     */
    @Transactional
    public Long signupAndLink(SignupForm form, String provider, String providerUserId) {
        Long userId = authService.signup(form);
        link(userId, provider, providerUserId);
        log.info("[소셜] 새 회원 가입·연결 — {} userId={}", provider, userId);
        return userId;
    }

    /**
     * 겹치지 않는 닉네임을 고른다. 가입 화면에 <b>미리 채워 줄 추천값</b>이다.
     *
     * <p>확정이 아니다 — 사용자가 화면에서 고칠 수 있고, 최종 판정은 가입 시
     * {@code AuthService.signup()} 이 한다. 여기서 겹치지 않는 값을 주는 이유는
     * 화면을 열자마자 "이미 사용 중" 이 떠 있는 상태를 피하기 위해서다.
     */
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
