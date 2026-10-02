/*
 * ProfitLossService — 손익 계산을 엮어 주는 곳
 *
 * 이 파일이 하는 일
 *   계산 자체는 ProfitLossCalculator 가 한다. 이 파일은 그 앞뒤를 맡는다 —
 *   DB 에서 보유 종목을 꺼내고, 그날 환율을 구해 넣고, 나온 결과를 돌려준다.
 *   환율을 못 구하면 손익이 성립하지 않으므로 계산하지 않고 비워 보낸다.
 */
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

/**
 * 손익 요인 분해. 개발명세서(API) PROFIT-03 · 화면 SR-003
 *
 * <p>계산은 {@link ProfitLossCalculator} 가 한다. 이 클래스는 <b>입력을 모아 넣고
 * 결과를 돌려주는 일</b>만 한다.
 */
@Service
@RequiredArgsConstructor
public class ProfitLossService {

    private final HoldingMapper holdingMapper;
    private final FxRateService fxRateService;

    /**
     * 전체 손익 분해. 대시보드가 쓴다.
     *
     * <p>환율이 없으면 <b>null 을 돌려준다.</b> 환율은 두 항 모두에 곱해지므로
     * 없으면 손익 자체가 성립하지 않는다(2.6). 오류가 아니라 값이 없는 상태다.
     */
    @Transactional(readOnly = true)
    public ProfitLossResponse ofUser(Long userId) {
        return calculate(userId, null);
    }

    /**
     * 한 종목의 손익 분해. 종목 상세가 쓴다.
     *
     * <p>전체와 <b>같은 코드</b>로 계산한다. 종목별로 계산해 합치는 구조라
     * 필터만 바뀐다(2.4).
     */
    @Transactional(readOnly = true)
    public ProfitLossResponse ofSymbol(Long userId, String symbol) {
        return calculate(userId, symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 공통 경로. 환율을 구하고 계산기에 넘긴다.
     *
     * <p>{@code fx} 범위가 확정 환율과 시세를 분리하면서 {@link FxRateService#findByDate}
     * 가 {@code FxRateResponse} 를 돌려주게 됐다. 여기서는 <b>확정값</b>을 쓴다 —
     * 보유 현황의 평가금액({@code HoldingService.todayRate})과 같은 값이어야 화면에서
     * 총평가금액과 손익이 어긋나지 않는다.
     */
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
