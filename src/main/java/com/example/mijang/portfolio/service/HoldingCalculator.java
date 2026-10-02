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

/** 거래 목록을 시간순으로 훑어 평단가(이동평균)·평균매수환율(금액가중)·실현손익을 구하는 순수 계산기다. */
public final class HoldingCalculator {

    /** 단가·수량 자리수. 스키마 DECIMAL(18,6). */
    private static final int PRICE_SCALE = 6;

    /** 환율 자리수. 스키마 DECIMAL(10,4). */
    private static final int FX_SCALE = 4;

    /** 원화 금액 자리수. */
    private static final int KRW_SCALE = 2;

    private HoldingCalculator() {
    }

    /** 한 종목 계산 결과다. 보유 현황과 매도 건별 실현손익·원가, 훑는 도중의 최저 수량을 담는다. */
    public record Calculation(Holding holding,
                              Map<Long, BigDecimal> realizedBySellId,
                              Map<Long, BigDecimal> costBasisBySellId,
                              BigDecimal minQuantity) {

        /** 훑는 도중 보유를 넘겨 판 시점이 있었는지 판별한다. */
        public boolean oversold() {
            return minQuantity.compareTo(BigDecimal.ZERO) < 0;
        }
    }

    /** 거래일 오름차순 거래 목록을 훑어 보유 현황을 만든다. 순서가 뒤바뀌면 평단가가 달라진다. */
    public static Holding calculate(String symbol, List<Transaction> transactions) {
        return calculateAll(symbol, transactions).holding();
    }

    /** 분할을 반영해 보유 현황을 계산한다. */
    public static Holding calculate(String symbol, List<Transaction> transactions,
                                    List<StockSplit> splits) {
        return calculateAll(symbol, adjustForSplits(transactions, splits)).holding();
    }

    /** 분할을 반영해 계산하고 매도 건별 실현손익까지 함께 준다. */
    public static Calculation calculateAll(String symbol, List<Transaction> transactions,
                                           List<StockSplit> splits) {
        return calculateAll(symbol, adjustForSplits(transactions, splits));
    }

    /** 분할 이전 거래를 지금 기준으로 환산한다. exDate 보다 앞선 거래만 수량×배수·단가÷배수로 보정한다. */
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

    /** 한 건에 분할 배수를 적용한 새 값을 만든다. */
    private static Transaction apply(Transaction tx, BigDecimal factor) {
        return new Transaction(tx.id(), tx.userId(), tx.portfolioId(), tx.symbol(), tx.side(),
                tx.quantity().multiply(factor).setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                tx.price().divide(factor, PRICE_SCALE, RoundingMode.HALF_UP),
                tx.fxRate(), tx.fee(), tx.tradedAt(), tx.tradeDate(),
                tx.buyReason(), tx.targetPrice(), tx.sentiment());
    }

    /** 보유 현황과 매도 건별 실현손익을 한 루프에서 함께 구한다. */
    public static Calculation calculateAll(String symbol, List<Transaction> transactions) {
        BigDecimal quantity = BigDecimal.ZERO;
        // 훑는 도중의 최저 수량. 마지막 값만 보면 과거 날짜로 끼워 넣은 초과 매도를 놓친다
        BigDecimal minQuantity = BigDecimal.ZERO;
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
            minQuantity = minQuantity.min(quantity);
        }

        Holding holding = new Holding(symbol,
                quantity.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                avgPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                avgFxRate.setScale(FX_SCALE, RoundingMode.HALF_UP),
                totalFee.setScale(4, RoundingMode.HALF_UP),
                realizedPnlKrw.setScale(KRW_SCALE, RoundingMode.HALF_UP));
        return new Calculation(holding,
                Collections.unmodifiableMap(realizedBySellId),
                Collections.unmodifiableMap(costBasisBySellId),
                minQuantity.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
    }

    /** 매수 금액(USD)으로 가중한 평균환율을 구한다. */
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

    /** 실현손익 = 수량 × (매도단가 × 매도환율 − 평단가 × 평균매수환율) − 수수료 × 매도환율. */
    private static BigDecimal realized(Transaction tx, BigDecimal avgPrice, BigDecimal avgFxRate) {
        BigDecimal sellKrw = tx.price().multiply(tx.fxRate());
        BigDecimal costKrw = avgPrice.multiply(avgFxRate);
        return tx.quantity().multiply(sellKrw.subtract(costKrw))
                .subtract(tx.fee().multiply(tx.fxRate()));
    }
}
