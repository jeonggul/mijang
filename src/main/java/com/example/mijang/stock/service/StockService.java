package com.example.mijang.stock.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.StockProperties;
import com.example.mijang.fx.service.FxRateService;
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

/** 종목 상세 화면에 필요한 정보를 모아 돌려준다. 등락률 기준가는 세션이 정한다. */
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
    private final FxRateService fxRateService;
    private final com.example.mijang.common.time.TradingClock tradingClock;

    /** 종목 상세를 돌려준다. 기준가로 쓸 일봉이 없으면 그 자리에서 받아 저장하므로 읽기 전용이 아니다. */
    @Transactional
    public StockDetailResponse detail(String symbol) {
        String key = normalize(symbol);
        Stock stock = stockMapper.findBySymbol(key);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }

        MarketSession session = calendarService.currentSession();
        LocalDate lastDay = calendarService.lastTradingDay().orElse(null);

        // 당일 정규장 종가는 장이 끝난 뒤에만 벤더에서 받는다. 장중의 미확정 값이 daily_prices 에 들어가면 안 된다
        boolean needRegularClose = session == MarketSession.AFTER || session == MarketSession.CLOSED;
        BigDecimal regularClose = needRegularClose
                ? closeOrStored(key, lastDay)
                : dailyPriceMapper.findCloseOn(key, lastDay);
        BigDecimal previousClose = lastDay == null ? null
                : closeOrStored(key, calendarService.previousTradingDay(lastDay).orElse(null));

        BigDecimal basePrice = baseFor(session, previousClose, regularClose);

        // 장이 열려 있으면 실시간 캐시 값을, 닫혀 있으면 마지막 정규장 종가를 쓴다
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

        BigDecimal latestFx = fxRateService.latest().map(rate -> rate.rate()).orElse(null);
        BigDecimal priceKrw = currentPrice == null || latestFx == null ? null
                : currentPrice.multiply(latestFx).setScale(2, RoundingMode.HALF_UP);

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
                priceKrw,
                session.name(),
                session.label(),
                basePrice,
                regularClose,
                lastDay);
    }

    /** 그날 종가를 돌려준다. 없으면 벤더에서 채우고, 그것도 실패하면 저장된 값으로 물러난다. */
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

    /** 세션별 등락률 기준가를 고른다. 시간외에만 당일 정규장 종가를 쓴다. */
    private BigDecimal baseFor(MarketSession session, BigDecimal previousClose, BigDecimal regularClose) {
        return session == MarketSession.AFTER && regularClose != null ? regularClose : previousClose;
    }

    /** 등락률을 계산한다. 기준가가 없거나 0 이면 null 을 돌려준다. */
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
