/*
 * StockNewsFetchService — 종목 뉴스를 구해 주는 곳
 *
 * 이 파일이 하는 일
 *   최근 뉴스를 받아 화면이 쓸 모양으로 바꿔 준다.
 *
 *   DB 에 담지 않는다. 뉴스는 지나가는 값이라 쌓아 둘 이유가 없고,
 *   전 종목을 미리 받아 두면 분당 60회 한도로는 감당이 안 된다.
 *   대신 <b>메모리에 짧게 캐시한다</b> — 같은 종목을 여러 사람이 열어도 한 번만 부른다.
 *
 *   벤더가 막혀도 화면이 깨지지 않아야 한다. 실패하면 빈 목록을 준다.
 *   뉴스가 없어도 시세와 차트는 돈다.
 *
 *   받은 것을 그대로 뿌리지 않는다. 시황 나열과 다른 회사 얘기가 섞여 있어
 *   {@link NewsRanker} 가 걸러 내고 기업 뉴스를 앞에 세운다.
 */
package com.example.mijang.news.service;

import com.example.mijang.stock.client.FinnhubStockClient;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.mapper.StockMapper;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.news.dto.NewsItemResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockNewsFetchService {

    /** 뉴스는 자주 바뀌지 않는다. 10분이면 충분하고 한도도 아낀다 */
    private static final Duration TTL = Duration.ofMinutes(10);

    /** 며칠 치를 볼지. 너무 좁으면 거래가 뜸한 종목은 한 건도 안 나온다 */
    private static final int WINDOW_DAYS = 30;

    /** 화면이 한 번에 보여줄 건수. 벤더는 200건 넘게 주기도 한다 */
    private static final int LIMIT = 30;

    private final FinnhubStockClient finnhubClient;
    private final StockMapper stockMapper;
    private final NewsRanker ranker;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Instant until, List<NewsItemResponse> items) {
        boolean alive() {
            return Instant.now().isBefore(until);
        }
    }

    /**
     * 최근 뉴스. 뉴스가 없거나 벤더에서 못 받으면 빈 목록이다.
     *
     * <p><b>우리 목록에 없는 종목이면 먼저 막는다.</b> 이 경로는 로그인 없이 부를 수 있어,
     * 막지 않으면 아무 문자열이나 캐시 키가 되고 그 값으로 벤더까지 부르게 된다.
     * {@code ChartService} 가 같은 이유로 같은 검사를 한다 — 두 곳이 달라야 할 이유가 없다.
     */
    public List<NewsItemResponse> news(String rawSymbol) {
        String symbol = normalize(rawSymbol);
        Stock stock = stockMapper.findBySymbol(symbol);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }
        Cached hit = cache.get(symbol);
        if (hit != null && hit.alive()) {
            return hit.items();
        }
        List<NewsItemResponse> items = fetch(symbol, stock);
        cache.put(symbol, new Cached(Instant.now().plus(TTL), items));
        return items;
    }

    /**
     * 수명이 다한 것을 걷어낸다.
     *
     * <p>덮어쓰기만 하고 지우지 않으면 캐시는 줄어들 일이 없다. 사람들이 들여다본 종목이
     * 만료된 채로 계속 쌓인다.
     *
     * <p>통째로 비우지 않고 죽은 것만 고른다 — 한꺼번에 비우면 그 직후 요청이 전부
     * 벤더로 몰린다. {@code ChartService.sweepExpired} 와 같은 방식이다.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000)
    public void sweepExpired() {
        int before = cache.size();
        cache.values().removeIf(entry -> !entry.alive());
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("[뉴스] 만료된 캐시 {}건 걷어냄 (남은 {}건)", removed, cache.size());
        }
    }

    private List<NewsItemResponse> fetch(String symbol, Stock stock) {
        if (!finnhubClient.configured()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        JsonNode rows = finnhubClient.companyNews(symbol, today.minusDays(WINDOW_DAYS), today);
        if (rows == null || !rows.isArray()) {
            return List.of();
        }

        List<NewsItemResponse> raw = new ArrayList<>();
        for (JsonNode row : rows) {
            String headline = row.path("headline").asString("").trim();
            String url = row.path("url").asString("").trim();
            /* 제목이나 링크가 없으면 화면에서 쓸모가 없다 */
            if (headline.isEmpty() || url.isEmpty()) {
                continue;
            }
            raw.add(new NewsItemResponse(
                    headline,
                    row.path("summary").asString("").trim(),
                    row.path("source").asString("").trim(),
                    /* datetime 은 초 단위 유닉스 시각이다 */
                    Instant.ofEpochSecond(row.path("datetime").asLong(0)),
                    url,
                    emptyToNull(row.path("image").asString("").trim())));
        }

        /* 여기서 자르지 않는다. 거르고 순서를 매긴 뒤에 잘라야 쓸 만한 것이 남는다 —
           먼저 자르면 시황 나열이 앞자리를 차지한 채로 잘려 나간다 */
        List<NewsItemResponse> ranked = ranker.rank(raw, stock.name(), symbol);
        return List.copyOf(ranked.size() <= LIMIT ? ranked : ranked.subList(0, LIMIT));
    }

    private String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
