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

/** 외부 API RestClient 들을 벤더별 인증 헤더까지 붙여 구성한다. */
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

    /** SEC EDGAR 데이터 API 클라이언트를 만든다. 연락처 User-Agent 가 없으면 403 이다. */
    @Bean
    public RestClient secDataClient() {
        return RestClient.builder()
                .baseUrl(props.sec().dataBaseUrl())
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .build();
    }

    /** SEC 정적 파일(www.sec.gov) 클라이언트를 만든다. */
    @Bean
    public RestClient secWwwClient() {
        return RestClient.builder()
                .baseUrl(props.sec().wwwBaseUrl())
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .build();
    }

    /** Alpaca 시세 API 클라이언트를 만든다. */
    @Bean
    public RestClient alpacaDataClient() {
        return alpacaClient(props.alpaca().dataBaseUrl());
    }

    /** Alpaca 트레이딩 API(종목 마스터·휴장일) 클라이언트를 만든다. */
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

    /** Finnhub 클라이언트를 만든다. 키가 로그에 남지 않게 쿼리 대신 헤더로 보낸다. */
    @Bean
    public RestClient finnhubClient() {
        return RestClient.builder()
                .baseUrl(props.finnhub().baseUrl())
                .requestFactory(requestFactory())
                .defaultHeader("X-Finnhub-Token", nullSafe(props.finnhub().apiKey()))
                .build();
    }

    /** Alpha Vantage 클라이언트를 만든다. 키는 쿼리 파라미터로만 받는다. */
    @Bean
    public RestClient alphaVantageClient() {
        return RestClient.builder()
                .baseUrl(props.alphavantage().baseUrl())
                .requestFactory(requestFactory())
                .build();
    }

    /** BLS 발표 일정 클라이언트를 만든다. 연락처 User-Agent 만 붙인다. */
    @Bean
    public RestClient blsClient() {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .build();
    }

    /** Wikidata SPARQL 클라이언트를 만든다. 연락처 User-Agent 가 없으면 차단된다. */
    @Bean
    public RestClient wikidataSparqlClient() {
        return RestClient.builder()
                .baseUrl("https://query.wikidata.org")
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, "application/sparql-results+json")
                .build();
    }

    /* Accept-Encoding 을 손으로 넣으면 압축 해제가 호출부로 넘어가 파싱이 깨지므로 지정하지 않는다. */
    private ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        return factory;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 벤더별 설정 여부를 기동 로그로 남긴다. */
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
