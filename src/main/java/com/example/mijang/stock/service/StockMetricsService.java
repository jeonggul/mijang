/*
 * StockMetricsService — 투자 지표를 구해 주는 곳
 *
 * 이 파일이 하는 일
 *   종목 상세가 물으면 시가총액·PER·PBR 같은 값을 돌려준다.
 *
 *   받아 오는 방식이 차트의 일봉 백필과 같다 — <b>미리 다 받지 않고, 열어 본 종목만</b>
 *   받아서 DB 에 남긴다. Finnhub 는 분당 60회라 13,000 종목을 전부 받으면 3.7시간이 걸린다.
 *   사용자가 실제로 보는 종목은 그중 극히 일부다.
 *
 *   하루가 지난 값은 다시 받는다. 시가총액과 PER 은 주가를 따라 매일 바뀐다.
 *
 *   벤더가 막히거나 값이 없어도 <b>화면이 깨지지 않아야 한다.</b> 그래서 실패하면 예외를
 *   던지지 않고 저장돼 있던 값을(있으면) 그대로 돌려준다. 지표는 없어도 시세와 차트는 돈다.
 */
package com.example.mijang.stock.service;

import com.example.mijang.stock.client.FinnhubStockClient;
import com.example.mijang.stock.dto.StockMetricsResponse;
import com.example.mijang.stock.mapper.StockMetricsMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMetricsService {

    /** 이만큼 지난 값은 다시 받는다. 시가총액·PER 은 주가를 따라 매일 바뀐다 */
    private static final Duration STALE_AFTER = Duration.ofHours(20);

    /** Finnhub 는 시가총액을 <b>백만 달러</b> 단위로 준다. 원 단위로 펴서 저장한다 */
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    private final FinnhubStockClient finnhubClient;
    private final StockMetricsMapper metricsMapper;

    /**
     * 지표 한 건.
     *
     * <p>저장된 값이 아직 싱싱하면 그것을 준다. 낡았거나 없으면 벤더에서 받아 저장하고 준다.
     *
     * @return 벤더도 못 주고 저장된 것도 없으면 null. 화면은 그때 전부 — 로 둔다
     */
    @Transactional
    public StockMetricsResponse metrics(String rawSymbol) {
        String symbol = normalize(rawSymbol);
        StockMetricsResponse stored = metricsMapper.findBySymbol(symbol);
        if (stored != null && fresh(stored)) {
            return stored;
        }
        StockMetricsResponse fetched = fetch(symbol);
        if (fetched == null) {
            return stored;          // 벤더가 막혀도 있던 값은 보여준다
        }
        metricsMapper.upsert(fetched);
        return fetched;
    }

    /**
     * 아직 쓸 만한 값인가.
     *
     * <p>기준 시각을 <b>DB 에서 받아 온다.</b> {@code synced_at} 을 찍는 것이 DB 이므로,
     * 자바 시각과 견주면 서로 다른 시계를 비교하게 된다. 표준시가 어긋나면 방금 저장한
     * 값도 만료로 읽혀 요청마다 벤더를 부르고, Finnhub 의 분당 60회를 금세 넘긴다.
     */
    private boolean fresh(StockMetricsResponse stored) {
        return stored.syncedAt() != null
                && stored.syncedAt().isAfter(metricsMapper.now().minus(STALE_AFTER));
    }

    /**
     * 벤더에서 두 창구를 받아 하나로 합친다.
     *
     * <p>프로필(시가총액·산업·상장일)과 지표(PER·PBR·EPS)가 따로 있다.
     * ETF 는 프로필이 빈 객체로 오는데 오류가 아니다 — Finnhub 는 회사만 프로필을 들고 있다.
     */
    private StockMetricsResponse fetch(String symbol) {
        if (!finnhubClient.configured()) {
            return null;
        }
        JsonNode profile = finnhubClient.profile(symbol);
        JsonNode metricRoot = finnhubClient.metrics(symbol);
        JsonNode metric = metricRoot == null ? null : metricRoot.get("metric");

        if ((profile == null || profile.isEmpty()) && (metric == null || metric.isEmpty())) {
            return null;
        }

        return new StockMetricsResponse(
                symbol,
                /* 프로필의 시가총액이 더 자주 갱신된다. 없으면 지표 쪽 값을 쓴다 */
                scaleUp(firstOf(decimal(profile, "marketCapitalization"), decimal(metric, "marketCapitalization"))),
                decimal(metric, "peBasicExclExtraTTM"),
                decimal(metric, "pbAnnual"),
                decimal(metric, "epsBasicExclExtraItemsTTM"),
                decimal(metric, "dividendYieldIndicatedAnnual"),
                decimal(metric, "beta"),
                decimal(metric, "52WeekHigh"),
                decimal(metric, "52WeekLow"),
                text(profile, "finnhubIndustry"),
                text(profile, "country"),
                text(profile, "weburl"),
                date(profile, "ipo"),
                text(profile, "logo"),
                decimal(profile, "shareOutstanding"),
                /* 화면에 바로 돌려줄 때 쓰는 값이다. 표에 실제로 적히는 것은 이게 아니라
                   DB 가 찍는 CURRENT_TIMESTAMP(3) 이고, fresh() 도 그것과 견준다 */
                LocalDateTime.now());
    }

    /** 백만 단위를 원 단위로 편다. null 은 그대로 null 이다 */
    private BigDecimal scaleUp(BigDecimal millions) {
        return millions == null ? null : millions.multiply(MILLION);
    }

    private BigDecimal firstOf(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    /** 값이 없으면 null 이다. 0 으로 바꾸면 "PER 0" 이라는 없는 사실이 된다 */
    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return (value == null || value.isNull() || !value.isNumber()) ? null : value.decimalValue();
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asString("").trim();
        return value.isEmpty() ? null : value;
    }

    /** 상장일. 형식이 깨져 와도 지표 전체를 버리지 않는다 */
    private LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
