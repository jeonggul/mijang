package com.example.mijang.user.service;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.mapper.AdminUserMapper;
import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.security.JwtProvider;
import com.example.mijang.security.PasswordVersionRegistry;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.LoginForm;
import com.example.mijang.user.dto.LoginResponse;
import com.example.mijang.user.dto.AvailabilityResponse;
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.mapper.OAuthAccountMapper;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.policy.SignupPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입·로그인·토큰 갱신·탈퇴를 담당한다. 쿠키는 컨트롤러가 굽고 여기서는 토큰 문자열까지만 만든다. */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 계정이 없을 때 대신 검증할 해시. BCrypt 를 같은 비용으로 태워 타이밍 차이를 없앤다. */
    private static final String DUMMY_HASH =
            "$2a$10$ZZZZZZZZZZZZZZZZZZZZZeS7Z5nQ0Xk8Yq9Q0Yq9Q0Yq9Q0Yq9Q0y";

    private final UserMapper userMapper;
    private final OAuthAccountMapper oauthMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AdminSettingService settingService;
    private final LoginAttemptService loginAttemptService;
    private final AdminUserMapper adminUserMapper;
    private final PasswordVersionRegistry versions;

    /** 가입 전 닉네임 사용 가능 여부를 형식 → 금지어 → 중복 순으로 확인한다. */
    @Transactional(readOnly = true)
    public AvailabilityResponse checkNickname(String nickname) {
        String reason = SignupPolicy.validateNickname(nickname);
        if (reason != null) {
            return AvailabilityResponse.no(reason);
        }
        if (userMapper.countByNickname(nickname) > 0) {
            return AvailabilityResponse.no("이미 사용 중인 닉네임입니다");
        }
        return AvailabilityResponse.ok("사용 가능한 닉네임입니다");
    }

    /** 회원가입을 처리하고 생성된 사용자 id 를 돌려준다. 토큰은 발급하지 않는다. */
    @Transactional
    public Long signup(SignupForm form) {
        // 가입 차단 확인은 계정 존재 여부가 새지 않도록 중복 검사보다 앞에 둔다.
        if (!settingService.isOn(AdminSettingKey.SIGNUP_ENABLED)) {
            throw new BusinessException(ErrorCode.SIGNUP_DISABLED);
        }
        if (userMapper.countByEmail(form.getEmail()) > 0) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED, "email");
        }
        // 소셜 가입 경로는 @Valid 를 거치지 않으므로 이메일 길이를 여기서 다시 본다.
        if (form.getEmail() != null && form.getEmail().length() > SignupPolicy.EMAIL_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_REQUEST, "email");
        }
        // 형식은 @Pattern 이 걸렀다. 여기서는 금지어만 본다
        if (SignupPolicy.containsForbiddenWord(form.getNickname())) {
            throw new BusinessException(ErrorCode.AUTH_NICKNAME_FORBIDDEN, "nickname");
        }
        // 닉네임·이메일이 들어간 비밀번호를 막는다.
        if (SignupPolicy.containsProfileInfo(
                form.getPassword(), form.getNickname(), form.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_TOO_GUESSABLE, "password");
        }
        if (userMapper.countByNickname(form.getNickname()) > 0) {
            throw new BusinessException(ErrorCode.AUTH_NICKNAME_DUPLICATED, "nickname");
        }

        var param = new UserMapper.UserInsert(
                form.getEmail(),
                passwordEncoder.encode(form.getPassword()),
                form.getNickname());
        try {
            userMapper.insert(param);
        } catch (DuplicateKeyException e) {
            // 확인과 저장 사이에 같은 이메일이 먼저 들어온 경우다. uk_users_email 이 잡는다.
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED, "email");
        }
        return param.getId();
    }

    /** 자격을 검증하고 토큰 한 쌍을 만든다. 계정 존재 여부가 새지 않도록 모든 실패를 같은 오류로 돌려준다. */
    @Transactional(readOnly = true)
    public Tokens login(LoginForm form, String clientIp) {
        // 잠금 상태도 자격 증명 오류와 같은 응답으로 감춘다.
        if (loginAttemptService.isBlocked(form.getEmail(), clientIp)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        User user = userMapper.findByEmail(form.getEmail());

        // 계정이 없어도 BCrypt 를 한 번 태워 응답 시간 차이로 계정 존재가 새는 것을 막는다.
        String hash = (user != null && user.hasPassword()) ? user.passwordHash() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(form.getPassword(), hash);

        if (user == null || !user.hasPassword() || !passwordMatches || !user.isActive()) {
            loginAttemptService.recordFailure(form.getEmail(), clientIp);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        loginAttemptService.recordSuccess(form.getEmail());
        return issue(user, form.isRememberMe());
    }

    /** refresh 쿠키로 토큰을 다시 발급한다. 사용자를 DB 에서 다시 읽어 정지·탈퇴 계정이 갱신으로 되살아나지 않게 한다. */
    @Transactional(readOnly = true)
    public Tokens refresh(String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        Claims claims;
        try {
            claims = jwtProvider.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        if (!jwtProvider.isType(claims, JwtProvider.TYPE_REFRESH)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        User user = userMapper.findById(jwtProvider.userId(claims));
        if (user == null || !user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        // password_version 이 다른 토큰은 거부한다. 비밀번호 변경 뒤 옛 refresh 가 살아남지 못하게 한다.
        if (jwtProvider.passwordVersion(claims) != user.passwordVersion()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        // 처음 로그인할 때의 유지 선택을 그대로 이어받는다
        return issue(user, jwtProvider.remember(claims));
    }

    /** 소셜 로그인용 토큰을 발급한다. 신원 확인은 제공자가 이미 했고 발급 경로만 공유한다. */
    public Tokens issueForSocial(User user) {
        return issue(user, true);
    }

    /** 검증이 끝난 사용자로 토큰 한 쌍과 응답용 정보를 만든다. 로그인·갱신·소셜이 함께 쓴다. */
    private Tokens issue(User user, boolean remember) {
        String access = jwtProvider.createAccessToken(
                user.id(), user.nickname(), user.role(), user.passwordVersion());
        String refresh = jwtProvider.createRefreshToken(user.id(), remember, user.passwordVersion());
        var info = new LoginResponse.LoginUserInfo(
                user.id(), user.nickname(), user.role(), user.baseCurrency());
        return new Tokens(access, refresh, remember, info);
    }

    /** 회원 탈퇴를 처리한다. 행을 지우지 않고 상태만 바꾸며, 비밀번호를 다시 확인한다. */
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 정지된 계정이 남은 access 토큰으로 탈퇴하지 못하게 막는다.
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        // 소셜 전용 계정은 확인할 비밀번호가 없어 이 경로로는 탈퇴할 수 없다.
        if (!user.hasPassword()) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_MISMATCH, "password");
        }

        // 유일한 활성 관리자는 탈퇴할 수 없다. FOR UPDATE 잠금 조회로 동시 탈퇴·정지와 직렬화한다.
        if ("ADMIN".equals(user.role())) {
            List<Long> activeAdminIds = adminUserMapper.lockActiveAdminIds();
            if (activeAdminIds.size() <= 1 && activeAdminIds.contains(userId)) {
                throw new BusinessException(ErrorCode.ADMIN_LAST_ACTIVE);
            }
        }

        // CAS 갱신 — 확인한 비밀번호 해시 그대로일 때만 탈퇴되고, 그 사이 변경·정지가 끼면 0행이 된다.
        int changed = userMapper.withdraw(userId, user.passwordHash());
        if (changed != 1) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        oauthMapper.deleteByUser(userId);

        // 옛 access 토큰 무효화. 스냅샷+1 이 아니라 방금 확정된 password_version 을 다시 읽어 기록한다.
        int newVersion = userMapper.findPasswordVersion(userId);
        versions.record(userId, newVersion);
    }

    /** 컨트롤러가 쿠키를 구울 수 있도록 refresh 까지 함께 넘긴다. */
    public record Tokens(String accessToken, String refreshToken, boolean remember,
                         LoginResponse.LoginUserInfo user) {

        /** 응답 본문용으로 변환한다. refreshToken 은 HttpOnly 쿠키 전용이라 일부러 뺀다. */
        public LoginResponse toResponse() {
            return new LoginResponse(accessToken, user);
        }
    }
}
