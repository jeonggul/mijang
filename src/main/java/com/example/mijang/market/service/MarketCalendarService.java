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

/** DB 거래일 달력을 보고 지금이 어느 장 구간인지 판단한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCalendarService {

    /** 미국 동부 시간대. 장 시간은 전부 이 기준이다. */
    public static final ZoneId ET = ZoneId.of("America/New_York");

    private final MarketDayMapper marketDayMapper;

    /** 지금 장 구간을 반환한다. 달력이 비어 있으면 정규장으로 본다. */
    @Transactional(readOnly = true)
    public MarketSession currentSession() {
        return sessionAt(ZonedDateTime.now(ET));
    }

    /** 지정한 시각의 장 구간을 반환한다. */
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

    /** 그날의 시각이 어느 구간인지 판정한다. 경계는 뒤 구간에 넣는다. */
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

    /** 지금 기준으로 마지막으로 열렸던 거래일을 반환한다. */
    @Transactional(readOnly = true)
    public Optional<LocalDate> lastTradingDay() {
        MarketDay day = marketDayMapper.findLatestOnOrBefore(LocalDate.now(ET));
        return Optional.ofNullable(day).map(MarketDay::tradeDate);
    }

    /** 그 거래일의 직전 거래일을 반환한다. 주말·연휴를 건너뛴다. */
    @Transactional(readOnly = true)
    public Optional<LocalDate> previousTradingDay(LocalDate base) {
        MarketDay day = marketDayMapper.findPreviousBefore(base);
        return Optional.ofNullable(day).map(MarketDay::tradeDate);
    }

    /** 지정한 날짜의 거래일 정보를 반환한다. */
    @Transactional(readOnly = true)
    public Optional<MarketDay> tradingDay(LocalDate date) {
        return Optional.ofNullable(marketDayMapper.findByDate(date));
    }

    /** 지정한 날짜보다 앞선 거래일의 개장·마감 정보를 반환한다. */
    @Transactional(readOnly = true)
    public Optional<MarketDay> previousTradingDayInfo(LocalDate base) {
        return Optional.ofNullable(marketDayMapper.findPreviousBefore(base));
    }

    /** 오늘 거래일 정보를 반환한다. 휴장이면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<MarketDay> today() {
        return Optional.ofNullable(marketDayMapper.findByDate(LocalDate.now(ET)));
    }
}
