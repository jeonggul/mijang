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

/**
 * 일봉 수집. 개발명세서(API) PRICE-05 · 외부 데이터 출처 3.1
 *
 * <p>여기서 저장한 종가가 <b>손익 계산의 기준가</b>다(2.4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceCollectService {

    private final AlpacaStockClient alpacaClient;
    private final StockMapper stockMapper;
    private final DailyPriceMapper dailyPriceMapper;
    private final StockProperties props;
    private final TradingClock tradingClock;

    /**
     * 자기 자신. {@code @Transactional} 을 타려면 프록시를 거쳐야 한다.
     *
     * <p>같은 클래스 안에서 {@code saveChunk(...)} 로 부르면 프록시를 지나치지 않아
     * <b>트랜잭션이 아예 열리지 않는다.</b> 그러면 봉 하나하나가 따로 커밋되어,
     * 묶음 중간에 실패했을 때 앞부분만 남는다.
     *
     * <p>생성자에서 자기를 받으면 순환이 되므로 {@code ObjectProvider} 로 미룬다.
     */
    private final org.springframework.beans.factory.ObjectProvider<DailyPriceCollectService> self;

    /**
     * 활성 종목의 최근 일봉을 받아 저장한다.
     *
     * <p>종목을 묶어서 부른다. 1만 종목을 하나씩 부르면 1만 번이 되어 분당 200회 한도를
     * 감당할 수 없다. 100개씩 묶으면 100회로 끝난다.
     *
     * <p>묶음 하나가 실패해도 <b>다음 묶음은 계속 간다.</b> 종목 하나 때문에 그날 수집이
     * 통째로 비는 것이 더 나쁘다.
     *
     * @param days 오늘로부터 며칠 전까지 받을지. 평소 배치는 며칠이면 충분하고,
     *             처음 채울 때만 크게 준다
     * @return 저장한 봉 개수
     */
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

    /**
     * 한 묶음을 받아 저장한다.
     *
     * <p>묶음마다 트랜잭션을 따로 연다. 전체를 하나로 묶으면 1만 종목치가 한 트랜잭션에
     * 쌓여 잠금이 오래 걸린다.
     *
     * <p><b>{@code public} 이어야 하고 {@code self} 를 통해 불러야 한다.</b> 같은 클래스에서
     * 곧장 부르면 프록시를 지나치지 않아 이 {@code @Transactional} 이 무시된다.
     *
     * <p>응답 모양은 {@code {"bars": {"AAPL": [ {...}, ... ], "MSFT": [...] }}} 다.
     * 티커별로 봉 배열이 들어 있어 두 겹으로 돈다.
     */
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

    /**
     * 봉의 값 하나를 BigDecimal 로 읽는다.
     *
     * <p>{@code asDouble()} 로 받으면 안 된다. 0.1 같은 값이 이진 부동소수로 정확히
     * 표현되지 않아 손익 계산에서 오차가 쌓인다. 문자열을 거쳐 BigDecimal 로 만든다.
     *
     * <p><b>없으면 null 이다. 0 이 아니다.</b> 이 표는 손익 계산의 기준가라 0 이 들어가면
     * 등락률과 평가손익이 조용히 망가진다 — 비어 있는 값은 눈에 띄지만 0 은 안 띈다.
     * {@code ChartService.decimal} 도 같은 규칙이다.
     */
    private BigDecimal decimal(JsonNode bar, String field) {
        JsonNode value = bar.path(field);
        return value.isMissingNode() || value.isNull()
                ? null
                : new BigDecimal(value.asText());
    }
}
