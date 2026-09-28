package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.stock.mapper.StockSplitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 거래와 분할을 모아 {@link HoldingCalculator} 에 넣는 유일한 입구다. 운영 코드는 이 클래스만 거친다. */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionMapper transactionMapper;
    private final StockSplitMapper splitMapper;

    /** 한 종목의 거래를 분할 반영해 처음부터 계산한다. */
    @Transactional(readOnly = true)
    public HoldingCalculator.Calculation calculationOf(Long userId, String symbol) {
        return HoldingCalculator.calculateAll(symbol,
                transactionMapper.findForRecalc(userId, symbol),
                splitMapper.findBySymbol(symbol));
    }
}
