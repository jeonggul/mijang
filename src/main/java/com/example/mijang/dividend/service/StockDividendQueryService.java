package com.example.mijang.dividend.service;

import com.example.mijang.common.time.TradingClock;
import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.dto.StockDividendTabResponse;
import com.example.mijang.dividend.mapper.StockDividendMapper;
import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 종목 배당 탭(INFO-06)의 이력·요약 응답을 만든다. */
@Service
@RequiredArgsConstructor
public class StockDividendQueryService {

    /** 화면 표에 보여주는 이력 수. */
    private static final int HISTORY_LIMIT = 8;

    private final StockDividendSyncService syncService;
    private final StockDividendMapper stockDividendMapper;
    private final DailyPriceMapper dailyPriceMapper;

    /** 종목 배당 탭 응답을 만든다. ensureFresh 가 INSERT 할 수 있어 readOnly 트랜잭션을 걸지 않는다. */
    public StockDividendTabResponse tab(String symbol) {
        String upper = symbol.trim().toUpperCase();
        syncService.ensureFresh(upper);

        List<StockDividend> all = stockDividendMapper.findBySymbol(upper);
        LocalDate today = LocalDate.now(TradingClock.SERVICE_ZONE);

        List<StockDividendTabResponse.Item> history = all.stream()
                .limit(HISTORY_LIMIT)
                .map(d -> new StockDividendTabResponse.Item(
                        d.exDate(), d.payableDate(), d.amountPerShare(), d.special(),
                        d.payableDate() == null || d.payableDate().isAfter(today)))
                .toList();

        BigDecimal annual = BigDecimal.ZERO;
        int perYear = 0;
        LocalDate yearAgo = today.minusYears(1);
        for (StockDividend d : all) {
            if (d.special() || d.exDate().isAfter(today) || !d.exDate().isAfter(yearAgo)) {
                continue;
            }
            annual = annual.add(d.amountPerShare());
            perYear++;
        }

        return new StockDividendTabResponse(history,
                yieldPct(upper, annual), annual, perYear, streakYears(all, today));
    }

    /** 배당수익률(%)을 구한다. 종가나 배당이 없으면 null 이다. */
    private BigDecimal yieldPct(String symbol, BigDecimal annual) {
        if (annual.signum() <= 0) {
            return null;
        }
        CandleResponse latest = dailyPriceMapper.findLatest(symbol);
        if (latest == null || latest.close() == null || latest.close().signum() <= 0) {
            return null;
        }
        return annual.multiply(BigDecimal.valueOf(100))
                .divide(latest.close(), 2, RoundingMode.HALF_UP);
    }

    /** 완결된 해의 연간 합(특별배당 제외)을 비교해 연속 증배 연수를 센다. */
    private static int streakYears(List<StockDividend> all, LocalDate today) {
        Map<Integer, BigDecimal> byYear = new TreeMap<>();
        for (StockDividend d : all) {
            if (d.special() || d.exDate().getYear() >= today.getYear()) {
                continue;
            }
            byYear.merge(d.exDate().getYear(), d.amountPerShare(), BigDecimal::add);
        }
        int streak = 0;
        int year = today.getYear() - 1;
        while (byYear.containsKey(year) && byYear.containsKey(year - 1)
                && byYear.get(year).compareTo(byYear.get(year - 1)) > 0) {
            streak++;
            year--;
        }
        return streak;
    }
}
