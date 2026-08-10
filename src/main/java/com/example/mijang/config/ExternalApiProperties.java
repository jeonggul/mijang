package com.example.mijang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 API 접속 정보 바인딩.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.config
 *
 * <p>주소·한도는 {@code application.properties}, 키는 {@code application-secret.properties}(git 미추적)에
 * 나눠 둔다. 둘 다 {@code mijang.external.*} 한 곳으로 바인딩되므로 코드에서는 구분 없이 쓴다.
 */
@ConfigurationProperties(prefix = "mijang.external")
public record ExternalApiProperties(
        Sec sec,
        Alpaca alpaca,
        Finnhub finnhub,
        Koreaexim koreaexim,
        Bls bls,
        int connectTimeoutMs,
        int readTimeoutMs) {

    /**
     * SEC EDGAR — 공시·재무제표. 유일하게 API 키가 없는 벤더다.
     *
     * @param userAgent        "앱이름 이메일" 형식. 없으면 모든 요청이 403 이다.
     * @param requestsPerSecond SEC 공정접근 한도. 분당이 아니라 초당이다.
     */
    public record Sec(
            String dataBaseUrl,
            String wwwBaseUrl,
            String userAgent,
            int requestsPerSecond,
            int cikCacheHours) {

        /** User-Agent 가 실제 연락처 형태인지. 기본 예시값을 그대로 두면 false. */
        public boolean configured() {
            return userAgent != null
                    && userAgent.contains("@")
                    && !userAgent.contains("example.com");
        }
    }

    /** Alpaca — 시세·일봉·배당은 data, 종목마스터·휴장일은 trading 쪽이다. */
    public record Alpaca(
            String dataBaseUrl,
            String tradingBaseUrl,
            String apiKey,
            String apiSecret) {

        public boolean configured() {
            return hasText(apiKey) && hasText(apiSecret);
        }
    }

    /** Finnhub — 뉴스·기업정보·투자지표·경제캘린더. 토큰 하나가 전부다. */
    public record Finnhub(String baseUrl, String apiKey) {

        public boolean configured() {
            return hasText(apiKey);
        }
    }

    /**
     * BLS(미 노동통계국) — 경제 지표 발표 일정.
     *
     * <p>키가 없다. iCalendar 파일 하나를 받아 파싱하는 구조라 인증 항목 자체가 없다.
     */
    public record Bls(String scheduleUrl, int cacheHours) {
    }

    /** 한국수출입은행 — 손익 계산 기준 환율. */
    public record Koreaexim(String baseUrl, String apiKey) {

        public boolean configured() {
            return hasText(apiKey);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
