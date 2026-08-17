/*
 * MarketCalendarSyncService — 거래일 달력을 채우는 곳
 *
 * 이 파일이 하는 일
 *   Alpaca 에서 거래일 달력을 받아 market_days 에 넣는다.
 *
 *   앞뒤로 넉넉히 받는다. 뒤(과거)는 전일 종가를 찾는 데, 앞(미래)은 배치가 하루 걸러도
 *   세션 판정이 멈추지 않게 하는 데 쓴다.
 */
package com.example.mijang.market.service;

import com.example.mijang.market.client.AlpacaCalendarClient;
import com.example.mijang.market.domain.MarketDay;
import com.example.mijang.market.mapper.MarketDayMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCalendarSyncService {

    /** 과거로 얼마나. 전일 종가를 찾으려면 연휴를 건널 만큼은 있어야 한다 */
    private static final int BACK_DAYS = 400;

    /** 미래로 얼마나. 배치가 며칠 걸러도 판정이 멈추지 않게 둔다 */
    private static final int FORWARD_DAYS = 120;

    private final AlpacaCalendarClient calendarClient;
    private final MarketDayMapper marketDayMapper;

    /** @return 채운 거래일 수 */
    @Transactional
    public int syncAll() {
        LocalDate today = LocalDate.now(MarketCalendarService.ET);
        JsonNode days = calendarClient.calendar(today.minusDays(BACK_DAYS), today.plusDays(FORWARD_DAYS));
        if (days == null || !days.isArray()) {
            log.warn("[거래일] 달력을 받지 못했다 — 기존 값은 그대로 둔다");
            return 0;
        }

        int saved = 0;
        for (JsonNode day : days) {
            MarketDay parsed = parse(day);
            if (parsed != null) {
                marketDayMapper.upsert(parsed);
                saved++;
            }
        }
        log.info("[거래일] {}일 반영 — 마지막 거래일 {}", saved, marketDayMapper.findMaxDate());
        return saved;
    }

    /**
     * 한 줄을 읽는다.
     *
     * <p>{@code open}·{@code close} 는 "09:30" 처럼 오고, {@code session_open}·
     * {@code session_close} 는 <b>콜론 없이</b> "0400" 으로 온다. 형식이 달라 따로 읽는다.
     */
    private MarketDay parse(JsonNode day) {
        try {
            return new MarketDay(
                    LocalDate.parse(day.path("date").asString("")),
                    LocalTime.parse(day.path("open").asString("09:30")),
                    LocalTime.parse(day.path("close").asString("16:00")),
                    compact(day.path("session_open").asString("0400")),
                    compact(day.path("session_close").asString("2000")));
        } catch (RuntimeException e) {
            log.warn("[거래일] 한 줄을 읽지 못해 건너뛴다 — {}", day, e);
            return null;
        }
    }

    /** "0400" → 04:00. 콜론이 없는 표기다 */
    private LocalTime compact(String raw) {
        String value = raw.trim();
        if (value.length() != 4) {
            throw new IllegalArgumentException("시각 형식이 아니다: " + raw);
        }
        return LocalTime.of(Integer.parseInt(value.substring(0, 2)),
                            Integer.parseInt(value.substring(2)));
    }
}
