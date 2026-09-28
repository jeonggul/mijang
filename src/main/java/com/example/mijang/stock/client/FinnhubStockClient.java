package com.example.mijang.stock.client;

import com.example.mijang.config.ExternalApiProperties;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Finnhub 에서 종목 정보(목록·프로필·지표·뉴스)를 받아 오는 클라이언트다. 시세는 받지 않는다. */
@Slf4j
@Component
public class FinnhubStockClient {

    private final RestClient finnhubClient;
    private final ExternalApiProperties.Finnhub config;

    public FinnhubStockClient(@Qualifier("finnhubClient") RestClient finnhubClient,
                              ExternalApiProperties props) {
        this.finnhubClient = finnhubClient;
        this.config = props.finnhub();
    }

    /** API 키가 채워져 있는지 확인한다. */
    public boolean configured() {
        return config.apiKey() != null && !config.apiKey().isBlank();
    }

    /** 미국 상장 종목 목록(종류·isin 포함)을 받는다. 실패하면 null — 빈 목록과 구분해야 기존 값을 안 지운다. */
    public JsonNode usSymbols() {
        try {
            return finnhubClient.get()
                    .uri(uri -> uri.path("/stock/symbol")
                            .queryParam("exchange", "US")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Finnhub 종목 목록 조회 실패", e);
            return null;
        }
    }

    /** 기업 프로필(시가총액·산업·상장일·로고)을 받는다. 없는 티커·ETF 는 빈 객체가 온다. */
    public JsonNode profile(String symbol) {
        return get("/stock/profile2", symbol);
    }

    /** 투자 지표(PER·PBR·EPS·배당수익률·베타·52주 최고저)를 한 번에 받는다. */
    public JsonNode metrics(String symbol) {
        return getWithMetric("/stock/metric", symbol);
    }

    /** 기간을 지정해 종목 뉴스를 받는다. 기간이 없으면 빈 배열이 온다. */
    public JsonNode companyNews(String symbol, java.time.LocalDate from, java.time.LocalDate to) {
        try {
            return finnhubClient.get()
                    .uri(uri -> uri.path("/company-news")
                            .queryParam("symbol", normalize(symbol))
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Finnhub 뉴스 조회 실패 — {}", symbol, e);
            return null;
        }
    }

    private JsonNode get(String path, String symbol) {
        try {
            return finnhubClient.get()
                    .uri(uri -> uri.path(path).queryParam("symbol", normalize(symbol)).build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Finnhub {} 조회 실패 — {}", path, symbol, e);
            return null;
        }
    }

    private JsonNode getWithMetric(String path, String symbol) {
        try {
            return finnhubClient.get()
                    .uri(uri -> uri.path(path)
                            .queryParam("symbol", normalize(symbol))
                            .queryParam("metric", "all")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Finnhub 지표 조회 실패 — {}", symbol, e);
            return null;
        }
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
