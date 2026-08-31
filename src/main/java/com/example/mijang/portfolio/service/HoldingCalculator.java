/*
 * HoldingCalculator — 보유 현황을 계산하는 곳
 *
 * 이 파일이 하는 일
 *   이 범위의 심장이다. 거래 목록을 시간순으로 훑어 평단가·평균매수환율·
 *   실현손익을 구한다.
 *   DB 도 스프링도 모르는 순수 계산이다. 이 서비스에서 가장 틀리면 안 되는
 *   코드라, 입력과 출력만 있어 검사하기 쉬운 모양으로 떼어 두었다.
 *   평단가는 이동평균, 평균환율은 금액가중평균으로 구한다.
 */
package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.domain.Holding;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.stock.domain.StockSplit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 보유 현황 계산. <b>DB 도 스프링도 모른다</b>(2.10).
 *
 * <p>이 서비스에서 가장 틀리면 안 되는 코드라 순수 계산으로 떼어 두었다.
 * 입력(거래 목록)과 출력(보유 현황)만 있어 테스트가 쉽다.
 *
 * <p>계산 방식은 [[미장-기획서]] 6장 — 평단가는 이동평균, 평균환율은 금액가중평균(2.3).
 */
public final class HoldingCalculator {

    /** 단가·수량 자리수. 스키마의 DECIMAL(18,6) 에 맞춘다. */
    private static final int PRICE_SCALE = 6;

    /** 환율 자리수. 스키마의 DECIMAL(10,4). */
    private static final int FX_SCALE = 4;

    /** 원화 금액 자리수. 원 단위 아래는 반올림한다. */
    private static final int KRW_SCALE = 2;

    private HoldingCalculator() {
    }

    /**
     * 한 종목을 훑은 결과 전부.
     *
     * <p>보유 현황과 <b>매도 건별 실현손익</b>을 함께 담는다. 목록 화면이 매도 한 줄마다
     * "실현 +142,600원" 을 보여주려면 건별 값이 필요한데, 그 값은 <b>그 시점의 평단가</b>에
     * 달려 있어 거래 하나만 떼어 보면 구할 수 없다.
     *
     * <p>원장(`transactions`)에 저장하지 않는 이유 — 파생값이기 때문이다(2.1). 과거 날짜를
     * 나중에 끼워 넣으면 그 뒤 매도들의 실현손익이 전부 달라진다. 저장해 두면 그 순간 틀린 값이 된다.
     *
     * <p>원가({@code costBasisBySellId})도 같이 담는다. 손익 <b>금액</b>만으로는 수익률을
     * 만들 수 없고, 나눌 원가는 그 매도 시점의 평단가·평균매수환율에서 나오므로 이 루프
     * 밖에서는 다시 구할 수 없다. 커뮤니티 글에 붙는 매매 카드가 이 값으로 수익률을 낸다.
     *
     * @param holding          보유 현황
     * @param realizedBySellId 매도 기록 id → 그 매도가 확정한 손익(원). 매수는 들어 있지 않다
     * @param costBasisBySellId 매도 기록 id → 그 매도가 처분한 몫의 원가(원). 수수료는 빼지 않는다 —
     *                          원가는 원가고, 수수료는 손익 쪽에서 이미 차감됐다
     */
    public record Calculation(Holding holding,
                              Map<Long, BigDecimal> realizedBySellId,
                              Map<Long, BigDecimal> costBasisBySellId) {
    }

    /**
     * 한 종목의 거래를 처음부터 훑어 보유 현황을 만든다.
     *
     * <p><b>거래일 순서대로 들어와야 한다.</b> 순서가 뒤바뀌면 평단가가 달라진다.
     * 정렬은 호출부(매퍼의 ORDER BY)가 보장한다.
     *
     * <p>매도 수량이 보유량을 넘으면 그 시점에서 수량이 음수가 된다. 예외를 던지지 않고
     * 음수 그대로 돌려주며, <b>거절 여부는 서비스가 판단한다</b>(2.5) — 계산기는 규칙이 아니라
     * 산수만 책임진다.
     *
     * @param symbol       종목 티커
     * @param transactions 거래일 오름차순으로 정렬된 해당 종목의 거래 전부
     */
    public static Holding calculate(String symbol, List<Transaction> transactions) {
        return calculateAll(symbol, transactions).holding();
    }

    /** 분할을 반영해 계산한다. 분할이 없으면 위와 같다. */
    public static Holding calculate(String symbol, List<Transaction> transactions,
                                    List<StockSplit> splits) {
        return calculateAll(symbol, adjustForSplits(transactions, splits)).holding();
    }

    /** 분할을 반영해 계산한다. 매도 건별 실현손익까지 함께 준다. */
    public static Calculation calculateAll(String symbol, List<Transaction> transactions,
                                           List<StockSplit> splits) {
        return calculateAll(symbol, adjustForSplits(transactions, splits));
    }

    /**
     * 분할 이전 거래를 지금 기준으로 환산한다.
     *
     * <p>4:1 분할 전에 10주를 $400 에 샀다면, 지금 기준으로는 40주를 $100 에 산 것과 같다.
     * <b>수량에 배수를 곱하고 단가를 배수로 나눈다.</b> 두 값을 곱한 체결 금액은 그대로다 —
     * 분할은 가진 몫을 쪼갤 뿐 돈이 오가지 않는다.
     *
     * <p>기준일 <b>당일</b> 거래는 이미 조정된 값으로 체결된다. 그래서 {@code exDate} 보다
     * <b>앞선</b> 거래만 보정한다. 여기서 경계를 하루 잘못 잡으면 그날 산 사람의 수량만
     * 배수만큼 틀어지는데, 화면에는 그냥 "수량이 이상하다" 로만 보인다.
     *
     * <p>수수료는 건드리지 않는다. 실제로 낸 돈이라 분할과 무관하다.
     */
    static List<Transaction> adjustForSplits(List<Transaction> transactions,
                                             List<StockSplit> splits) {
        if (splits == null || splits.isEmpty() || transactions.isEmpty()) {
            return transactions;
        }
        List<Transaction> adjusted = new ArrayList<>(transactions.size());
        for (Transaction tx : transactions) {
            BigDecimal factor = BigDecimal.ONE;
            for (StockSplit split : splits) {
                if (split.exDate() != null && tx.tradeDate() != null
                        && tx.tradeDate().isBefore(split.exDate())) {
                    factor = factor.multiply(split.factor());
                }
            }
            adjusted.add(factor.compareTo(BigDecimal.ONE) == 0 ? tx : apply(tx, factor));
        }
        return adjusted;
    }

    /** 한 건에 배수를 먹인다. 원본은 그대로 두고 새 값을 만든다 — 원장은 고쳐 쓰지 않는다. */
    private static Transaction apply(Transaction tx, BigDecimal factor) {
        return new Transaction(tx.id(), tx.userId(), tx.portfolioId(), tx.symbol(), tx.side(),
                tx.quantity().multiply(factor).setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                tx.price().divide(factor, PRICE_SCALE, RoundingMode.HALF_UP),
                tx.fxRate(), tx.fee(), tx.tradedAt(), tx.tradeDate(),
                tx.buyReason(), tx.targetPrice(), tx.sentiment());
    }

    /**
     * 보유 현황과 매도 건별 실현손익을 <b>한 번에</b> 구한다.
     *
     * <p>훑는 루프가 하나뿐인 것이 중요하다. 건별 실현손익을 따로 계산하는 루프를 하나 더 두면
     * 합계와 건별 값이 어긋나는 날이 온다 — 같은 계산이 두 곳에 있으면 언젠가 갈라진다(2.10).
     */
    public static Calculation calculateAll(String symbol, List<Transaction> transactions) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal avgPrice = BigDecimal.ZERO;
        BigDecimal avgFxRate = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal realizedPnlKrw = BigDecimal.ZERO;
        Map<Long, BigDecimal> realizedBySellId = new LinkedHashMap<>();
        Map<Long, BigDecimal> costBasisBySellId = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            totalFee = totalFee.add(tx.fee());

            if (tx.buy()) {
                BigDecimal existingCost = quantity.multiply(avgPrice);
                BigDecimal addedCost = tx.amountUsd();
                BigDecimal newQuantity = quantity.add(tx.quantity());

                // 첫 매수이거나 전량 매도 후 재매수면 이번 거래 값이 그대로 평균이 된다
                if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                avgFxRate = weightedFx(existingCost, avgFxRate, addedCost, tx.fxRate());
                avgPrice = existingCost.add(addedCost)
                        .divide(newQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
                quantity = newQuantity;
            } else {
                BigDecimal thisSell = realized(tx, avgPrice, avgFxRate);
                realizedPnlKrw = realizedPnlKrw.add(thisSell);
                if (tx.id() != null) {
                    realizedBySellId.put(tx.id(), thisSell.setScale(KRW_SCALE, RoundingMode.HALF_UP));
                    // 처분한 몫의 원가. 평단가·평균환율이 이 매도 뒤에 바뀔 수 있으므로 지금 담는다
                    costBasisBySellId.put(tx.id(), tx.quantity()
                            .multiply(avgPrice).multiply(avgFxRate)
                            .setScale(KRW_SCALE, RoundingMode.HALF_UP));
                }
                // 매도는 평단가·평균환율을 바꾸지 않는다. 판 것은 남은 것의 원가와 무관하다(2.3)
                quantity = quantity.subtract(tx.quantity());
            }
        }

        Holding holding = new Holding(symbol,
                quantity.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                avgPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                avgFxRate.setScale(FX_SCALE, RoundingMode.HALF_UP),
                totalFee.setScale(4, RoundingMode.HALF_UP),
                realizedPnlKrw.setScale(KRW_SCALE, RoundingMode.HALF_UP));
        return new Calculation(holding,
                Collections.unmodifiableMap(realizedBySellId),
                Collections.unmodifiableMap(costBasisBySellId));
    }

    /**
     * 금액가중 평균환율.
     *
     * <p>수량이 아니라 <b>매수 금액(USD)</b>으로 가중한다. 1주에 $500 짜리와 100주에 $5 짜리는
     * 같은 100주여도 환율 노출 금액이 다르다. 수량으로 가중하면 싼 종목의 환율이 과대평가된다(2.3).
     *
     * <p>기존 금액이 0(첫 매수)이면 이번 환율이 그대로 평균이 된다.
     */
    private static BigDecimal weightedFx(BigDecimal existingCost, BigDecimal existingFx,
                                         BigDecimal addedCost, BigDecimal addedFx) {
        BigDecimal totalCost = existingCost.add(addedCost);
        if (totalCost.compareTo(BigDecimal.ZERO) == 0) {
            return addedFx;
        }
        return existingCost.multiply(existingFx)
                .add(addedCost.multiply(addedFx))
                .divide(totalCost, FX_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 이 매도로 확정된 손익(원).
     *
     * <pre>
     * 실현손익 = 수량 × (매도단가 × 매도환율 − 평단가 × 평균매수환율) − 수수료 × 매도환율
     * </pre>
     *
     * <p>수수료를 빼는 이유 — 수수료도 실제로 나간 돈이다. 빼지 않으면 실현손익이
     * 실제보다 좋아 보인다.
     */
    private static BigDecimal realized(Transaction tx, BigDecimal avgPrice, BigDecimal avgFxRate) {
        BigDecimal sellKrw = tx.price().multiply(tx.fxRate());
        BigDecimal costKrw = avgPrice.multiply(avgFxRate);
        return tx.quantity().multiply(sellKrw.subtract(costKrw))
                .subtract(tx.fee().multiply(tx.fxRate()));
    }
}
