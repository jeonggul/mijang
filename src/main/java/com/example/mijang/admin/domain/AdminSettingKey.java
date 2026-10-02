/*
 * AdminSettingKey — 운영 설정에서 쓸 수 있는 키
 *
 * 이 파일이 하는 일
 *   admin_settings 는 키·값 한 표라 타입 안전이 없다. 그 자리를 여기가 메운다.
 *   알려진 키만 저장되게 막고, 값의 형태(참거짓 · 정수)와 기본값을 함께 들고 있다.
 *   화면이 보낸 문자열을 그대로 믿고 넣으면 나중에 읽는 쪽이 전부 방어해야 한다.
 */
package com.example.mijang.admin.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 운영 설정 키. 화면 SR-013 운영 설정 탭
 *
 * <p>값의 형태가 둘뿐이다 — 참거짓과 정수. 정수는 <b>허용 목록</b>을 함께 두어
 * 화면의 알약에 없는 값이 들어오는 것을 막는다. 목록이 비어 있으면 0 이상이면 된다.
 */
public enum AdminSettingKey {

    /* ── 데이터 갱신 ── */
    /** 뉴스 수집 주기(분). Finnhub 한도에 직접 영향을 준다. */
    NEWS_REFRESH_MINUTES("news.refresh.minutes", Type.INT, "60", List.of(30, 60, 180)),
    /** 환율 수집 실패 시 직전 영업일 값으로 대체할지. */
    FX_FALLBACK_ENABLED("fx.fallback.enabled", Type.BOOL, "true", List.of()),
    /** 실시간 시세 공급. 끄면 전 회원에게 지연 시세만 나간다. */
    QUOTE_LIVE_ENABLED("quote.live.enabled", Type.BOOL, "true", List.of()),

    /* ── 커뮤니티 ── */
    /** 가입 후 이 일수 동안 글을 쓸 수 없다. 0 이면 제한 없음. */
    COMMUNITY_WRITE_DELAY_DAYS("community.write.delay.days", Type.INT, "1", List.of(0, 1, 3)),
    /** 신고가 이 횟수를 넘으면 자동으로 숨긴다. */
    COMMUNITY_AUTOHIDE_REPORTS("community.autohide.reports", Type.INT, "5", List.of(3, 5, 10)),
    /** 금칙어 필터. 리딩방·종목 추천 표현을 막는다. */
    COMMUNITY_BADWORD_ENABLED("community.badword.enabled", Type.BOOL, "true", List.of()),

    /* ── 서비스 ── */
    /** 신규 가입 허용. */
    SIGNUP_ENABLED("signup.enabled", Type.BOOL, "true", List.of()),
    /** 점검 모드. 켜면 관리자를 제외한 모든 접속이 막힌다. */
    MAINTENANCE_ENABLED("maintenance.enabled", Type.BOOL, "false", List.of());

    /** 값의 형태. 둘뿐이라 enum 하나로 충분하다. */
    public enum Type { BOOL, INT }

    private final String key;
    private final Type type;
    private final String defaultValue;
    private final List<Integer> allowed;

    AdminSettingKey(String key, Type type, String defaultValue, List<Integer> allowed) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.allowed = allowed;
    }

    public String key() {
        return key;
    }

    public String defaultValue() {
        return defaultValue;
    }

    /** 저장된 키 문자열로 되찾는다. 모르는 키면 비어 있다. */
    public static Optional<AdminSettingKey> of(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }

    /**
     * 화면이 보낸 값이 이 키에 쓸 수 있는 값인지.
     *
     * <p>참거짓은 `true`/`false` 만, 정수는 허용 목록 안에 있어야 한다.
     * 목록이 비어 있으면 0 이상이면 된다.
     */
    public boolean accepts(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (type == Type.BOOL) {
            return "true".equals(v) || "false".equals(v);
        }
        try {
            int n = Integer.parseInt(v);
            return allowed.isEmpty() ? n >= 0 : allowed.contains(n);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 저장 형태로 다듬는다. 대소문자·공백 때문에 같은 값이 두 모양으로 남지 않게 한다. */
    public String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
