/*
 * CalendarService — 거시·실적·배당을 한 캘린더 이벤트로 모은다
 *
 * 이 파일이 하는 일
 *   경제 캘린더(기존)·어닝(stock_earnings)·배당(stock_dividends)을 CalendarEventResponse
 *   한 모양으로 맞춰 낸다. "내 종목" 필터는 보유·관심 심볼 합집합으로 판정한다.
 */
package com.example.mijang.news.service;

import com.example.mijang.common.time.TradingClock;
import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.domain.StockEarnings;
import com.example.mijang.dividend.mapper.StockDividendMapper;
import com.example.mijang.dividend.mapper.StockEarningsMapper;
import com.example.mijang.news.dto.CalendarEventResponse;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.stock.mapper.WatchlistMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final StockEarningsMapper earningsMapper;
    private final StockDividendMapper dividendMapper;
    private final TransactionMapper transactionMapper;
    private final WatchlistMapper watchlistMapper;
    // 오늘 날짜는 TradingClock.SERVICE_ZONE(static)만 쓰므로 주입 필드를 두지 않는다

    /** 내 종목 = 보유 ∪ 관심. 비로그인은 빈 집합. */
    public Set<String> mySymbols(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<String> s = new HashSet<>();
        transactionMapper.findSymbolsByUser(userId).forEach(x -> s.add(x.toUpperCase(Locale.ROOT)));
        watchlistMapper.findSymbolsByUser(userId).forEach(x -> s.add(x.toUpperCase(Locale.ROOT)));
        return s;
    }

    public List<CalendarEventResponse> earnings(LocalDate from, LocalDate to, Set<String> mine) {
        List<CalendarEventResponse> out = new ArrayList<>();
        for (StockEarnings e : earningsMapper.findByReportDateBetween(from, to)) {
            if (!mine.isEmpty() && !mine.contains(e.symbol().toUpperCase(Locale.ROOT))) {
                continue;
            }
            out.add(new CalendarEventResponse(e.reportDate(), CalendarEventResponse.TYPE_EARNINGS,
                    e.symbol(), "실적 발표", earningsNote(e)));
        }
        return out;
    }

    public List<CalendarEventResponse> dividends(LocalDate from, LocalDate to, Set<String> mine) {
        List<CalendarEventResponse> out = new ArrayList<>();
        for (StockDividend d : dividendMapper.findByExDateBetween(from, to)) {
            if (!mine.isEmpty() && !mine.contains(d.symbol().toUpperCase(Locale.ROOT))) {
                continue;
            }
            String amount = d.amountPerShare() == null ? null
                    : "$" + d.amountPerShare().stripTrailingZeros().toPlainString();
            // 배당 한 행이 두 이벤트(락일·지급일)가 된다. 각자 자기 날짜가 구간 안일 때만 낸다
            if (!d.exDate().isBefore(from) && !d.exDate().isAfter(to)) {
                out.add(new CalendarEventResponse(d.exDate(), CalendarEventResponse.TYPE_DIVIDEND,
                        d.symbol(), "배당락", amount));
            }
            if (d.payableDate() != null && !d.payableDate().isBefore(from) && !d.payableDate().isAfter(to)) {
                out.add(new CalendarEventResponse(d.payableDate(), CalendarEventResponse.TYPE_DIVIDEND,
                        d.symbol(), "배당 지급", amount));
            }
        }
        return out;
    }

    /** 종목 상세용 — 그 종목의 오늘 이후 첫 실적·첫 배당락. 없으면 null. */
    public NextEvents nextEvents(String symbol) {
        LocalDate today = LocalDate.now(TradingClock.SERVICE_ZONE);
        String up = symbol.toUpperCase(Locale.ROOT);
        StockEarnings e = earningsMapper.findNextBySymbol(up, today);
        CalendarEventResponse earn = e == null ? null : new CalendarEventResponse(
                e.reportDate(), CalendarEventResponse.TYPE_EARNINGS, up, "실적 발표", earningsNote(e));
        StockDividend d = dividendMapper.findNextExDateBySymbol(up, today);
        CalendarEventResponse div = d == null ? null : new CalendarEventResponse(
                d.exDate(), CalendarEventResponse.TYPE_DIVIDEND, up, "배당락",
                d.amountPerShare() == null ? null : "$" + d.amountPerShare().stripTrailingZeros().toPlainString());
        return new NextEvents(earn, div);
    }

    private String earningsNote(StockEarnings e) {
        String when = "pre-market".equals(e.timeOfDay()) ? "장전"
                : "post-market".equals(e.timeOfDay()) ? "장후" : null;
        String eps = e.estimateEps() == null ? null : "EPS " + e.estimateEps().stripTrailingZeros().toPlainString();
        if (when == null && eps == null) return null;
        if (when == null) return eps;
        if (eps == null) return when;
        return when + " · " + eps;
    }

    public record NextEvents(CalendarEventResponse earnings, CalendarEventResponse dividend) {
    }
}
