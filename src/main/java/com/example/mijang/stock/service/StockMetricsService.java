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

/** 시가총액·PER 등 투자 지표를 열어 본 종목만 벤더에서 받아 저장·제공한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMetricsService {

    /** 이만큼 지난 값은 다시 받는다. */
    private static final Duration STALE_AFTER = Duration.ofHours(20);

    /** Finnhub 는 시가총액을 백만 달러 단위로 주므로 펴서 저장한다. */
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    private final FinnhubStockClient finnhubClient;
    private final StockMetricsMapper metricsMapper;

    /** 지표 한 건을 돌려준다. 저장 값이 낡으면 벤더에서 새로 받고, 벤더가 실패하면 있던 값을 그대로 준다. */
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

    /** 저장 값이 아직 유효한지 본다. 시계 어긋남을 피하려고 기준 시각은 DB 에서 받는다. */
    private boolean fresh(StockMetricsResponse stored) {
        return stored.syncedAt() != null
                && stored.syncedAt().isAfter(metricsMapper.now().minus(STALE_AFTER));
    }

    /** 프로필과 지표 두 창구를 받아 하나로 합친다. ETF 는 프로필이 빈 객체로 와도 오류가 아니다. */
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
                /* 화면 즉시 응답용 값이다. 표에 적히고 fresh() 가 견주는 것은 DB 의 CURRENT_TIMESTAMP(3) 다 */
                LocalDateTime.now());
    }

    /** 백만 단위를 원 단위로 편다. null 은 그대로 null 이다. */
    private BigDecimal scaleUp(BigDecimal millions) {
        return millions == null ? null : millions.multiply(MILLION);
    }

    private BigDecimal firstOf(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }

    /** 값이 없으면 0 이 아니라 null 을 돌려준다. */
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

    /** 상장일을 읽는다. 형식이 깨져 와도 지표 전체를 버리지 않는다. */
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
