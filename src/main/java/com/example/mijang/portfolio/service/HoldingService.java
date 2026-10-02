package com.example.mijang.portfolio.service;

import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.portfolio.domain.Holding;
import com.example.mijang.portfolio.dto.HoldingResponse;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보유 현황을 재계산해 저장하고 조회하는 서비스다. 계산 자체는 {@link HoldingCalculator} 가 한다. */
@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingMapper holdingMapper;
    private final FxRateService fxRateService;
    private final LedgerService ledgerService;

    /** 한 종목을 처음부터 전체 재계산해 저장한다. 보유를 넘겨 팔았으면 예외를 던진다. */
    @Transactional
    public Holding recalculate(Long userId, Long portfolioId, String symbol) {
        HoldingCalculator.Calculation calc = ledgerService.calculationOf(userId, symbol);
        Holding holding = calc.holding();

        // 최종 수량이 아니라 훑는 도중의 최저 수량으로 초과 매도를 잡는다
        if (calc.oversold()) {
            throw new BusinessException(ErrorCode.TX_QUANTITY_EXCEEDS_HOLDING, "quantity");
        }
        holdingMapper.upsert(userId, portfolioId, symbol,
                holding.quantity(), holding.avgPrice(), holding.avgFxRate(),
                holding.totalFee(), holding.realizedPnlKrw());
        return holding;
    }

    /** 보유 현황 목록을 조회한다. 환율이 없으면 원화 금액은 null 로 나온다. */
    @Transactional(readOnly = true)
    public List<HoldingResponse> findByUser(Long userId) {
        return holdingMapper.findByUser(userId, todayRate());
    }

    /** 총 평가금액(원)을 계산한다. 보유가 없으면 0 이다. */
    @Transactional(readOnly = true)
    public BigDecimal totalMarketValueKrw(Long userId) {
        BigDecimal sum = holdingMapper.sumMarketValueKrw(userId, todayRate());
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /** 평가에 쓸 오늘 환율을 구한다. 없으면 null 이다. */
    private BigDecimal todayRate() {
        return fxRateService.rateOf(LocalDate.now());
    }
}
