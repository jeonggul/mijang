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

/** Alpaca Corporate Actions 에서 현금 배당을 받아 stock_dividends 에 채운다. PROFIT-12 · INFO-06. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDividendSyncService {

    /** 이력 시작점. 연속 증배를 셀 수 있게 깊게 받는다. */
    private static final LocalDate HISTORY_START = LocalDate.of(2000, 1, 1);

    /** 이 시간 안에 다시 물으면 벤더를 부르지 않는다. */
    private static final int FRESH_HOURS = 24;

    /** 배치가 보는 구간. 정정(과거)과 예정 배당(미래)을 함께 잡는다. */
    private static final int BATCH_LOOKBACK_DAYS = 30;
    private static final int BATCH_LOOKAHEAD_DAYS = 90;

    /** 배치 한 요청에 묶는 종목 수. */
    private static final int BATCH_CHUNK = 100;

    private final AlpacaStockClient alpacaClient;
    private final StockDividendMapper stockDividendMapper;

    /** 종목 하나의 배당을 신선하게 만든다. 하루 안에 수집했으면 넘어가고, 벤더 장애 시 기존 데이터로 버틴다. */
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

    /** 보유 종목의 최근 구간을 수집하고 넣거나 고친 이벤트 수를 반환한다. */
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

    /** 벤더에서 받아 upsert 한다. 페이지가 이어지면 끝까지 따라간다. */
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

    /** 벤더 응답 한 건을 우리 행으로 바꾼다. */
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
