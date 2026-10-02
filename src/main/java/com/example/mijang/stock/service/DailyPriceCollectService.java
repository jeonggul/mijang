package com.example.mijang.stock.service;

import com.example.mijang.common.time.TradingClock;
import com.example.mijang.config.StockProperties;
import com.example.mijang.stock.client.AlpacaStockClient;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import com.example.mijang.stock.mapper.StockMapper;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 활성 종목의 일봉을 수집·저장한다. 여기서 저장한 종가가 손익 계산의 기준가다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceCollectService {

    private final AlpacaStockClient alpacaClient;
    private final StockMapper stockMapper;
    private final DailyPriceMapper dailyPriceMapper;
    private final StockProperties props;
    private final TradingClock tradingClock;

    /** 자기 프록시. 같은 클래스에서 saveChunk 를 직접 부르면 @Transactional 이 무시되므로 이를 거친다. */
    private final org.springframework.beans.factory.ObjectProvider<DailyPriceCollectService> self;

    /** 활성 종목의 최근 일봉을 묶음으로 받아 저장한다. 한 묶음이 실패해도 다음 묶음은 계속 진행한다. */
    public int collectRecent(int days) {
        if (!alpacaClient.configured()) {
            log.warn("[일봉 수집] Alpaca 키가 없어 건너뛴다");
            return 0;
        }

        LocalDate to = tradingClock.today();
        LocalDate from = to.minusDays(days);
        List<String> symbols = stockMapper.findActiveSymbols();
        int total = 0;

        for (int i = 0; i < symbols.size(); i += props.getBarBatchSize()) {
            List<String> chunk = symbols.subList(
                    i, Math.min(i + props.getBarBatchSize(), symbols.size()));
            try {
                // 프록시를 거쳐야 @Transactional 이 걸린다. 직접 부르면 안 된다
                total += self.getObject().saveChunk(chunk, from, to);
            } catch (RuntimeException e) {
                // 이 묶음만 버리고 계속 간다
                log.warn("[일봉 수집] 묶음 실패 — {}~{} ({}건)", chunk.get(0),
                        chunk.get(chunk.size() - 1), chunk.size(), e);
            }
        }
        log.info("[일봉 수집] {}건 저장 ({} ~ {})", total, from, to);
        return total;
    }

    /** 한 묶음을 별도 트랜잭션으로 저장한다. 반드시 self 프록시를 거쳐 불러야 @Transactional 이 적용된다. */
    @Transactional
    public int saveChunk(List<String> symbols, LocalDate from, LocalDate to) {
        JsonNode response = alpacaClient.dailyBars(symbols, from, to);
        JsonNode bars = response == null ? null : response.get("bars");
        if (bars == null || !bars.isObject()) {
            return 0;
        }

        int saved = 0;
        // Jackson 3 에서는 fields() 가 아니라 properties() 다. 이름이 바뀌었다
        for (Map.Entry<String, JsonNode> entry : bars.properties()) {
            String symbol = entry.getKey();
            for (JsonNode bar : entry.getValue()) {
                dailyPriceMapper.upsert(
                        symbol,
                        // t 는 ISO-8601 타임스탬프다. 거래일만 필요하므로 앞 10자를 자른다
                        LocalDate.parse(bar.path("t").asText().substring(0, 10)),
                        decimal(bar, "o"),
                        decimal(bar, "h"),
                        decimal(bar, "l"),
                        decimal(bar, "c"),
                        bar.path("v").asLong(0));
                saved++;
            }
        }
        return saved;
    }

    /** 봉의 값 하나를 BigDecimal 로 읽는다. 값이 없으면 0 이 아니라 null 을 돌려준다. */
    private BigDecimal decimal(JsonNode bar, String field) {
        JsonNode value = bar.path(field);
        return value.isMissingNode() || value.isNull()
                ? null
                : new BigDecimal(value.asText());
    }
}
