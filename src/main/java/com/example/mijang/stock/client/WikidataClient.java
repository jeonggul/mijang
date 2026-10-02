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

/** Wikidata SPARQL 로 미국 상장 종목의 한글 이름을 받아 오는 클라이언트다. */
@Slf4j
@Component
public class WikidataClient {

    /** 미국 거래소 넷 — 나스닥·NYSE·NYSE American·Cboe 의 Wikidata 항목 번호. 거래소를 한정하지 않으면 타국 티커와 겹친다. */
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

    /** 티커(대문자)→한글명 맵을 한 번에 받는다. 받지 못하면 예외 — 빈 맵을 주면 호출부가 기존 값을 지울 수 있다. */
    public Map<String, String> koreanNames() {
        JsonNode body;
        try {
            // SPARQL 의 중괄호를 스프링 URI 빌더가 변수로 읽어 터지므로, 미리 인코딩한 URI 를 직접 만들어 넘긴다

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

    /** 미국 티커 모양(알파벳 1~5자)만 통과시킨다. */
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
