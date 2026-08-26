/*
 * NotificationProducerService — 알림을 만드는 쪽
 *
 * 이 파일이 하는 일
 *   일봉이 들어온 뒤 목표가 도달(NOTI-01)·급등락(NOTI-02)을 만들고,
 *   배당 수집 뒤에는 배당락일·지급 예정(NOTI-04)을 notifications 에 넣는다.
 *   읽는 쪽(NotificationService)은 손대지 않는다.
 */
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

/**
 * 알림 생성. 개발명세서(API) NOTI-01·02·04 — 확장(부록 C) · 4.5 점검 2.2
 *
 * <p>지금까지는 <b>읽는 쪽만 있었다.</b> 설정 화면의 토글은 저장돼도 알림이 생기지 않았고
 * 헤더 종 아이콘은 항상 비어 있었다. 이 서비스가 그 빈 곳을 채운다.
 *
 * <p>판정은 전부 SQL 로 한다. 후보(보유 중 · 설정 켜짐 · 조건 충족 · 오늘 미발송)를
 * 자바로 거르면 사용자 수만큼 질의가 나간다 — 표 넷을 한 번 조인하는 것으로 끝낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducerService {

    private final NotificationMapper notificationMapper;
    private final MarketCalendar marketCalendar;

    /**
     * 하루치 알림을 만든다.
     *
     * @param tradeDate 판정할 ET 거래일. 그날 일봉이 이미 수집돼 있어야 한다
     * @return 만든 알림 수. 배치 로그에 남는다
     */
    @Transactional
    public int produce(LocalDate tradeDate) {
        return produceTargetPrice(tradeDate) + produceVolatility(tradeDate);
    }

    /**
     * 가장 최근에 <b>마감된</b> 거래일 치를 만든다. 수동 실행용.
     *
     * <p>정기 배치(07:30 KST)는 그 시각의 ET 거래일이 곧 방금 마감한 날이라 그대로 쓰면
     * 되지만, 수동 실행은 아무 때나 눌린다. 한국 낮에 누르면 ET 는 아직 장이 열리기
     * 전이라 {@code tradeDate(now)} 의 일봉이 없다 — 판정할 수 없는 날이다.
     * 마감(16시 ET) 전이면 하루 물리고, 휴장일이면 거래일까지 더 물린다.
     */
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

    /**
     * 목표가 도달. 판단 메모에 적어 둔 값이 실제로 닿았을 때다 — 미장 고유 기능이라
     * 알림도 "왜 그 값을 적었는지" 를 돌아보는 회고 화면으로 보낸다(스키마 주석).
     */
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

    /** 급등락. 어느 쪽이든 이유를 확인할 종목 화면으로 보낸다. */
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

    /**
     * 배당 알림(NOTI-04) — 두 갈래를 한 번에 만든다.
     *
     * <p>가격 알림({@link #produce})과 달리 거래일에 묶이지 않는다 — 배당 공시·지급은
     * 달력을 따라 움직인다. 배당 수집·예상 생성 배치가 끝난 뒤 부른다.
     *
     * @param today 판정 기준일(KST)
     * @return 만든 알림 수
     */
    @Transactional
    public int produceDividend(LocalDate today) {
        return produceDividendExDate(today) + produceDividendPay();
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
