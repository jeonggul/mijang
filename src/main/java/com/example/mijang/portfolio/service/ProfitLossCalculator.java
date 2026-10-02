package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.dto.ProfitLossResponse;
import com.example.mijang.portfolio.dto.SymbolPnl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** 종목별 값을 받아 주가손익·환차손익·합계를 구하는 순수 계산기다. */
public final class ProfitLossCalculator {

    /** 원화 금액 자리수. */
    private static final int KRW_SCALE = 2;
    /** 달러 금액 자리수. */
    private static final int USD_SCALE = 4;
    /** 수익률 자리수. 스키마 DECIMAL(9,4). */
    private static final int RATE_SCALE = 4;

    /** 주가·환율 손익의 부호 조합 상태. */
    public static final String STATE_BOTH_POSITIVE = "BOTH_POSITIVE";
    public static final String STATE_BOTH_NEGATIVE = "BOTH_NEGATIVE";
    public static final String STATE_OFFSET = "OFFSET";

    private ProfitLossCalculator() {
    }

    /** 종목별 손익을 계산해 합친다. fxRate 는 null 이면 안 되며 호출부가 먼저 확인한다. */
    public static ProfitLossResponse calculate(List<SymbolPnl> holdings,
                                               BigDecimal fxRate,
                                               LocalDate asOf,
                                               boolean substituted) {
        BigDecimal valueUsd = BigDecimal.ZERO;
        BigDecimal costKrw = BigDecimal.ZERO;
        BigDecimal pricePnlKrw = BigDecimal.ZERO;
        BigDecimal fxPnlKrw = BigDecimal.ZERO;
        BigDecimal pricePnlUsd = BigDecimal.ZERO;
        int skipped = 0;

        for (SymbolPnl h : holdings) {
            if (!h.calculable()) {
                skipped++;   // 현재가가 없으면 계산에서 뺀다
                continue;
            }
            BigDecimal priceGap = h.currentPrice().subtract(h.avgPrice());
            BigDecimal fxGap = fxRate.subtract(h.avgFxRate());

            // 주가손익 = 수량 × (현재가 − 평단가) × 현재환율
            pricePnlKrw = pricePnlKrw.add(h.quantity().multiply(priceGap).multiply(fxRate));
            // 환차손익 = 수량 × 평단가 × (현재환율 − 평균매수환율)
            fxPnlKrw = fxPnlKrw.add(h.quantity().multiply(h.avgPrice()).multiply(fxGap));

            // 달러 기준에는 환차손익이 없다
            pricePnlUsd = pricePnlUsd.add(h.quantity().multiply(priceGap));

            valueUsd = valueUsd.add(h.quantity().multiply(h.currentPrice()));
            costKrw = costKrw.add(h.quantity().multiply(h.avgPrice()).multiply(h.avgFxRate()));
        }

        BigDecimal totalPnlKrw = pricePnlKrw.add(fxPnlKrw);   // 총손익은 두 손익을 더해서 만든다

        return new ProfitLossResponse(
                asOf,
                valueUsd.multiply(fxRate).setScale(KRW_SCALE, RoundingMode.HALF_UP),
                valueUsd.setScale(USD_SCALE, RoundingMode.HALF_UP),
                costKrw.setScale(KRW_SCALE, RoundingMode.HALF_UP),
                pricePnlKrw.setScale(KRW_SCALE, RoundingMode.HALF_UP),
                fxPnlKrw.setScale(KRW_SCALE, RoundingMode.HALF_UP),
                totalPnlKrw.setScale(KRW_SCALE, RoundingMode.HALF_UP),
                pricePnlUsd.setScale(USD_SCALE, RoundingMode.HALF_UP),
                pricePnlUsd.setScale(USD_SCALE, RoundingMode.HALF_UP),   // 달러 총손익 = 주가손익
                returnRate(totalPnlKrw, costKrw),
                stateOf(pricePnlKrw, fxPnlKrw),
                fxRate,
                substituted,
                skipped);
    }

    /** 매입 원가를 분모로 한 수익률을 구한다. 원가가 0 이면 0 이다. */
    private static BigDecimal returnRate(BigDecimal totalPnlKrw, BigDecimal costKrw) {
        if (costKrw.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return totalPnlKrw.divide(costKrw, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 두 손익의 부호 조합으로 상쇄 상태를 판정한다. 0 은 양수 쪽으로 본다. */
    private static String stateOf(BigDecimal pricePnl, BigDecimal fxPnl) {
        boolean priceUp = pricePnl.compareTo(BigDecimal.ZERO) >= 0;
        boolean fxUp = fxPnl.compareTo(BigDecimal.ZERO) >= 0;
        if (priceUp && fxUp) {
            return STATE_BOTH_POSITIVE;
        }
        if (!priceUp && !fxUp) {
            return STATE_BOTH_NEGATIVE;
        }
        return STATE_OFFSET;
    }
}
