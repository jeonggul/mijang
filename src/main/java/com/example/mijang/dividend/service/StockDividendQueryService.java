/*
 * StockDividendQueryService — 종목 배당 탭 조회
 *
 * 이 파일이 하는 일
 *   수집된 배당 마스터를 화면 모양으로 만든다 — 이력 몇 건과
 *   배당수익률·연간 배당금·주기·연속 증배. 열 때마다 신선도를 확인하고
 *   낡았으면 수집부터 한다.
 */
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

/**
 * 종목 배당 탭. 개발명세서(API) INFO-06 · 화면 stock p4
 */
@Service
@RequiredArgsConstructor
public class StockDividendQueryService {

    /** 화면 표에 보여주는 이력 수. 더 길면 표가 화면을 넘긴다. */
    private static final int HISTORY_LIMIT = 8;

    private final StockDividendSyncService syncService;
    private final StockDividendMapper stockDividendMapper;
    private final DailyPriceMapper dailyPriceMapper;

    /* readOnly 트랜잭션을 걸지 않는다 — ensureFresh 가 수집(INSERT)을 할 수 있다 */
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

    /** 배당수익률(%). 최근 1년 합 ÷ 최신 종가. 종가나 배당이 없으면 null. */
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

    /**
     * 연속 증배 연수.
     *
     * <p>완결된 해(올해 제외)의 연간 합(특별배당 제외)을 해마다 비교해,
     * 마지막 해부터 거슬러 "작년보다 늘었다" 가 이어진 횟수다.
     * 첫 해까지 이어지면 그 앞은 알 수 없으므로 거기서 멈춘다.
     */
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
