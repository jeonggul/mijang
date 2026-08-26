/*
 * TaxService — 양도소득세 참고 계산
 *
 * 이 파일이 하는 일
 *   한 해의 매도들이 확정한 실현손익을 모아 기본공제·세율을 적용해 본다.
 *   새 계산이 아니다 — 건별 실현손익은 HoldingCalculator 가 이미 구하는
 *   값이고, 여기는 그것을 연도로 묶기만 한다. 같은 계산이 두 곳에 있으면
 *   언젠가 갈라진다(portfolio 2.10).
 *   참고값이다. 실제 신고 자료가 아니라는 경고는 화면이 띄운다.
 */
package com.example.mijang.portfolio.service;

import com.example.mijang.common.time.TradingClock;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.CapitalGainsResponse;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 양도소득세 참고 계산. 개발명세서(API) GLOBAL-06 · 화면 SR-009-1
 *
 * <p>실현손익은 <b>원화 기준</b>이다 — 매도환율과 평균매수환율이 계산에 이미
 * 들어 있어 환차손익이 포함된 금액이 나온다. 실제 세법과 다를 수 있는 지점
 * (결제일 기준·환율 고시 기준 등)은 화면의 참고 문구가 안내한다.
 */
@Service
@RequiredArgsConstructor
public class TaxService {

    /** 해외주식 양도소득 기본공제. 연 250만원. */
    private static final BigDecimal BASIC_DEDUCTION = new BigDecimal("2500000");

    /** 양도소득세 20% + 지방소득세 2%. */
    private static final BigDecimal TAX_RATE = new BigDecimal("0.22");

    private final TransactionMapper transactionMapper;

    /**
     * 한 해의 실현손익과 과세 추정.
     *
     * <p>{@code year} 를 비우면 매도가 있는 가장 최근 해, 그것도 없으면 올해다.
     * 전 종목의 거래를 처음부터 다시 훑는다 — 건별 실현손익이 그 시점의 평단가에
     * 달려 있어 연도만 잘라 계산할 수 없기 때문이다(portfolio 2.2).
     */
    @Transactional(readOnly = true)
    public CapitalGainsResponse capitalGains(Long userId, Integer year) {
        List<SellRealized> sells = collectSells(userId);

        TreeSet<Integer> years = new TreeSet<>();
        sells.forEach(s -> years.add(s.date().getYear()));

        int target = year != null ? year
                : years.isEmpty() ? LocalDate.now(TradingClock.SERVICE_ZONE).getYear()
                : years.last();

        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        int count = 0;
        for (SellRealized s : sells) {
            if (s.date().getYear() != target) {
                continue;
            }
            count++;
            if (s.realizedKrw().signum() >= 0) {
                gain = gain.add(s.realizedKrw());
            } else {
                loss = loss.add(s.realizedKrw());
            }
        }

        BigDecimal net = gain.add(loss);
        BigDecimal taxable = net.subtract(BASIC_DEDUCTION).max(BigDecimal.ZERO);
        BigDecimal tax = taxable.multiply(TAX_RATE).setScale(0, RoundingMode.HALF_UP);

        return new CapitalGainsResponse(target, count, gain, loss, net,
                BASIC_DEDUCTION, taxable, TAX_RATE, tax,
                years.descendingSet().stream().toList());
    }

    /** 매도 하나와 그 매도가 확정한 손익(원). */
    private record SellRealized(LocalDate date, BigDecimal realizedKrw) {
    }

    /** 전 종목을 훑어 매도 건별 실현손익을 모은다. 계산기는 portfolio 의 것을 그대로 쓴다. */
    private List<SellRealized> collectSells(Long userId) {
        List<SellRealized> sells = new ArrayList<>();
        for (String symbol : transactionMapper.findSymbolsByUser(userId)) {
            List<Transaction> txs = transactionMapper.findForRecalc(userId, symbol);
            Map<Long, BigDecimal> realized =
                    HoldingCalculator.calculateAll(symbol, txs).realizedBySellId();
            for (Transaction tx : txs) {
                BigDecimal r = tx.id() == null ? null : realized.get(tx.id());
                if (r != null) {
                    sells.add(new SellRealized(tx.tradeDate(), r));
                }
            }
        }
        sells.sort(Comparator.comparing(SellRealized::date));
        return sells;
    }
}
