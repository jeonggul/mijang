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

/** 종목 뉴스를 벤더에서 받아 NewsRanker 로 거른 뒤 메모리에 짧게 캐시해 내준다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockNewsFetchService {

    /** 캐시 수명. 10분이면 충분하고 벤더 한도도 아낀다. */
    private static final Duration TTL = Duration.ofMinutes(10);

    /** 조회 구간(일). 너무 좁으면 거래가 뜸한 종목은 한 건도 안 나온다. */
    private static final int WINDOW_DAYS = 30;

    /** 화면이 한 번에 보여줄 건수. */
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

    /** 최근 뉴스를 준다. 우리 목록에 없는 종목은 막고, 벤더에서 못 받으면 빈 목록이다. */
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

    /** 수명이 다한 캐시만 걷어낸다. 통째로 비우면 직후 요청이 전부 벤더로 몰린다. */
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
            // 제목이나 링크가 없으면 화면에서 쓸모가 없다
            if (headline.isEmpty() || url.isEmpty()) {
                continue;
            }
            raw.add(new NewsItemResponse(
                    headline,
                    row.path("summary").asString("").trim(),
                    row.path("source").asString("").trim(),
                    // datetime 은 초 단위 유닉스 시각이다
                    Instant.ofEpochSecond(row.path("datetime").asLong(0)),
                    url,
                    emptyToNull(row.path("image").asString("").trim())));
        }

        // 거르고 순서를 매긴 뒤에 잘라야 쓸 만한 것이 남는다
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
