/*
 * StockDividendSyncService — 종목 배당 수집
 *
 * 이 파일이 하는 일
 *   Alpaca Corporate Actions 에서 현금 배당을 받아 stock_dividends 에 채운다.
 *   두 입구가 있다 — 종목 화면이 배당 탭을 열 때(그 종목 전체 이력),
 *   그리고 매일 배치(보유 종목의 최근 구간). 하루 안에 다시 열면
 *   벤더를 부르지 않는다.
 */
package com.example.mijang.dividend.service;

import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.mapper.StockDividendMapper;
import com.example.mijang.stock.client.AlpacaStockClient;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 종목 배당 수집. 개발명세서(API) PROFIT-12 · INFO-06
 *
 * <p>전체 이력을 받는 이유 — 배당 요약의 연속 증배는 해마다의 연간 합을
 * 비교해야 해서 최근 몇 건으로는 만들 수 없다. 실측으로 2016년 이력까지
 * 오는 것을 확인했고, 한 종목 전체가 요청 한두 번이면 온다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDividendSyncService {

    /** 이력 시작점. 연속 증배를 셀 수 있을 만큼 깊게 받는다. */
    private static final LocalDate HISTORY_START = LocalDate.of(2000, 1, 1);

    /** 이 시간 안에 다시 물으면 벤더를 부르지 않는다. */
    private static final int FRESH_HOURS = 24;

    /** 배치가 보는 구간 — 정정 반영(과거)과 예정 배당(미래)을 함께 잡는다. */
    private static final int BATCH_LOOKBACK_DAYS = 30;
    private static final int BATCH_LOOKAHEAD_DAYS = 90;

    /** 배치 한 요청에 묶는 종목 수. 일봉 수집과 같은 결이다. */
    private static final int BATCH_CHUNK = 100;

    private final AlpacaStockClient alpacaClient;
    private final StockDividendMapper stockDividendMapper;

    /**
     * 종목 하나를 신선하게 만든다. 배당 탭이 열릴 때 부른다.
     *
     * <p>하루 안에 수집한 적이 있으면 그대로 둔다. 벤더가 죽어 있어도
     * 이미 받아 둔 것이 있으면 그걸로 답한다 — 탭이 벤더 장애에 같이 죽을 이유가 없다.
     */
    public void ensureFresh(String symbol) {
        LocalDateTime last = stockDividendMapper.findLastSyncedAt(symbol);
        if (last != null && last.isAfter(LocalDateTime.now().minusHours(FRESH_HOURS))) {
            return;
        }
        try {
            syncSymbols(List.of(symbol), HISTORY_START, LocalDate.now().plusDays(BATCH_LOOKAHEAD_DAYS));
        } catch (RuntimeException e) {
            if (last == null) {
                throw e;    // 보여줄 것이 하나도 없다 — 실패를 그대로 알린다
            }
            log.warn("배당 수집 실패 — {} 는 이전 수집분으로 답한다", symbol, e);
        }
    }

    /**
     * 보유 종목의 최근 구간을 수집한다. 배치와 관리자 수동 실행이 부른다.
     *
     * @return 넣거나 고친 이벤트 수
     */
    public int syncHeldSymbols() {
        List<String> symbols = stockDividendMapper.findHeldSymbols();
        if (symbols.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        int saved = 0;
        for (int i = 0; i < symbols.size(); i += BATCH_CHUNK) {
            List<String> chunk = symbols.subList(i, Math.min(i + BATCH_CHUNK, symbols.size()));
            saved += syncSymbols(chunk,
                    today.minusDays(BATCH_LOOKBACK_DAYS), today.plusDays(BATCH_LOOKAHEAD_DAYS));
        }
        return saved;
    }

    /** 받아서 upsert 한다. 페이지가 이어지면 끝까지 따라간다. */
    public int syncSymbols(List<String> symbols, LocalDate from, LocalDate to) {
        int saved = 0;
        String pageToken = null;
        do {
            JsonNode body = alpacaClient.cashDividends(symbols, from, to, pageToken);
            for (JsonNode event : body.path("corporate_actions").path("cash_dividends")) {
                stockDividendMapper.upsert(parse(event));
                saved++;
            }
            JsonNode next = body.path("next_page_token");
            pageToken = next.isNull() || next.isMissingNode() ? null : next.asText();
        } while (pageToken != null);
        return saved;
    }

    /** 벤더 응답 한 건 → 우리 행. 응답 필드가 스키마와 1:1 이다(3.11). */
    private StockDividend parse(JsonNode event) {
        return new StockDividend(
                event.path("symbol").asText(),
                LocalDate.parse(event.path("ex_date").asText()),
                "CASH",
                new BigDecimal(event.path("rate").asText()),
                date(event, "record_date"),
                date(event, "payable_date"),
                date(event, "process_date"),
                event.path("special").asBoolean(false),
                event.path("foreign").asBoolean(false),
                text(event, "cusip"),
                text(event, "id"));
    }

    private static LocalDate date(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : LocalDate.parse(value.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
