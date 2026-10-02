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

/**
 * 일봉 조회. 개발명세서(API) PRICE-05
 *
 * <p><b>벤더를 부르지 않는다.</b> 수집 배치가 채워 둔 것만 읽는다(2.7).
 */
@Service
@RequiredArgsConstructor
public class DailyPriceService {

    private final DailyPriceMapper dailyPriceMapper;
    private final TradingClock tradingClock;

    /**
     * 기간 일봉 조회.
     *
     * <p>수집되지 않은 구간은 <b>빈 목록</b>이다. 그 자리에서 벤더를 부르면 응답이 몇 초씩
     * 걸리고 사용자는 왜 이 종목만 느린지 알 수 없다.
     *
     * @param range 1M·3M·6M·1Y·5Y. 모르는 값이면 1M 로 본다
     */
    @Transactional(readOnly = true)
    public List<CandleResponse> candles(String symbol, String range) {
        LocalDate to = tradingClock.today();
        LocalDate from = to.minusDays(rangeToDays(range));
        return dailyPriceMapper.findByRange(normalize(symbol), from, to);
    }

    /**
     * 기간 문자열을 일수로 바꾼다.
     *
     * <p>거래일이 아니라 <b>달력 일수</b>로 뺀다. 주말·휴장일이 섞여 있어도
     * 조회 조건은 날짜 범위면 충분하고, 실제로 있는 봉만 돌아온다.
     *
     * <p>모르는 값에 예외를 던지지 않는다. 주소창에 이상한 값을 넣었다고
     * 차트가 오류를 띄우는 것보다 기본 구간을 보여 주는 편이 낫다.
     */
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
