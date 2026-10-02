/*
 * StockSplitSyncService — 주식 분할 받아 넣기
 *
 * 이 파일이 하는 일
 *   벤더에서 분할 이벤트를 받아 stock_splits 에 쌓는다.
 *
 *   왜 이 표가 필요한가
 *     시세는 분할이 반영된 값으로 들어오는데(bars 를 adjustment=split 으로 받는다)
 *     사용자의 매매 기록은 그날 체결한 그대로다. 둘의 기준이 어긋나면 4:1 분할 뒤
 *     평가금액이 4분의 1로 보인다. 보정하려면 "언제 몇 배가 되었는가" 를 알아야 한다.
 *
 *   왜 보유 종목만 받는가
 *     분할은 보유 수량을 바로잡는 데 쓴다. 아무도 안 들고 있는 종목의 분할은
 *     지금 아무 계산에도 들어가지 않는다. 벤더 한도를 거기에 쓸 이유가 없다.
 *     들고 있지 않던 종목을 새로 사면 그때 이 배치가 다음 회차에 데려온다.
 */
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

/** 분할 동기화. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSplitSyncService {

    /**
     * 얼마나 거슬러 올라가 받을 것인가.
     *
     * <p>매매 기록이 몇 년 전 것일 수 있어 넉넉히 둔다. 이미 있는 행은 INSERT IGNORE 로
     * 넘어가므로 같은 구간을 다시 훑어도 값이 어긋나지 않는다.
     */
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

    /** 받아서 넣는다. 페이지가 이어지면 끝까지 따라간다. */
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

    /**
     * 한 묶음을 넣는다.
     *
     * <p>비율이 없거나 0 이면 건너뛴다. 그런 행이 들어오면 보정 배수가 0 이나 무한이 되어
     * 보유 수량이 통째로 망가진다 — 한 건 버리는 편이 낫다.
     */
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
