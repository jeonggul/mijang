/*
 * StockService — 종목 상세
 *
 * 이 파일이 하는 일
 *   티커 하나를 받아 상세 화면에 필요한 것을 모아 준다 —
 *   종목 정보, 현재가, 등락률, 기간 최고·최저.
 *
 *   등락률을 그냥 "전일 대비" 로 두지 않는다. <b>세션이 기준가를 정한다</b>(2.15).
 *     · 프리마켓·정규장 — 직전 거래일 정규장 종가
 *     · 시간외        — 당일 정규장 종가
 *     · 휴장·마감      — 직전 거래일 종가. 값이 멈춘다
 *
 *   기준가를 "저장된 일봉 중 뒤에서 두 번째" 로 잡으면 안 된다. 일봉 수집 배치는 장 마감 뒤에
 *   돌기 때문에, <b>장중에는 그날 일봉이 아직 없어</b> 하루씩 밀린 값을 기준으로 삼게 된다.
 *   거래일 달력에서 날짜를 얻어 그 날짜의 종가를 찾는다.
 */
package com.example.mijang.stock.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.StockProperties;
import com.example.mijang.market.domain.MarketSession;
import com.example.mijang.market.cache.QuoteCacheService;
import com.example.mijang.market.service.MarketCalendarService;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.dto.HighLow;
import com.example.mijang.stock.dto.StockDetailResponse;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import com.example.mijang.stock.mapper.StockMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMapper stockMapper;
    private final DailyPriceMapper dailyPriceMapper;
    private final StockProperties props;
    private final MarketCalendarService calendarService;
    private final ChartService chartService;
    private final QuoteCacheService quoteCache;
    private final com.example.mijang.common.time.TradingClock tradingClock;

    /**
     * 종목 상세. {@code PRICE-02}·{@code PRICE-04}
     *
     * <p>읽기 전용이 아니다. 기준가로 쓸 일봉이 없으면 그 자리에서 받아 저장하기 때문이다
     * — 장중과 마감 직후에는 그날 일봉이 아직 수집되지 않았다.
     */
    @Transactional
    public StockDetailResponse detail(String symbol) {
        String key = normalize(symbol);
        Stock stock = stockMapper.findBySymbol(key);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }

        MarketSession session = calendarService.currentSession();
        LocalDate lastDay = calendarService.lastTradingDay().orElse(null);

        /* 마지막 거래일의 정규장 종가. 시간외 등락률의 기준이자, 휴장일에 멈춰 있을 값이다 */
        /* 당일 정규장 종가는 <b>장이 끝난 뒤에만</b> 받아 온다.
           장중에 받으면 아직 확정되지 않은 값이 daily_prices 에 들어가고,
           그 표는 손익 계산의 기준가라 확정된 값만 있어야 한다.
           정규장·프리마켓에는 이 값을 쓰지 않으므로 없어도 된다 */
        boolean needRegularClose = session == MarketSession.AFTER || session == MarketSession.CLOSED;
        BigDecimal regularClose = needRegularClose
                ? closeOrStored(key, lastDay)
                : dailyPriceMapper.findCloseOn(key, lastDay);
        BigDecimal previousClose = lastDay == null ? null
                : closeOrStored(key, calendarService.previousTradingDay(lastDay).orElse(null));

        BigDecimal basePrice = baseFor(session, previousClose, regularClose);

        /* 화면에 처음 뜨는 현재가.
           장이 열려 있으면 실시간 캐시의 값을 쓴다 — 없으면 첫 화면이 어제 종가로 떠서
           등락률이 0% 로 보인다. 장이 닫혀 있으면 마지막 정규장 종가에서 멈춘다 */
        CandleResponse latest = dailyPriceMapper.findLatest(key);
        BigDecimal currentPrice = session.live()
                ? quoteCache.get(key).map(q -> q.price()).orElse(regularClose)
                : regularClose;
        if (currentPrice == null) {
            currentPrice = latest == null ? null : latest.close();
        }

        HighLow highLow = dailyPriceMapper.findHighLow(
                // 거래일 기준. 세션 판단이 전부 ET 인데 여기만 KST 면 경계에서 하루가 밀린다
                key, tradingClock.today().minusDays(props.getHighLowDays()));

        return new StockDetailResponse(
                stock.symbol(),
                stock.name(),
                stock.nameKo(),
                stock.exchange(),
                stock.assetClass(),
                stock.isActive(),
                stock.inactiveReason(),
                currentPrice,
                previousClose,
                changeRate(currentPrice, basePrice),
                highLow == null ? null : highLow.high(),
                highLow == null ? null : highLow.low(),
                lastDay != null ? lastDay : (latest == null ? null : latest.tradeDate()),
                null,   // priceKrw — fx 범위가 붙으면 채운다 (2.6)
                session.name(),
                session.label(),
                basePrice,
                regularClose,
                lastDay);
    }

    /**
     * 이 세션의 등락률 기준가.
     *
     * <p>시간외에만 당일 정규장 종가를 쓴다. 마감 뒤의 움직임을 하루치 등락에 섞으면
     * 그날 장이 어땠는지가 흐려진다.
     */
    /**
     * 그날 종가. 없으면 벤더에서 채워 보고, 그것도 실패하면 DB 에 있는 것으로 만족한다.
     *
     * <p><b>벤더가 죽었다고 상세 화면 전체가 죽으면 안 된다.</b> {@code closeOn} 은 값이
     * 없을 때 벤더를 부르는데, 거기서 나는 예외는 503 이라 그대로 두면 종목명·거래소·
     * 전일 종가가 전부 DB 에 있는데도 화면이 "찾을 수 없는 종목입니다" 가 된다.
     *
     * <p>지표와 뉴스는 이미 이렇게 물러난다. 상세만 다를 이유가 없다.
     */
    private BigDecimal closeOrStored(String symbol, java.time.LocalDate date) {
        if (date == null) {
            return null;
        }
        try {
            return chartService.closeOn(symbol, date);
        } catch (RuntimeException e) {
            log.warn("[종목] {} {} 종가를 벤더에서 못 받았다. 저장된 값으로 간다", symbol, date);
            return dailyPriceMapper.findCloseOn(symbol, date);
        }
    }

    private BigDecimal baseFor(MarketSession session, BigDecimal previousClose, BigDecimal regularClose) {
        return session == MarketSession.AFTER && regularClose != null ? regularClose : previousClose;
    }

    /** 기준가가 없거나 0 이면 계산하지 않는다. 0 으로 나누면 무한대가 된다 */
    private BigDecimal changeRate(BigDecimal price, BigDecimal base) {
        if (price == null || base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return price.subtract(base)
                .divide(base, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
