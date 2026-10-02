package com.example.mijang.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 외부 API 접속 정보({@code mijang.external.*}) 바인딩이다. 키는 git 미추적 secret 파일에 둔다. */
@ConfigurationProperties(prefix = "mijang.external")
public record ExternalApiProperties(
        Sec sec,
        Alpaca alpaca,
        Finnhub finnhub,
        Bls bls,
        Alphavantage alphavantage,
        int connectTimeoutMs,
        int readTimeoutMs) {

    /** SEC EDGAR 설정이다. userAgent 는 "앱이름 이메일" 형식이어야 하고 requestsPerSecond 는 초당 한도다. */
    public record Sec(
            String dataBaseUrl,
            String wwwBaseUrl,
            String userAgent,
            int requestsPerSecond,
            int cikCacheHours) {

        /** User-Agent 가 실제 연락처 형태인지 판정한다. */
        public boolean configured() {
            return userAgent != null
                    && userAgent.contains("@")
                    && !userAgent.contains("example.com");
        }
    }

    /** Alpaca 설정이다. 시세는 data, 종목마스터·휴장일은 trading 쪽이다. */
    public record Alpaca(
            String dataBaseUrl,
            String tradingBaseUrl,
            String apiKey,
            String apiSecret) {

        /** 키·시크릿이 모두 채워졌는지 판정한다. */
        public boolean configured() {
            return hasText(apiKey) && hasText(apiSecret);
        }
    }

    /** Finnhub 설정이다. */
    public record Finnhub(String baseUrl, String apiKey) {

        /** 키가 채워졌는지 판정한다. */
        public boolean configured() {
            return hasText(apiKey);
        }
    }

    /** Alpha Vantage(실적 발표 일정) 설정이다. */
    public record Alphavantage(String baseUrl, String apiKey) {

        /** 키가 채워졌는지 판정한다. */
        public boolean configured() {
            return hasText(apiKey);
        }
    }

    /** BLS(미 노동통계국) 발표 일정 설정이다. 인증 항목이 없다. */
    public record Bls(String scheduleUrl, int cacheHours) {
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
