package com.example.mijang.security;

import com.example.mijang.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** JWT 를 발급·검증한다. 서명 키는 이 클래스 밖으로 나가지 않는다. */
@Component
public class JwtProvider {

    /** 토큰 종류. 갱신용 토큰으로 API 를 호출하는 것을 막는다. */
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_ROLE = "role";

    /** 서버 인스턴스 식별자. 재시작하면 바뀌어 이전 토큰이 전부 무효가 된다. */
    private static final String CLAIM_INSTANCE = "sid";

    /** 로그인 상태 유지 여부. 갱신할 때 이 값을 이어받아 쿠키 수명을 유지한다. */
    private static final String CLAIM_REMEMBER = "rm";

    /** 발급 당시의 비밀번호 세대. 비밀번호가 바뀌면 어긋나 토큰이 막힌다. */
    private static final String CLAIM_PW_VERSION = "pv";

    private final SecretKey key;
    private final JwtProperties props;
    /** 애플리케이션이 뜰 때 한 번 만들어지고 죽을 때까지 바뀌지 않는다. */
    private final String instanceId = UUID.randomUUID().toString();

    /** 서명 키를 만든다. secret 이 없거나 32바이트 미만이면 기동 시점에 죽는다. */
    public JwtProvider(JwtProperties props) {
        byte[] secret = props.getSecret() == null
                ? new byte[0]
                : props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "mijang.jwt.secret 이 없거나 32바이트 미만입니다. "
                    + "application-secret.properties 를 확인하세요. (HS256 최소 길이)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.props = props;
    }

    /** access token 을 만든다. 식별자·닉네임·권한과 비밀번호 세대를 담는다. */
    public String createAccessToken(Long userId, String nickname, String role,
                                    int passwordVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_INSTANCE, instanceId)
                .claim(CLAIM_NICKNAME, nickname)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_PW_VERSION, passwordVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getAccessTtl())))
                .signWith(key)
                .compact();
    }

    /** 갱신 전용 refresh token 을 만든다. 식별자만 담는다. */
    public String createRefreshToken(Long userId, boolean remember, int passwordVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_INSTANCE, instanceId)
                .claim(CLAIM_REMEMBER, remember)
                .claim(CLAIM_PW_VERSION, passwordVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getRefreshTtl())))
                .signWith(key)
                .compact();
    }

    /** 토큰에 담긴 비밀번호 세대를 꺼낸다. 클레임이 없으면 0 으로 본다. */
    public int passwordVersion(Claims claims) {
        Integer v = claims.get(CLAIM_PW_VERSION, Integer.class);
        return v == null ? 0 : v;
    }

    /** 서명·만료를 검증하고 클레임을 돌려준다. */
    public Claims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // 이전 서버 인스턴스에서 발급된 토큰은 서명이 맞아도 받지 않는다.
        if (!instanceId.equals(claims.get(CLAIM_INSTANCE, String.class))) {
            throw new JwtException("이전 서버 인스턴스에서 발급된 토큰입니다");
        }
        return claims;
    }

    /** 토큰 종류를 확인한다. refresh 로 일반 API 를 호출하는 것을 막는 검사다. */
    public boolean isType(Claims claims, String type) {
        return type.equals(claims.get(CLAIM_TYPE, String.class));
    }

    /** subject 에 넣어 둔 사용자 식별자를 꺼낸다. */
    public Long userId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /** access token 에만 있는 닉네임. refresh 에서는 null 이다. */
    public String nickname(Claims claims) {
        return claims.get(CLAIM_NICKNAME, String.class);
    }

    /** access token 에만 있는 권한. refresh 에서는 null 이다. */
    public String role(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    /** refresh token 에 담긴 로그인 상태 유지 여부. 값이 없으면 유지하지 않는 것으로 본다. */
    public boolean remember(Claims claims) {
        return Boolean.TRUE.equals(claims.get(CLAIM_REMEMBER, Boolean.class));
    }

}
