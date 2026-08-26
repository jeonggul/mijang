package com.example.mijang.user.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.MailProperties;
import com.example.mijang.config.PasswordResetProperties;
import com.example.mijang.user.domain.PasswordResetToken;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.mail.MailTransport;
import com.example.mijang.user.mapper.PasswordResetTokenMapper;
import com.example.mijang.user.mapper.UserMapper;
import com.example.mijang.security.PasswordVersionRegistry;
import com.example.mijang.user.policy.SignupPolicy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정과 변경. 개발명세서(API) AUTH-05
 *
 * <p>가입·로그인과 생명주기가 달라 AuthService 에서 분리했다.
 *
 * <p>재설정 링크는 <b>표에 저장한다.</b> 무상태 토큰은 표가 필요 없는 대신 발급한 링크를
 * 서버가 통제하지 못한다. 저장하면 쓴 링크에 표시를 남겨 확실히 일회용으로 만들고,
 * 새로 요청할 때 이전 링크를 죽이고, 재전송 간격을 걸고, 링크를 눌렀을 때 화면에 들어가기
 * 전에 유효한지 미리 확인할 수 있다.
 *
 * <p>원문은 저장하지 않는다. 해시만 넣고 원문은 메일에만 실린다. 표가 통째로 새어도
 * 그것만으로는 남의 비밀번호를 바꿀 수 없다.
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    /** 토큰 바이트 수. 256비트면 찍어서 맞히는 것은 불가능하다. */
    private static final int TOKEN_BYTES = 32;

    private final UserMapper userMapper;
    private final PasswordResetTokenMapper tokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final MailTransport mailTransport;
    private final MailProperties mailProps;
    private final PasswordResetProperties resetProps;
    private final PasswordVersionRegistry versions;

    /** 예측 가능한 난수로 토큰을 만들면 안 된다. Random 이 아니라 SecureRandom 이다. */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 재설정 링크를 요청한다.
     *
     * <p><b>어떤 경우에도 예외를 던지지 않는다.</b> 없는 이메일에 오류를 주면
     * 이 화면이 계정 조회 도구가 된다(8.1.3). 가입돼 있을 때만 메일이 나가고,
     * 응답은 언제나 같다. 소셜 전용·정지·탈퇴 계정도 조용히 넘긴다.
     */
    @Transactional
    public void requestReset(String email) {
        tokenMapper.deleteExpired();   // 지나간 것 가볍게 정리. 별도 배치가 필요 없다

        User user = userMapper.findByEmail(email);
        if (user == null || !user.isActive() || !user.hasPassword()) {
            return;
        }

        String token = issueToken(user.id());
        if (token == null) {
            return;   // 재전송 간격 안이라 새로 만들지 않았다. 응답은 그대로다
        }

        String resetUrl = mailProps.getBaseUrl() + "/password-reset?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        mailTransport.sendResetLink(user.email(), resetUrl, resetProps.getTokenTtl().toMinutes());
    }

    /**
     * 새 토큰을 발급하고 원문을 돌려준다. 표에는 해시만 들어간다.
     *
     * <p>발급 전에 이전 링크를 전부 죽인다. 유효한 링크가 여러 개 떠 있으면
     * "가장 최근 것만 쓸 수 있다"는 사용자의 기대와 어긋난다.
     *
     * @return 메일에 실을 원문. 재전송 간격 안이면 null
     */
    private String issueToken(Long userId) {
        PasswordResetToken latest = tokenMapper.findLatestActiveByUserId(userId);
        if (latest != null && latest.createdAt() != null
                && latest.createdAt().plus(resetProps.getResendCooldown()).isAfter(LocalDateTime.now())) {
            // 짧은 간격의 반복 요청. 메일 폭탄을 막는다
            return null;
        }
        tokenMapper.invalidateActiveByUserId(userId);

        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        // URL 에 그대로 실을 수 있는 형태. 패딩(=)이 없어 링크가 깔끔하다
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokenMapper.insert(userId, sha256Hex(token),
                LocalDateTime.now().plus(resetProps.getTokenTtl()));
        return token;
    }

    /**
     * 링크로 들어왔을 때 토큰이 쓸 만한지 본다. 화면을 그리기 전에 부른다.
     *
     * <p>이게 없으면 만료된 링크로도 입력 화면이 뜨고, 다 입력한 뒤에야 실패한다.
     *
     * @throws BusinessException 없거나·이미 썼거나·만료됐을 때(400)
     */
    @Transactional(readOnly = true)
    public void validateToken(String token) {
        findValidRow(token);
    }

    /**
     * 링크로 들어온 사용자의 비밀번호를 새로 저장한다.
     *
     * <p>없음·이미 씀·만료를 <b>구분하지 않고</b> 같은 오류로 돌려준다.
     * 나누면 "이 토큰은 이미 쓰였다"는 사실이 확인 도구가 된다.
     *
     * @throws BusinessException 토큰을 믿을 수 없거나(400) 이전과 같은 비밀번호일 때(422)
     */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken row = findValidRow(token);

        /* 여기서 일회용이 확정된다. used_at IS NULL 조건이 붙은 갱신이라
           같은 링크로 동시에 두 번 들어와도 한 쪽만 1을 받는다. */
        if (tokenMapper.markUsed(row.tokenId()) != 1) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "token");
        }

        User user = userMapper.findById(row.userId());
        if (user == null || !user.isActive() || !user.hasPassword()) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "token");
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_UNCHANGED, "password");
        }
        /* 링크로 들어온 사람도 가입과 같은 기준을 받는다.
           재설정만 느슨하면 비밀번호를 잊은 뒤 오히려 약한 값으로 바꿀 수 있다 */
        if (SignupPolicy.containsProfileInfo(newPassword, user.nickname(), user.email())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_TOO_GUESSABLE, "password");
        }

        int changed = userMapper.updatePassword(
                user.id(), passwordEncoder.encode(newPassword), user.passwordHash());
        if (changed == 0) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "token");
        }
        /* 갱신이 password_version 을 +1 한다. 이미 나간 access 토큰은 그 전 값을 들고
           있어 다음 요청에서 걸린다 — 전에는 만료까지 30분을 살았다(8.1.7) */
        versions.record(user.id(), user.passwordVersion() + 1);
    }

    /**
     * 로그인한 사용자가 비밀번호를 바꾼다.
     *
     * <p>이미 인증된 요청이지만 현재 비밀번호를 다시 받는다(8.1.5).
     *
     * @throws BusinessException 현재 비밀번호가 틀리거나(400) 이전과 같을 때(422)
     */
    @Transactional
    public void change(Long userId, String currentPassword, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        /* 정지된 계정은 이미 나간 access 토큰이 만료될 때까지 요청을 보낼 수 있다. */
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        // 소셜 전용 계정. 바꿀 비밀번호가 없다는 사실을 그대로 알려 준다
        if (!user.hasPassword()) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_MISMATCH, "currentPassword");
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_UNCHANGED, "newPassword");
        }
        if (SignupPolicy.containsProfileInfo(newPassword, user.nickname(), user.email())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_TOO_GUESSABLE, "newPassword");
        }

        int changed = userMapper.updatePassword(
                userId, passwordEncoder.encode(newPassword), user.passwordHash());
        if (changed == 0) {
            // 그 사이 다른 요청이 먼저 바꿨다. 지금 받은 현재 비밀번호는 이미 옛것이다
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_MISMATCH, "currentPassword");
        }
        versions.record(userId, user.passwordVersion() + 1);
    }

    /** 존재 → 사용 여부 → 만료 순으로 본다. 실패는 전부 같은 오류다. */
    private PasswordResetToken findValidRow(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "token");
        }
        PasswordResetToken row = tokenMapper.findByTokenHash(sha256Hex(token));
        if (row == null || !row.isUnused() || row.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "token");
        }
        return row;
    }

    /** 표에 넣고 찾을 때 쓰는 해시. 원문은 어디에도 저장하지 않는다. */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 JDK 표준이라 실제로는 나지 않는다
            throw new IllegalStateException(e);
        }
    }
}
