package com.example.mijang.stock.service;

import com.example.mijang.stock.client.AlpacaStockClient;
import com.example.mijang.stock.mapper.StockSplitMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 보유 종목의 주식 분할 이벤트를 벤더에서 받아 stock_splits 에 쌓는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSplitSyncService {

    /** 거슬러 받을 기간. INSERT IGNORE 라 같은 구간을 다시 훑어도 안전하다. */
    private static final int LOOKBACK_YEARS = 5;

    /** 한 번에 물어볼 종목 수. URL 이 길어지면 벤더가 거절한다. */
    private static final int CHUNK = 50;

    private final AlpacaStockClient alpacaClient;
    private final StockSplitMapper splitMapper;

    /** 지금 누군가 들고 있는 종목의 분할을 받아 넣는다. 새로 저장한 건수를 돌려준다. */
    @Transactional
    public int syncHeldSymbols() {
        List<String> symbols = splitMapper.findHeldSymbols();
        if (symbols.isEmpty()) {
            return 0;
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(LOOKBACK_YEARS);
        int saved = 0;
        for (int i = 0; i < symbols.size(); i += CHUNK) {
            saved += syncSymbols(symbols.subList(i, Math.min(i + CHUNK, symbols.size())), from, to);
        }
        return saved;
    }

    /** 지정 종목·기간의 분할을 받아 넣는다. 페이지가 이어지면 끝까지 따라간다. */
    @Transactional
    public int syncSymbols(List<String> symbols, LocalDate from, LocalDate to) {
        int saved = 0;
        String pageToken = null;
        do {
            JsonNode body = alpacaClient.splits(symbols, from, to, pageToken);
            JsonNode actions = body.path("corporate_actions");
            saved += save(actions.path("forward_splits"), "FORWARD");
            saved += save(actions.path("reverse_splits"), "REVERSE");
            JsonNode next = body.path("next_page_token");
            pageToken = next.isNull() || next.isMissingNode() ? null : next.asText();
        } while (pageToken != null);
        return saved;
    }

    /** 한 묶음을 넣는다. 비율이 없거나 0 이하인 행은 건너뛴다. */
    private int save(JsonNode events, String type) {
        int saved = 0;
        for (JsonNode event : events) {
            BigDecimal oldRate = rate(event, "old_rate");
            BigDecimal newRate = rate(event, "new_rate");
            if (oldRate == null || newRate == null
                    || oldRate.signum() <= 0 || newRate.signum() <= 0) {
                log.warn("[분할] 비율이 깨진 이벤트를 건너뛴다 — {} {}",
                        event.path("symbol").asText(), event.path("ex_date").asText());
                continue;
            }
            saved += splitMapper.insertIgnore(
                    event.path("symbol").asText(),
                    LocalDate.parse(event.path("ex_date").asText()),
                    type, oldRate, newRate,
                    text(event, "id"));
        }
        return saved;
    }

    private static BigDecimal rate(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
