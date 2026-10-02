/*
 * WikidataClient — 한글 종목명을 받아 오는 곳
 *
 * 이 파일이 하는 일
 *   Wikidata 에 "미국 거래소에 상장된 것들의 티커와 한글 이름을 달라" 고 물어본다.
 *
 *   왜 Wikidata 인가 — 키가 필요 없고, 티커와 한글 이름을 함께 들고 있는 공개 자료가
 *   달리 없다. 증권사 API 는 계좌가 있어야 하고, 국내 포털은 공개 창구가 없다.
 *
 *   거래소를 반드시 한정해야 한다. 한정하지 않으면 티커가 겹친다 —
 *   AAL 은 미국에서 아메리칸항공이지만 런던에서는 앵글로아메리칸이고,
 *   Wikidata 는 둘 다 AAL 로 들고 있다. 실제로 겪은 일이다.
 */
package com.example.mijang.stock.client;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class WikidataClient {

    /**
     * 미국 거래소 넷. Wikidata 의 항목 번호다.
     *
     * <p>Q82059 나스닥 · Q13677 뉴욕증권거래소 · Q1064434 NYSE American · Q11288 Cboe
     */
    private static final String US_EXCHANGES = "wd:Q82059 wd:Q13677 wd:Q1064434 wd:Q11288";

    private static final String QUERY = """
            SELECT DISTINCT ?ticker ?ko WHERE {
              ?item p:P414 ?stmt .
              ?stmt ps:P414 ?ex ; pq:P249 ?ticker .
              VALUES ?ex { %s }
              ?item rdfs:label ?ko FILTER(lang(?ko) = "ko")
            }
            """.formatted(US_EXCHANGES);

    private final RestClient sparqlClient;

    public WikidataClient(@Qualifier("wikidataSparqlClient") RestClient sparqlClient) {
        this.sparqlClient = sparqlClient;
    }

    /**
     * 티커 → 한글명.
     *
     * <p>한 번에 전부 받는다. 1,000건이 채 안 되고 하루 한 번만 부르므로 나눠 받을 이유가 없다.
     *
     * <p>같은 티커가 여러 번 나오면 <b>먼저 온 것을 쓴다.</b> 한 회사가 이름을 여러 개 달고
     * 있을 때인데, 어느 쪽이든 사람이 알아볼 수 있는 이름이라 굳이 고르지 않는다.
     *
     * @return 티커는 대문자. 받지 못하면 빈 맵이 아니라 예외를 던진다 —
     *         빈 맵을 돌려주면 호출부가 "한글명이 하나도 없다" 로 읽고 기존 값을 지울 수 있다
     */
    public Map<String, String> koreanNames() {
        JsonNode body;
        try {
            /* 주소를 직접 만들어 넘긴다.
               SPARQL 에는 중괄호가 들어 있는데, 스프링의 URI 빌더는 그것을 치환할 변수로 읽어
               "Not enough variable values available" 로 터진다. 이미 인코딩된 URI 를 주면
               빌더가 손대지 않는다 */
            URI uri = URI.create("/sparql?query="
                    + URLEncoder.encode(QUERY, StandardCharsets.UTF_8));
            body = sparqlClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Wikidata 한글명 조회 실패", e);
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }

        JsonNode rows = body == null ? null : body.path("results").path("bindings");
        if (rows == null || !rows.isArray()) {
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }

        Map<String, String> names = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            String ticker = row.path("ticker").path("value").asString("").trim().toUpperCase(Locale.ROOT);
            String korean = row.path("ko").path("value").asString("").trim();
            if (!usableTicker(ticker) || korean.isEmpty()) {
                continue;
            }
            names.putIfAbsent(ticker, korean);
        }
        log.info("[한글명] Wikidata 에서 {}건 받음", names.size());
        return names;
    }

    /**
     * 쓸 수 있는 티커인가.
     *
     * <p>Wikidata 에는 "NASDAQ: AAPL" 이나 "005930.KS" 처럼 형식이 제각각인 값이 섞여 있다.
     * 알파벳 1~5자만 통과시킨다 — 미국 티커의 모양이다.
     */
    private boolean usableTicker(String ticker) {
        if (ticker.length() < 1 || ticker.length() > 5) {
            return false;
        }
        for (int i = 0; i < ticker.length(); i++) {
            if (!Character.isLetter(ticker.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
