/*
 * ProfitLossCalculator — 손익을 실제로 계산하는 곳
 *
 * 이 파일이 하는 일
 *   이 범위의 심장이다. 종목별 값을 받아 주가손익·환차손익·합계를 구한다.
 *   DB 도 스프링도 모르는 순수 계산이라 테스트하기 쉽다 — 간판 기능이라
 *   숫자가 틀리면 안 되고, 틀리지 않으려면 검사가 쉬워야 한다.
 */
package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.dto.ProfitLossResponse;
import com.example.mijang.portfolio.dto.SymbolPnl;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 손익 요인 분해 계산. <b>DB 도 스프링도 모른다.</b>
 *
 * <p>{@code HoldingCalculator} 와 같은 이유로 순수 계산으로 떼어 두었다 —
 * 이 서비스의 시그니처 기능이라 테스트가 쉬워야 한다.
 */
public final class ProfitLossCalculator {

    /** 원화 금액 자리수. */
    private static final int KRW_SCALE = 2;
    /** 달러 금액 자리수. */
    private static final int USD_SCALE = 4;
    /** 수익률 자리수. 0.0408 = +4.08%. 스키마의 DECIMAL(9,4) 에 맞춘다. */
    private static final int RATE_SCALE = 4;

    /** 주가·환율 손익의 부호 조합. 서버가 판정한다(2.3). */
    public static final String STATE_BOTH_POSITIVE = "BOTH_POSITIVE";
    public static final String STATE_BOTH_NEGATIVE = "BOTH_NEGATIVE";
    public static final String STATE_OFFSET = "OFFSET";

    private ProfitLossCalculator() {
    }

    /**
     * 종목별 손익을 계산해 합친다.
     *
     * <p>전체를 한 번에 계산하지 않는 이유 — 종목마다 평단가와 평균매수환율이 다르다(2.4).
     *
     * <p>총손익은 두 손익을 <b>더해서</b> 만든다. 따로 계산하면 반올림 때문에
     * 화면의 두 값이 합계와 어긋난다(2.2).
     *
     * @param holdings  보유 종목들. 현재가가 없는 것은 알아서 걸러진다
     * @param fxRate    평가에 쓸 현재 환율. null 이면 안 된다 — 호출부가 먼저 확인한다(2.6)
     * @param asOf      기준일. 응답에 그대로 담는다
     * @param substituted 환율이 대체값인지
     */
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
                skipped++;   // 현재가가 없으면 뺀다. 0 으로 두면 전량 손실로 잡힌다(2.5)
                continue;
            }
            BigDecimal priceGap = h.currentPrice().subtract(h.avgPrice());
            BigDecimal fxGap = fxRate.subtract(h.avgFxRate());

            // 주가손익 = 수량 × (현재가 − 평단가) × 현재환율
            pricePnlKrw = pricePnlKrw.add(h.quantity().multiply(priceGap).multiply(fxRate));
            // 환차손익 = 수량 × 평단가 × (현재환율 − 평균매수환율)
            fxPnlKrw = fxPnlKrw.add(h.quantity().multiply(h.avgPrice()).multiply(fxGap));

            // 달러 기준에는 환차손익이 없다(2.7)
            pricePnlUsd = pricePnlUsd.add(h.quantity().multiply(priceGap));

            valueUsd = valueUsd.add(h.quantity().multiply(h.currentPrice()));
            costKrw = costKrw.add(h.quantity().multiply(h.avgPrice()).multiply(h.avgFxRate()));
        }

        BigDecimal totalPnlKrw = pricePnlKrw.add(fxPnlKrw);   // 더해서 만든다(2.2)

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

    /**
     * 수익률. 분모는 <b>매입 원가</b>다(2.8).
     *
     * <p>원가가 0 이면(보유 없음) 0 을 돌려준다. 나누면 예외다.
     */
    private static BigDecimal returnRate(BigDecimal totalPnlKrw, BigDecimal costKrw) {
        if (costKrw.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return totalPnlKrw.divide(costKrw, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 상쇄 상태 판정. <b>화면이 아니라 여기서 정한다</b>(2.3).
     *
     * <p>0 은 양수 쪽으로 본다. 명세의 조건이 "주가 ≥ 0, 환율 ≥ 0" 이다.
     */
    private static String stateOf(BigDecimal pricePnl, BigDecimal fxPnl) {
        boolean priceUp = pricePnl.compareTo(BigDecimal.ZERO) >= 0;
        boolean fxUp = fxPnl.compareTo(BigDecimal.ZERO) >= 0;
        if (priceUp && fxUp) {
            return STATE_BOTH_POSITIVE;
        }
        if (!priceUp && !fxUp) {
            return STATE_BOTH_NEGATIVE;
        }
        return STATE_OFFSET;   // 이 상태를 보여주려고 만든 서비스다
    }
}
