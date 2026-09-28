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

/** 비밀번호 재설정·변경을 담당한다. 재설정 토큰은 해시만 표에 저장해 일회용으로 통제한다. */
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

    /** 재설정 링크를 요청한다. 계정 존재 여부가 드러나지 않도록 어떤 경우에도 예외를 던지지 않는다. */
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

    /** 이전 링크를 무효화하고 새 토큰 원문을 돌려준다. 재전송 간격 안이면 null 이다. */
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

    /** 화면을 그리기 전에 토큰이 유효한지 확인한다. */
    @Transactional(readOnly = true)
    public void validateToken(String token) {
        findValidRow(token);
    }

    /** 재설정 링크로 들어온 사용자의 비밀번호를 새로 저장한다. 토큰 실패 사유는 구분하지 않고 같은 오류로 돌려준다. */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken row = findValidRow(token);

        // used_at IS NULL 조건 갱신이라 같은 링크로 동시에 들어와도 한 쪽만 성공한다.
        if (tokenMapper.markUsed(row.tokenId()) != 1) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "token");
        }

        // hasPassword() 는 현재 스키마에서 항상 참이지만 스키마 회귀 대비로 남긴다.
        User user = userMapper.findById(row.userId());
        if (user == null || !user.isActive() || !user.hasPassword()) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "token");
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_UNCHANGED, "password");
        }
        // 재설정도 가입과 같은 비밀번호 기준을 적용한다.
        if (SignupPolicy.containsProfileInfo(newPassword, user.nickname(), user.email())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_TOO_GUESSABLE, "password");
        }

        int changed = userMapper.updatePassword(
                user.id(), passwordEncoder.encode(newPassword), user.passwordHash());
        if (changed == 0) {
            throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID, "token");
        }
        // password_version +1 로 이미 나간 access 토큰을 즉시 무효화한다.
        versions.record(user.id(), user.passwordVersion() + 1);
    }

    /** 로그인한 사용자의 비밀번호를 바꾼다. 인증된 요청이라도 현재 비밀번호를 다시 확인한다. */
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
