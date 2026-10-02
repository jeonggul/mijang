package com.example.mijang.portfolio.service;

import com.example.mijang.fx.dto.FxRateResponse;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.dto.ProfitLossResponse;
import com.example.mijang.portfolio.dto.SymbolPnl;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보유·환율을 모아 {@link ProfitLossCalculator} 로 손익 분해를 만드는 서비스다. */
@Service
@RequiredArgsConstructor
public class ProfitLossService {

    private final HoldingMapper holdingMapper;
    private final FxRateService fxRateService;

    /** 전체 손익 분해를 구한다. 환율이 없으면 null 을 돌려준다. */
    @Transactional(readOnly = true)
    public ProfitLossResponse ofUser(Long userId) {
        return calculate(userId, null);
    }

    /** 한 종목의 손익 분해를 구한다. */
    @Transactional(readOnly = true)
    public ProfitLossResponse ofSymbol(Long userId, String symbol) {
        return calculate(userId, symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT));
    }

    /** 확정 환율을 구해 계산기에 넘긴다. 보유 현황 평가와 같은 환율이어야 한다. */
    private ProfitLossResponse calculate(Long userId, String symbol) {
        Optional<FxRateResponse> rate = fxRateService.findByDate(LocalDate.now());
        if (rate.isEmpty()) {
            return null;
        }
        List<SymbolPnl> holdings = holdingMapper.findForPnl(userId, symbol);
        return ProfitLossCalculator.calculate(
                holdings, rate.get().rate(), rate.get().rateDate(), rate.get().substituted());
    }
}
