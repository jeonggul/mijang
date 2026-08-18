/*
 * SnapshotService — 스냅샷을 찍고 리포트를 만드는 곳
 *
 * 이 파일이 하는 일
 *   두 가지 일을 한다.
 *     ① 배치가 부르면 그날의 평가금액·손익을 계산해 한 줄 찍어 둔다.
 *     ② 화면이 부르면 쌓인 스냅샷을 기간으로 뽑아 추이와 수익률로 만들어 준다.
 *   계산은 대시보드와 같은 ProfitLossCalculator 를 쓴다. 식을 따로 두면
 *   대시보드 숫자와 리포트 숫자가 어긋난다.
 */
package com.example.mijang.portfolio.service;

import com.example.mijang.common.time.MarketCalendar;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.fx.dto.FxRateResponse;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.dto.PeriodReturnResponse;
import com.example.mijang.portfolio.dto.ProfitLossResponse;
import com.example.mijang.portfolio.dto.SnapshotResponse;
import com.example.mijang.portfolio.dto.SymbolPnl;
import com.example.mijang.portfolio.mapper.DailySnapshotMapper;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import com.example.mijang.portfolio.mapper.PortfolioMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 스냅샷과 기간 리포트. 개발명세서(API) PROFIT-06·09 · 화면 SR-009
 *
 * <p>계산은 {@link ProfitLossCalculator} 를 그대로 쓴다. 별도 식을 두면
 * 대시보드 값과 리포트 값이 어긋난다(2.2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    /** 수익률 자리수. 스키마의 DECIMAL(9,4). */
    private static final int RATE_SCALE = 4;

    private final DailySnapshotMapper snapshotMapper;
    private final HoldingMapper holdingMapper;
    private final PortfolioMapper portfolioMapper;
    private final FxRateService fxRateService;
    private final MarketCalendar marketCalendar;
    private final TradingClock tradingClock;

    /**
     * 하루치 스냅샷을 전 사용자에 대해 찍는다. 배치가 부른다.
     *
     * <p>거래일이 아니면 아무 것도 하지 않는다(2.3). 주말에 찍으면 같은 값이 반복되어
     * 차트에 평평한 구간이 생긴다.
     *
     * <p>날짜를 인자로 받는 이유 — 배치가 실패한 날을 나중에 메울 수 있어야 한다(2.4).
     *
     * @param date 찍을 거래일
     * @return 찍은 사용자 수
     */
    @Transactional
    public int createDailySnapshot(LocalDate date) {
        if (!marketCalendar.isTradingDay(date)) {
            log.info("[스냅샷] {} 은 거래일이 아니라 건너뛴다", date);
            return 0;
        }
        /* fx 가 확정값과 시세를 분리한 뒤 FxRateResponse 를 돌려준다([[미장-fx-구현]] v1.1).
           여기서는 반드시 그날 **확정값**이다 — 오늘 시세로 과거를 찍으면 추이가 거짓이 된다(2.4) */
        Optional<FxRateResponse> rate = fxRateService.findByDate(date);
        if (rate.isEmpty()) {
            log.warn("[스냅샷] {} 환율이 없어 찍지 못한다", date);
            return 0;
        }

        List<Long> userIds = snapshotMapper.findUserIdsWithHoldings();
        int done = 0;
        for (Long userId : userIds) {
            if (snapshotOne(userId, date, rate.get())) {
                done++;
            }
        }
        log.info("[스냅샷] {} — {}명 저장", date, done);
        return done;
    }

    /** 오늘 기준. 배치의 기본 호출이다. */
    @Transactional
    public int createDailySnapshot() {
        return createDailySnapshot(tradingClock.today());
    }

    /**
     * 사용자 한 명의 그날 스냅샷을 다시 찍는다. <b>놓친 날을 메우는 입구</b>다(2.4).
     *
     * <p>배치가 실패하거나 서버가 꺼져 있으면 그날 행이 빈 채로 남고, 차트에 구멍이 된다.
     * 날짜를 받아 도는 경로는 있었지만 <b>바깥에서 부를 방법이 없었다</b> — 그래서 여기를 연다.
     *
     * <p>전 사용자를 도는 배치와 달리 <b>부른 사람 것만</b> 건드린다. 남의 스냅샷까지
     * 다시 쓰게 두면 화면에서 누를 수 있는 버튼이 위험해진다.
     *
     * @return 찍었으면 true. 거래일이 아니거나 환율·보유가 없으면 false
     */
    @Transactional
    public boolean backfill(Long userId, LocalDate date) {
        /* 아직 오지 않은 날은 메울 것이 없다. 막지 않으면 대체 환율로 미래 스냅샷이 생기고
           차트가 오지 않은 날까지 뻗는다 */
        if (date.isAfter(tradingClock.today()) || !marketCalendar.isTradingDay(date)) {
            return false;
        }
        return fxRateService.findByDate(date)
                .map(rate -> snapshotOne(userId, date, rate))
                .orElse(false);
    }

    /**
     * 사용자 한 명의 스냅샷.
     *
     * <p>보유가 없으면 찍지 않는다(2.6).
     *
     * @return 저장했으면 true
     */
    private boolean snapshotOne(Long userId, LocalDate date, FxRateResponse rate) {
        /* 그날 이하의 마지막 종가를 쓴다. findForPnl 은 언제나 최신 종가를 붙이므로
           놓친 날을 메울 때 그것을 쓰면 과거 추이가 통째로 거짓이 된다(2.4) */
        List<SymbolPnl> holdings = holdingMapper.findForPnlAsOf(userId, null, date);
        if (holdings.isEmpty()) {
            return false;
        }
        ProfitLossResponse pnl = ProfitLossCalculator.calculate(
                holdings, rate.rate(), date, rate.substituted());

        Long portfolioId = portfolioMapper.findDefaultId(userId);
        if (portfolioId == null) {
            return false;   // 보유는 있는데 포트폴리오가 없다면 데이터가 어긋난 상태다
        }
        snapshotMapper.upsert(userId, portfolioId, date,
                pnl.totalValueUsd(), pnl.totalValueKrw(), pnl.costBasisKrw(),
                pnl.pricePnlKrw(), pnl.fxPnlKrw(), pnl.totalPnlKrw(),
                pnl.returnRate(), pnl.appliedFxRate(), pnl.fxSubstituted());
        return true;
    }

    /** 자산 추이. {@code PROFIT-09}. 차트가 그대로 쓴다. */
    @Transactional(readOnly = true)
    public List<SnapshotResponse> series(Long userId, LocalDate from, LocalDate to) {
        return snapshotMapper.findByRange(userId, from, to);
    }

    /**
     * 기간 수익률. {@code PROFIT-06}
     *
     * <p>스냅샷 <b>두 건만</b> 읽는다(2.1). 시작일에 정확히 스냅샷이 없으면
     * 그 이후 첫 행을 시작점으로 삼는다 — 주말이 시작일인 경우다.
     *
     * <p>중간 추가 매수는 반영하지 않는다(2.7). 그 한계는 8장에 적어 두었다.
     *
     * @return 스냅샷이 없으면 null
     */
    @Transactional(readOnly = true)
    public PeriodReturnResponse periodReturn(Long userId, LocalDate from, LocalDate to) {
        SnapshotResponse start = snapshotMapper.findFirstOnOrAfter(userId, from);
        SnapshotResponse end = snapshotMapper.findLastOnOrBefore(userId, to);
        if (start == null || end == null || start.snapshotDate().isAfter(end.snapshotDate())) {
            return null;
        }

        BigDecimal startValue = start.marketValueKrw();
        BigDecimal endValue = end.marketValueKrw();
        BigDecimal change = endValue.subtract(startValue);

        // 시작 평가액이 0 이면 나눌 수 없다. 수익률을 0 으로 둔다
        BigDecimal rate = startValue.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(RATE_SCALE)
                : change.divide(startValue, RATE_SCALE, RoundingMode.HALF_UP);

        return new PeriodReturnResponse(
                start.snapshotDate(), end.snapshotDate(), startValue, endValue, change, rate);
    }
}
