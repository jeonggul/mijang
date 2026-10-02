/*
 * FinnhubStockClient — 종목 정보를 받아 오는 곳
 *
 * 이 파일이 하는 일
 *   Finnhub 에서 <b>종목에 대한 정보</b>만 받는다. 시세는 받지 않는다.
 *
 *   역할을 이렇게 나눈 이유가 있다.
 *     · Alpaca 는 시세(일봉·분봉·실시간)는 무료로 잘 주는데 <b>종목 종류를 안 알려준다.</b>
 *       응답의 class 가 전부 us_equity 하나라 ETF·워런트·우선주를 구분할 수 없다.
 *     · Finnhub 는 반대다. 시세(일봉)는 유료라 403 이지만, 종류·시가총액·투자 지표는
 *       무료로 준다.
 *   겹치지 않게 나눠 쓰면 둘 다 무료로 필요한 것을 얻는다.
 *
 *   호출 한도가 분당 60회다. 그래서 쓰는 방식이 창구마다 다르다.
 *     · 종목 목록 — 한 번에 3만 건이 오므로 하루 한 번 배치로 받는다
 *     · 프로필·지표 — 종목당 한 번이라 사용자가 열어 본 종목만 받아 저장한다
 */
package com.example.mijang.stock.client;

import com.example.mijang.config.ExternalApiProperties;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

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

    /** 키가 채워져 있는지. 배치가 돌기 전에 먼저 본다. */
    public boolean configured() {
        return config.apiKey() != null && !config.apiKey().isBlank();
    }

    /**
     * 미국 상장 종목 목록. 종류(type)와 국제 식별번호(isin)가 들어 있다.
     *
     * <p>한 번 호출에 3만 건이 온다. 나눠 받을 방법이 없고 나눌 이유도 없다.
     *
     * <p><b>리다이렉트를 탄다.</b> 이 창구는 JSON 을 바로 주지 않고 파일 주소로 넘긴다.
     * 따라가지 않으면 본문이 {@code <a href=...>Found</a>} 인 HTML 이 와서 파싱이 깨진다.
     * JDK 기본 커넥션은 GET 리다이렉트를 따라가므로 별도 설정 없이 동작한다.
     *
     * @return 실패하면 null. 호출부가 "받지 못했다" 와 "빈 목록" 을 구분해야 한다 —
     *         빈 목록으로 착각하면 기존 값을 지우게 된다
     */
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

    /**
     * 기업 프로필. 시가총액·산업·상장일·로고를 준다.
     *
     * <p>없는 티커나 ETF 를 물으면 <b>빈 객체</b>가 온다. 오류가 아니다 —
     * Finnhub 는 회사만 프로필을 들고 있다.
     */
    public JsonNode profile(String symbol) {
        return get("/stock/profile2", symbol);
    }

    /**
     * 투자 지표. PER·PBR·EPS·배당수익률·베타·52주 최고저를 준다.
     *
     * <p>{@code metric=all} 로 한 번에 받는다. 항목을 골라 받아도 호출 수는 같다.
     */
    public JsonNode metrics(String symbol) {
        return getWithMetric("/stock/metric", symbol);
    }

    /**
     * 종목 뉴스. {@code INFO-01}
     *
     * <p>기간을 반드시 넘겨야 한다. 없으면 빈 배열이 온다.
     *
     * <p>한 번에 200건 넘게 오기도 한다. 화면이 잘라 쓴다 — 벤더에 건수 제한이 없다.
     */
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
