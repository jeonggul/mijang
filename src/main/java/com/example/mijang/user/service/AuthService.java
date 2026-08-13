package com.example.mijang.user.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.security.JwtProvider;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.LoginForm;
import com.example.mijang.user.dto.LoginResponse;
import com.example.mijang.user.dto.AvailabilityResponse;
import com.example.mijang.user.dto.SignupForm;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.user.policy.SignupPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입·로그인·토큰 갱신. 개발명세서(API) AUTH-001~003
 *
 * <p>쿠키를 굽는 일은 컨트롤러가 한다. 이 클래스는 토큰 문자열까지만 만든다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 계정이 없을 때 대신 검증할 해시. 어떤 비밀번호와도 맞지 않는다.
     *
     * <p>값 자체는 의미가 없고 BCrypt 를 같은 비용으로 한 번 돌리게 하는 것이 목적이다.
     * 상수라 매 요청 새로 만들지 않는다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$ZZZZZZZZZZZZZZZZZZZZZeS7Z5nQ0Xk8Yq9Q0Yq9Q0Yq9Q0Yq9Q0y";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * 가입 전 닉네임 사용 가능 확인. 형식 → 금지어 → 중복 순으로 본다.
     *
     * <p>이메일에는 같은 확인을 두지 않는다. 인증 없이 부를 수 있는 조회는 그대로
     * "이 주소가 가입돼 있는가"를 알려 주는 통로가 되어, 로그인 오류를 하나로 합쳐
     * 막아 둔 계정 존재 여부가 그쪽으로 새어 나간다. 닉네임은 게시글에 그대로 노출되는
     * 공개 값이라 같은 문제가 없다.
     *
     * <p>순서에 이유가 있다. 형식이 틀린 값으로 DB 를 조회할 이유가 없고,
     * 금지어는 DB 없이 판정되므로 조회 전에 걸러 낸다.
     */
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

    /**
     * AUTH-001 회원가입.
     *
     * <p>중복을 먼저 보고 저장한다. 검사와 저장 사이에 다른 요청이 끼어들 수 있는데,
     * 이메일은 UNIQUE 제약이 잡아 주고(그때는 DataIntegrityViolationException 이 난다)
     * 닉네임은 제약이 없어 통과할 수 있다. 2.2 의 주의와 같은 이야기다.
     *
     * <p>가입만 하고 토큰은 발급하지 않는다. 화면도 가입 후 로그인 화면으로 보낸다.
     *
     * @return 생성된 사용자 id
     * @throws BusinessException 이메일 또는 닉네임이 이미 있을 때(409)
     */
    @Transactional
    public Long signup(SignupForm form) {
        if (userMapper.countByEmail(form.getEmail()) > 0) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED, "email");
        }
        // 형식은 @Pattern 이 걸렀다. 여기서는 금지어만 본다
        if (SignupPolicy.containsForbiddenWord(form.getNickname())) {
            throw new BusinessException(ErrorCode.AUTH_NICKNAME_FORBIDDEN, "nickname");
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
            // 확인과 저장 사이에 같은 이메일이 먼저 들어온 경우. uk_users_email 이 잡아 준다.
            // 잡지 않으면 500 이 나가고 화면은 "일시적인 오류"를 띄운다 — 실제로는 중복이다.
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED, "email");
        }
        return param.getId();
    }

    /**
     * AUTH-002 로그인.
     *
     * <p>이메일이 없는 경우와 비밀번호가 틀린 경우를 <b>같은 오류</b>로 돌려준다.
     * 나누면 계정 존재 여부가 새어 나간다 (미장-API명세서 2장).
     */
    /**
     * 자격을 검증하고 토큰 두 개를 만든다.
     *
     * <p>세 갈래의 실패(없는 이메일·소셜 전용 계정·비밀번호 불일치)를 하나로 합쳐
     * 같은 오류로 던진다. 나누면 어떤 이메일이 가입돼 있는지 알아낼 수 있다.
     *
     * <p>정지 계정도 같은 오류다. "정지된 계정입니다"라고 알려 주면 그 자체로
     * 계정이 있다는 정보가 된다.
     *
     * @throws BusinessException 어떤 이유든 로그인 실패면 AUTH_INVALID_CREDENTIALS
     */
    @Transactional(readOnly = true)
    public Tokens login(LoginForm form) {
        User user = userMapper.findByEmail(form.getEmail());

        // 계정이 없거나 소셜 전용이어도 BCrypt 를 한 번 태운다.
        // 짧게 끊으면 응답 시간이 갈려 "가입된 이메일인가"가 시간으로 드러난다.
        // 오류 문구를 하나로 합쳐 막아 둔 것이 타이밍으로 새는 것을 여기서 닫는다.
        String hash = (user != null && user.hasPassword()) ? user.passwordHash() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(form.getPassword(), hash);

        if (user == null || !user.hasPassword() || !passwordMatches || !user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        return issue(user, form.isRememberMe());
    }

    /**
     * AUTH-03 토큰 갱신. refresh 쿠키로만 호출된다.
     *
     * <p>refresh 를 서버에 저장하지 않으므로 여기서 하는 검증은 서명·만료·종류뿐이다.
     * 사용자 상태는 매번 다시 조회해 정지·탈퇴 계정이 갱신으로 되살아나지 않게 한다.
     */
    /**
     * refresh 쿠키로 토큰을 다시 발급한다.
     *
     * <p>서명·만료·종류를 본 뒤 <b>사용자를 DB 에서 다시 읽는다.</b> 토큰 안의 값만 믿으면
     * 정지·탈퇴된 계정이 14일 동안 갱신으로 되살아난다.
     *
     * <p>만료와 위조를 구분하지 않고 모두 AUTH_TOKEN_EXPIRED 로 돌려준다.
     * 화면 입장에서 할 일은 어느 쪽이든 "다시 로그인"으로 같다.
     *
     * @param refreshToken 쿠키에서 읽은 값. null 이면 비로그인으로 본다
     * @throws BusinessException 토큰이 없거나(401) 못 믿을 때(401)
     */
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
        /* 비밀번호가 바뀐 뒤에 발급된 토큰만 받는다.
           이 검사가 없으면 비밀번호를 유출당한 사람이 재설정을 해도 공격자의 쿠키가
           그대로 살아 있고, 갱신 때마다 만료가 다시 14일로 늘어나 사실상 끊기지 않는다.
           갱신 길목이라 어차피 사용자를 다시 읽으므로 조회가 늘지 않는다. */
        if (jwtProvider.passwordVersion(claims) != user.passwordVersion()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }
        // 처음 로그인할 때의 유지 선택을 그대로 이어받는다
        return issue(user, jwtProvider.remember(claims));
    }

    /**
     * 검증이 끝난 사용자로 토큰 한 쌍과 응답용 정보를 만든다.
     *
     * <p>로그인과 갱신이 같은 결과를 내야 해서 한 곳에 모았다.
     * 한쪽만 고치면 두 경로의 토큰 내용이 달라진다.
     */
    private Tokens issue(User user, boolean remember) {
        String access = jwtProvider.createAccessToken(user.id(), user.nickname(), user.role());
        String refresh = jwtProvider.createRefreshToken(user.id(), remember, user.passwordVersion());
        var info = new LoginResponse.LoginUserInfo(
                user.id(), user.nickname(), user.role(), user.baseCurrency());
        return new Tokens(access, refresh, remember, info);
    }

    /**
     * AUTH-06 회원 탈퇴.
     *
     * <p>행을 지우지 않고 상태만 바꾼다(9.1.1). 매매 기록·게시글이 외래키를 타고
     * 함께 사라지는 것을 막고, 30일 안에는 되돌릴 여지를 남긴다.
     *
     * <p>돌이킬 수 없는 동작이라 비밀번호를 다시 받는다. 소셜 전용 계정은 확인할
     * 비밀번호가 없어 지금은 탈퇴할 수 없다 — 소셜 로그인(AUTH-07)과 함께 정리한다.
     *
     * @throws BusinessException 사용자가 없거나(404) 비밀번호가 틀릴 때(400)
     */
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 정지된 계정은 남은 access 토큰으로 탈퇴까지 밀어붙일 수 있었다
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        /* 소셜 전용 계정은 확인할 비밀번호가 없다. "비밀번호가 틀렸다"고 답하면
           한 번도 만든 적 없는 값을 맞히라는 말이 된다. 사실대로 알려 준다.
           이 경로로는 탈퇴할 수 없다는 것이 지금의 한계다(10장). */
        if (!user.hasPassword()) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_MISMATCH, "password");
        }
        userMapper.withdraw(userId);
    }

    /** 컨트롤러가 쿠키를 구울 수 있도록 refresh 까지 함께 넘긴다. */
    public record Tokens(String accessToken, String refreshToken, boolean remember,
                         LoginResponse.LoginUserInfo user) {

        /**
         * 응답 본문용으로 변환한다.
         *
         * <p>refreshToken 을 일부러 뺀다. 갱신 토큰은 HttpOnly 쿠키로만 오가야 하고,
         * 본문에 실리면 JS 가 읽을 수 있어 쿠키로 감춘 의미가 사라진다.
         */
        public LoginResponse toResponse() {
            return new LoginResponse(accessToken, user);
        }
    }
}
