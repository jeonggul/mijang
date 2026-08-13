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

/**
 * 토큰 발급과 검증. 서명 키는 이 클래스 밖으로 나가지 않는다.
 *
 * <p>토큰에는 서버 인스턴스 식별자가 함께 들어간다. 재시작하면 그 값이 바뀌어 이전 토큰이
 * 모두 무효가 되므로, 로그인 유지가 서버 수명을 넘지 않는다.
 *
 * <p>access 에는 화면·API 가 매 요청 쓰는 최소 정보(식별자·닉네임·권한)만 담는다.
 * 이메일처럼 바뀔 수 있는 값을 넣으면 토큰이 만료될 때까지 옛 값이 돌아다닌다.
 * refresh 에는 식별자만 담는다.
 */
@Component
public class JwtProvider {

    /** 토큰 종류. 갱신용 토큰으로 API 를 호출하는 것을 막는다. */
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_ROLE = "role";

    /**
     * 서버 인스턴스 식별자.
     *
     * <p>이 값이 토큰에 박히고 검증 때 현재 값과 대조된다. 서버가 재시작하면 새로 만들어지므로
     * 이전에 발급한 토큰은 전부 통과하지 못한다. "로그인 유지는 서버가 떠 있는 동안만"이라는
     * 요구가 여기서 구현된다.
     *
     * <p>서명 키를 매번 새로 만드는 방법도 있지만 그러면 설정의 secret 이 무의미해진다.
     * 클레임 하나로 같은 효과를 내면서 키는 설정을 그대로 쓴다.
     */
    private static final String CLAIM_INSTANCE = "sid";

    /** 로그인 상태 유지 여부. 갱신할 때 이 값을 이어받아 쿠키 수명을 유지한다. */
    private static final String CLAIM_REMEMBER = "rm";

    /**
     * 발급 당시의 비밀번호 세대. 비밀번호가 바뀌면 DB 값이 올라가 이 값과 어긋난다.
     *
     * <p>refresh 에만 담는다. access 는 30분이면 만료되므로 갱신 길목만 막으면
     * 오래 사는 세션이 끊긴다. 매 요청 DB 를 보지 않기 위한 선택이다.
     */
    private static final String CLAIM_PW_VERSION = "pv";

    private final SecretKey key;
    private final JwtProperties props;
    /** 애플리케이션이 뜰 때 한 번 만들어지고 죽을 때까지 바뀌지 않는다. */
    private final String instanceId = UUID.randomUUID().toString();

    /**
     * 서명 키를 만든다.
     *
     * <p>키가 짧으면 서명이 약해지므로 여기서 바로 막는다. 뜨고 나서 토큰 발급 시점에
     * 실패하면 원인을 찾기 어렵다. 기동 때 죽는 편이 낫다.
     *
     * @throws IllegalStateException secret 이 없거나 32바이트 미만일 때
     */
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

    /**
     * 매 요청에 쓸 access token 을 만든다.
     *
     * <p>subject 에 사용자 식별자를, 클레임에 닉네임·권한을 담는다.
     * 이 세 값이면 화면 헤더 표시와 권한 판정이 되므로 요청마다 DB 를 보지 않아도 된다.
     *
     * @param role users.role 값 그대로("USER"·"ADMIN"). ROLE_ 접두사는 필터가 붙인다
     */
    public String createAccessToken(Long userId, String nickname, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_INSTANCE, instanceId)
                .claim(CLAIM_NICKNAME, nickname)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getAccessTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * 갱신 전용 token 을 만든다.
     *
     * <p>식별자만 담는다. 닉네임·권한을 넣으면 14일 동안 옛 값이 남고,
     * 갱신 때 어차피 DB 에서 다시 읽으므로 담을 이유가 없다.
     */
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

    /**
     * refresh 토큰이 담고 있는 비밀번호 세대.
     *
     * <p>이 클레임이 생기기 전에 발급된 토큰에는 값이 없다. 그때는 0 으로 본다 —
     * users.password_version 의 기본값과 같아서, 배포 직후 로그인 상태가 풀리지 않는다.
     */
    public int passwordVersion(Claims claims) {
        Integer v = claims.get(CLAIM_PW_VERSION, Integer.class);
        return v == null ? 0 : v;
    }

    /**
     * 서명·만료를 검증하고 클레임을 돌려준다.
     *
     * @throws JwtException 서명 불일치·만료·형식 오류
     */
    public Claims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // 지난 실행에서 발급된 토큰은 서명이 맞아도 받지 않는다
        if (!instanceId.equals(claims.get(CLAIM_INSTANCE, String.class))) {
            throw new JwtException("이전 서버 인스턴스에서 발급된 토큰입니다");
        }
        return claims;
    }

    /**
     * 토큰 종류를 확인한다.
     *
     * <p>이 검사가 없으면 refresh token 으로 일반 API 를 호출할 수 있다.
     * 수명이 14일이라 사실상 만료 없는 열쇠가 된다.
     */
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
