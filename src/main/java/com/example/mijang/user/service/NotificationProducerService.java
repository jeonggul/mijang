package com.example.mijang.user.service;

import com.example.mijang.common.time.MarketCalendar;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.user.dto.DividendExDateHit;
import com.example.mijang.user.dto.DividendPayHit;
import com.example.mijang.user.dto.TargetPriceHit;
import com.example.mijang.user.dto.VolatilityHit;
import com.example.mijang.user.mapper.NotificationMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 목표가·급등락·배당·뉴스 알림을 만들어 notifications 에 넣는다(NOTI-01~04). 판정은 전부 SQL 조인 한 번으로 한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducerService {

    private final NotificationMapper notificationMapper;
    private final MarketCalendar marketCalendar;

    /** 지정한 ET 거래일 치 가격 알림을 만든다. 그날 일봉이 이미 수집돼 있어야 한다. */
    @Transactional
    public int produce(LocalDate tradeDate) {
        return produceTargetPrice(tradeDate) + produceVolatility(tradeDate);
    }

    /** 가장 최근에 마감된 거래일 치를 만든다. 수동 실행용으로, 마감 전·휴장일이면 직전 거래일로 물린다. */
    @Transactional
    public int produceLatestClosed() {
        var et = java.time.Instant.now().atZone(TradingClock.MARKET_ZONE);
        LocalDate date = et.toLocalDate();
        if (et.toLocalTime().isBefore(java.time.LocalTime.of(16, 0))) {
            date = date.minusDays(1);
        }
        while (!marketCalendar.isTradingDay(date)) {
            date = date.minusDays(1);
        }
        return produce(date);
    }

    /** 목표가 도달 알림. 회고 화면으로 보낸다. */
    private int produceTargetPrice(LocalDate tradeDate) {
        List<TargetPriceHit> hits = notificationMapper.findTargetPriceHits(tradeDate);
        for (TargetPriceHit hit : hits) {
            notificationMapper.insert(hit.userId(), "TARGET_PRICE", hit.symbol(),
                    hit.symbol() + " 목표가 도달",
                    "판단 메모에 적어 둔 목표가 " + money(hit.targetPrice())
                            + " 에 닿았습니다 (당일 고가 " + money(hit.todayHigh())
                            + "). 그때의 판단을 돌아볼 시간입니다.",
                    "/retrospect");
        }
        return hits.size();
    }

    /** 급등락 알림. 종목 화면으로 보낸다. */
    private int produceVolatility(LocalDate tradeDate) {
        List<VolatilityHit> hits = notificationMapper.findVolatilityHits(tradeDate);
        for (VolatilityHit hit : hits) {
            boolean up = hit.changeRate().signum() >= 0;
            notificationMapper.insert(hit.userId(), "VOLATILITY", hit.symbol(),
                    hit.symbol() + (up ? " 급등" : " 급락"),
                    "하루 만에 " + percent(hit.changeRate()) + " "
                            + (up ? "올랐습니다" : "내렸습니다")
                            + " (" + money(hit.prevClose()) + " → " + money(hit.todayClose()) + ").",
                    "/stock?symbol=" + hit.symbol());
        }
        return hits.size();
    }

    /** 배당락일·지급 예정 알림을 한 번에 만든다. 배당 수집·예상 생성 배치가 끝난 뒤 부른다. */
    @Transactional
    public int produceDividend(LocalDate today) {
        return produceDividendExDate(today) + produceDividendPay();
    }

    /** 새 기사가 들어온 종목의 뉴스 알림을 만든다. 제목 대신 건수만 싣는다. */
    @Transactional
    public int produceNews(String symbol, int newCount) {
        if (newCount <= 0) {
            return 0;
        }
        List<Long> recipients = notificationMapper.findNewsRecipients(symbol);
        for (Long userId : recipients) {
            notificationMapper.insert(userId, "NEWS", symbol,
                    symbol + " 새 뉴스 " + newCount + "건",
                    "종목 화면 뉴스 탭에서 확인할 수 있습니다",
                    "/stock?symbol=" + symbol);
        }
        return recipients.size();
    }

    /** 배당락일 임박. 종목 화면으로 보낸다 — 락일과 주당 배당을 확인하는 자리다. */
    private int produceDividendExDate(LocalDate today) {
        List<DividendExDateHit> hits = notificationMapper.findDividendExDateHits(today);
        for (DividendExDateHit hit : hits) {
            notificationMapper.insert(hit.userId(), "DIVIDEND", hit.symbol(),
                    hit.symbol() + " 배당락일 안내",
                    korean(hit.exDate()) + "이 배당락일입니다. 전일까지 보유한 수량 기준으로"
                            + " 배당이 나옵니다 (주당 " + money(hit.amountPerShare()) + ")."
                            + (hit.payableDate() != null
                               ? " 지급일은 " + korean(hit.payableDate()) + "입니다." : ""),
                    "/stock?symbol=" + hit.symbol());
        }
        return hits.size();
    }

    /** 예상 배당 지급 예정. 확정하는 자리인 배당 관리 화면으로 보낸다. */
    private int produceDividendPay() {
        List<DividendPayHit> hits = notificationMapper.findDividendPayHits();
        for (DividendPayHit hit : hits) {
            notificationMapper.insert(hit.userId(), "DIVIDEND", hit.symbol(),
                    hit.symbol() + " 배당 지급 예정",
                    korean(hit.payDate()) + " 지급 예정 · 예상 세후 " + money(hit.netAmountUsd())
                            + ". 입금을 확인하면 배당 관리에서 확정해 주세요.",
                    "/dividend");
        }
        return hits.size();
    }

    /** 8월 28일 꼴. 알림 문장 안에서는 ISO 날짜보다 이 쪽이 읽힌다. */
    private static String korean(LocalDate date) {
        return date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
    }

    private static String money(BigDecimal value) {
        return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 부호를 항상 붙인다. 색이 없는 알림 문장에서는 부호가 방향의 전부다. */
    private static String percent(BigDecimal rate) {
        BigDecimal pct = rate.abs().multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        return (rate.signum() >= 0 ? "+" : "−") + pct.toPlainString() + "%";
    }
}
