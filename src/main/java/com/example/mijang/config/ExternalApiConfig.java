package com.example.mijang.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 API 호출 클라이언트 구성.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.config
 *
 * <p>인증 헤더를 호출부마다 붙이면 한 군데만 빠뜨려도 401·403 이 난다. 벤더별 인증 방식을
 * 여기서 {@code defaultHeader} 로 못 박아 두고, 호출부는 경로와 파라미터만 신경 쓰게 한다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ExternalApiProperties.class)
public class ExternalApiConfig {

    private final ExternalApiProperties props;
    private final FxProperties fxProps;

    public ExternalApiConfig(ExternalApiProperties props, FxProperties fxProps) {
        this.props = props;
        this.fxProps = fxProps;
        logConfiguredVendors();
    }

    /**
     * SEC EDGAR 데이터 API (data.sec.gov) — 공시 목록, XBRL 재무 항목.
     *
     * <p>SEC 는 API 키 대신 연락처가 담긴 User-Agent 를 요구한다. 이 헤더가 없으면 403 이다.
     */
    @Bean
    public RestClient secDataClient() {
        return RestClient.builder()
                .baseUrl(props.sec().dataBaseUrl())
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .build();
    }

    /** SEC 정적 파일 (www.sec.gov) — 티커→CIK 매핑 등은 데이터 API 가 아닌 이쪽에 있다. */
    @Bean
    public RestClient secWwwClient() {
        return RestClient.builder()
                .baseUrl(props.sec().wwwBaseUrl())
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .build();
    }

    /** Alpaca 시세 API (data.alpaca.markets) — 봉·호가·체결·기업액션. */
    @Bean
    public RestClient alpacaDataClient() {
        return alpacaClient(props.alpaca().dataBaseUrl());
    }

    /** Alpaca 트레이딩 API (paper-api) — 종목 마스터와 휴장일 캘린더가 이쪽에 있다. */
    @Bean
    public RestClient alpacaTradingClient() {
        return alpacaClient(props.alpaca().tradingBaseUrl());
    }

    private RestClient alpacaClient(String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .defaultHeader("APCA-API-KEY-ID", nullSafe(props.alpaca().apiKey()))
                .defaultHeader("APCA-API-SECRET-KEY", nullSafe(props.alpaca().apiSecret()))
                .build();
    }

    /**
     * Finnhub — 뉴스·기업정보·투자지표.
     *
     * <p>Finnhub 는 {@code ?token=} 쿼리 파라미터도 받지만 그 방식은 키가 접근 로그에 그대로 남는다.
     * 같은 값을 헤더로도 받아주므로 헤더 쪽을 쓴다.
     */
    @Bean
    public RestClient finnhubClient() {
        return RestClient.builder()
                .baseUrl(props.finnhub().baseUrl())
                .requestFactory(requestFactory())
                .defaultHeader("X-Finnhub-Token", nullSafe(props.finnhub().apiKey()))
                .build();
    }

    /**
     * BLS 발표 일정 — 인증이 없다. 정부 사이트라 SEC 와 마찬가지로 연락처가 담긴 User-Agent 를 붙인다.
     */
    @Bean
    public RestClient blsClient() {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .build();
    }

    /**
     * Wikidata — 한글 종목명을 받는다. 인증이 없다.
     *
     * <p>공개 SPARQL 창구라 연락처가 담긴 User-Agent 를 요구한다. 없으면 차단된다.
     * SEC·BLS 와 같은 값을 쓴다 — 셋 다 "누가 부르는지 밝혀라" 는 같은 요구다.
     */
    @Bean
    public RestClient wikidataSparqlClient() {
        return RestClient.builder()
                .baseUrl("https://query.wikidata.org")
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, "application/sparql-results+json")
                .build();
    }

    /**
     * Accept-Encoding 은 일부러 직접 지정하지 않는다. 여기서 쓰는 JDK 커넥션은 gzip 을 스스로 붙이고
     * 응답도 알아서 푸는데, 헤더를 손으로 넣으면 압축 해제를 호출부 책임으로 넘겨버려 파싱이 깨진다.
     */
    private ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        return factory;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 어느 벤더가 실제로 쓸 수 있는 상태인지 기동 시 한 줄로 남긴다.
     * 키를 안 채운 채 호출해서 401 을 보고 원인을 찾는 시간을 줄이려는 것이다.
     */
    private void logConfiguredVendors() {
        log.info("외부 API 설정 — SEC:{} Alpaca:{} Finnhub:{} 환율:{}",
                mark(props.sec().configured()),
                mark(props.alpaca().configured()),
                mark(props.finnhub().configured()),
                mark(fxProps.getAppId() != null && !fxProps.getAppId().isBlank()));
        if (!props.sec().configured()) {
            log.warn("SEC User-Agent 가 기본 예시값이다. 실제 이메일로 바꾸지 않으면 모든 SEC 요청이 403 이다.");
        }
    }

    private static String mark(boolean configured) {
        return configured ? "설정됨" : "미설정";
    }
}
