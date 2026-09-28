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

/** Alpaca 에서 거래일 달력을 받아 market_days 에 채운다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCalendarSyncService {

    /** 과거로 받는 일수. 전일 종가를 찾으려면 연휴를 건널 만큼 필요하다. */
    private static final int BACK_DAYS = 400;

    /** 미래로 받는 일수. 배치가 며칠 걸러도 판정이 멈추지 않게 둔다. */
    private static final int FORWARD_DAYS = 120;

    private final AlpacaCalendarClient calendarClient;
    private final MarketDayMapper marketDayMapper;

    /** 거래일 달력을 받아 저장하고 채운 거래일 수를 반환한다. */
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

    /** 달력 한 줄을 읽는다. session_open·session_close 는 콜론 없는 "0400" 형식으로 온다. */
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

    /** 콜론 없는 "0400" 표기를 04:00 으로 읽는다. */
    private LocalTime compact(String raw) {
        String value = raw.trim();
        if (value.length() != 4) {
            throw new IllegalArgumentException("시각 형식이 아니다: " + raw);
        }
        return LocalTime.of(Integer.parseInt(value.substring(0, 2)),
                            Integer.parseInt(value.substring(2)));
    }
}
