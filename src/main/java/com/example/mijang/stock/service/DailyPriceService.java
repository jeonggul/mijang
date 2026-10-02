package com.example.mijang.stock.service;

import com.example.mijang.common.time.TradingClock;
import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 일봉을 조회한다. 벤더를 부르지 않고 수집 배치가 채운 것만 읽는다. */
@Service
@RequiredArgsConstructor
public class DailyPriceService {

    private final DailyPriceMapper dailyPriceMapper;
    private final TradingClock tradingClock;

    /** 기간별 일봉을 조회한다. 수집되지 않은 구간은 빈 목록으로 돌아온다. */
    @Transactional(readOnly = true)
    public List<CandleResponse> candles(String symbol, String range) {
        LocalDate to = tradingClock.today();
        LocalDate from = to.minusDays(rangeToDays(range));
        return dailyPriceMapper.findByRange(normalize(symbol), from, to);
    }

    /** 기간 문자열을 달력 일수로 바꾼다. 모르는 값은 30일로 본다. */
    private long rangeToDays(String range) {
        if (range == null) {
            return 30;
        }
        return switch (range.trim().toUpperCase(Locale.ROOT)) {
            case "3M" -> 90;
            case "6M" -> 180;
            case "1Y" -> 365;
            case "5Y" -> 365L * 5;
            default -> 30;
        };
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
