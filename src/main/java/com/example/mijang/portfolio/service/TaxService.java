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

/** 연도별 실현손익(원화 기준)에 기본공제·세율을 적용한 양도소득세 참고 계산 서비스다. */
@Service
@RequiredArgsConstructor
public class TaxService {

    /** 해외주식 양도소득 기본공제. 연 250만원. */
    private static final BigDecimal BASIC_DEDUCTION = new BigDecimal("2500000");

    /** 양도소득세 20% + 지방소득세 2%. */
    private static final BigDecimal TAX_RATE = new BigDecimal("0.22");

    private final TransactionMapper transactionMapper;
    private final LedgerService ledgerService;

    /** 한 해의 실현손익과 과세 추정을 계산한다. year 를 비우면 매도가 있는 가장 최근 해다. */
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

    /** 전 종목을 LedgerService 로 훑어 매도 건별 실현손익을 모은다. */
    private List<SellRealized> collectSells(Long userId) {
        List<SellRealized> sells = new ArrayList<>();
        for (String symbol : transactionMapper.findSymbolsByUser(userId)) {
            List<Transaction> txs = transactionMapper.findForRecalc(userId, symbol);
            Map<Long, BigDecimal> realized =
                    ledgerService.calculationOf(userId, symbol).realizedBySellId();
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
