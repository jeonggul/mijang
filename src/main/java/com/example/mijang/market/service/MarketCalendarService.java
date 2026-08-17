/*
 * MarketCalendarService — 지금이 어느 구간인지 판단하는 곳
 *
 * 이 파일이 하는 일
 *   거래일 달력을 보고 지금이 프리마켓인지 정규장인지 애프터마켓인지 휴장인지 답한다.
 *   등락률의 기준가와 화면의 멈춤 여부가 전부 이 판단 위에 있다.
 *
 *   주말만 거르면 안 되는 이유 — 추수감사절에 "정규장" 이라고 표시하게 되고,
 *   조기폐장일(13:00 마감)에는 마감 뒤 세 시간을 정규장으로 착각한다.
 *
 *   달력은 DB 에 담아 두고 본다. 요청마다 벤더를 부르면 한도를 잡아먹고 느리다.
 */
package com.example.mijang.market.service;

import com.example.mijang.market.domain.MarketDay;
import com.example.mijang.market.domain.MarketSession;
import com.example.mijang.market.mapper.MarketDayMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCalendarService {

    /** 미국 동부. 장 시간은 전부 이 기준이다 */
    public static final ZoneId ET = ZoneId.of("America/New_York");

    private final MarketDayMapper marketDayMapper;

    /**
     * 지금 구간.
     *
     * <p>달력에 오늘이 없으면 휴장이다. 있으면 시각을 견주어 넷 중 하나로 답한다.
     *
     * <p><b>달력이 비어 있으면 정규장으로 본다.</b> 달력을 아직 못 받았다고 해서
     * 장이 열린 날에 화면을 멈추면, 데이터가 없는 것과 장이 닫힌 것이 구분되지 않는다.
     * 배치가 채우기 전까지의 임시 상태다.
     */
    @Transactional(readOnly = true)
    public MarketSession currentSession() {
        return sessionAt(ZonedDateTime.now(ET));
    }

    /**
     * 그 시각의 구간.
     *
     * <p>지금이 아닌 시각으로도 물을 수 있게 열어 두었다. 네 구간을 전부 확인하려면
     * 장이 열리고 닫힐 때까지 기다릴 수 없기 때문이다.
     */
    @Transactional(readOnly = true)
    public MarketSession sessionAt(ZonedDateTime moment) {
        if (marketDayMapper.count() == 0) {
            return MarketSession.REGULAR;
        }
        ZonedDateTime et = moment.withZoneSameInstant(ET);
        MarketDay day = marketDayMapper.findByDate(et.toLocalDate());
        if (day == null) {
            return MarketSession.CLOSED;
        }
        return sessionAt(day, et.toLocalTime());
    }

    /** 그날의 시각이 어느 구간인지. 경계는 앞 구간이 아니라 뒤 구간에 넣는다 */
    private MarketSession sessionAt(MarketDay day, LocalTime time) {
        if (time.isBefore(day.sessionOpen()) || !time.isBefore(day.sessionClose())) {
            return MarketSession.CLOSED;
        }
        if (time.isBefore(day.openTime())) {
            return MarketSession.PRE;
        }
        if (time.isBefore(day.closeTime())) {
            return MarketSession.REGULAR;
        }
        return MarketSession.AFTER;
    }

    /**
     * 지금 기준으로 "마지막으로 열렸던 거래일".
     *
     * <p>휴장일에는 이 날의 값에서 화면이 멈춰야 한다. 오늘이 거래일이면 오늘이다.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> lastTradingDay() {
        MarketDay day = marketDayMapper.findLatestOnOrBefore(LocalDate.now(ET));
        return Optional.ofNullable(day).map(MarketDay::tradeDate);
    }

    /**
     * 그 거래일의 직전 거래일.
     *
     * <p>정규장 등락률의 기준가가 이 날의 종가다. 주말·연휴를 건너뛰어야 하므로
     * 하루 빼기로는 안 된다.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> previousTradingDay(LocalDate base) {
        MarketDay day = marketDayMapper.findPreviousBefore(base);
        return Optional.ofNullable(day).map(MarketDay::tradeDate);
    }

    /** 오늘 거래일 정보. 휴장이면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<MarketDay> today() {
        return Optional.ofNullable(marketDayMapper.findByDate(LocalDate.now(ET)));
    }
}
