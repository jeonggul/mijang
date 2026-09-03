/*
 * HoldingService — 보유 현황을 다시 계산하고 꺼내 주는 곳
 *
 * 이 파일이 하는 일
 *   계산 자체는 HoldingCalculator 가 한다. 이 파일은 그 앞뒤를 맡는다 —
 *   거래를 DB 에서 꺼내 계산기에 넣고, 나온 결과를 holdings 에 저장한다.
 *   화면이 보유 목록을 물어보면 현재가·평가금액을 붙여 돌려준다.
 */
package com.example.mijang.portfolio.service;

import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.portfolio.domain.Holding;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.stock.mapper.StockSplitMapper;
import com.example.mijang.portfolio.dto.HoldingResponse;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 현황 재계산·조회. 개발명세서(API) ACCOUNT-04·05·07
 *
 * <p>계산 자체는 {@link HoldingCalculator} 가 한다. 이 클래스는 <b>DB 에서 읽어 계산기에 넣고
 * 결과를 저장하는 일</b>만 한다.
 */
@Service
@RequiredArgsConstructor
public class HoldingService {

    private final TransactionMapper transactionMapper;
    private final HoldingMapper holdingMapper;
    private final FxRateService fxRateService;
    private final StockSplitMapper splitMapper;

    /**
     * 한 종목을 처음부터 다시 계산해 저장한다.
     *
     * <p>증분이 아니라 <b>전체 재생</b>이다(2.2). 과거 날짜를 나중에 넣거나 중간 기록을 지워도
     * 항상 옳은 값이 나온다.
     *
     * @return 계산된 보유 현황
     * @throws BusinessException 어느 시점에든 보유를 넘겨 팔았을 때(400)
     */
    @Transactional
    public Holding recalculate(Long userId, Long portfolioId, String symbol) {
        List<Transaction> transactions = transactionMapper.findForRecalc(userId, symbol);
        /* 분할 이전 거래는 지금 기준으로 환산해서 센다. 시세는 이미 분할이 반영된
           값으로 들어오는데 원장은 그날 체결한 그대로라, 보정하지 않으면 4:1 분할 뒤
           평가금액이 4분의 1로 보인다 */
        HoldingCalculator.Calculation calc = HoldingCalculator.calculateAll(
                symbol, transactions, splitMapper.findBySymbol(symbol));
        Holding holding = calc.holding();

        /* 최종 수량이 아니라 훑는 <b>도중</b>의 최저 수량을 본다. 최종 수량만 보면
           매수보다 앞선 날짜로 끼워 넣은 초과 매도를 놓친다 — 뒤에 오는 매수가 수량을
           도로 양수로 만들어 검사를 통과시키고, 그 매도는 원가 0 으로 계산돼
           평단가와 실현손익이 함께 틀어진 채 저장된다(2026-09-03 점검 3.2).

           holdings 에 ck_holdings_quantity CHECK(>=0) 이 걸려 있어 음수를 그대로 넣으면
           SQL 예외가 먼저 터지고 화면에는 "서버 오류" 로 나간다 — 보유량을 넘겨 팔았다는
           사실이 사용자에게 전달되지 않는다(2.5) */
        if (calc.oversold()) {
            throw new BusinessException(ErrorCode.TX_QUANTITY_EXCEEDS_HOLDING, "quantity");
        }
        holdingMapper.upsert(userId, portfolioId, symbol,
                holding.quantity(), holding.avgPrice(), holding.avgFxRate(),
                holding.totalFee(), holding.realizedPnlKrw());
        return holding;
    }

    /**
     * 보유 현황 목록. 평가금액은 오늘 환율로 환산한다.
     *
     * <p>환율이 없으면 원화 금액이 null 로 나온다. 오류로 막지 않는 이유 —
     * 수량과 평단가는 환율과 무관하게 맞는 값이고, 그것만이라도 보여야 한다.
     */
    @Transactional(readOnly = true)
    public List<HoldingResponse> findByUser(Long userId) {
        return holdingMapper.findByUser(userId, todayRate());
    }

    /** 총 평가금액(원). {@code ACCOUNT-07}. 보유가 없으면 0. */
    @Transactional(readOnly = true)
    public BigDecimal totalMarketValueKrw(Long userId) {
        BigDecimal sum = holdingMapper.sumMarketValueKrw(userId, todayRate());
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /**
     * 평가에 쓸 오늘 환율.
     *
     * <p>주말이면 fx 범위가 직전 영업일 값으로 대체해 준다([[미장-fx-구현]] 2.1).
     * 그마저 없으면 null 이고, 원화 금액은 계산되지 않는다.
     */
    private BigDecimal todayRate() {
        return fxRateService.rateOf(LocalDate.now());
    }
}
