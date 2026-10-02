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

/** 일별 스냅샷을 찍고 자산 추이·기간 수익률 리포트를 만드는 서비스다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    /** 수익률 자리수. 스키마 DECIMAL(9,4). */
    private static final int RATE_SCALE = 4;

    private final DailySnapshotMapper snapshotMapper;
    private final HoldingMapper holdingMapper;
    private final PortfolioMapper portfolioMapper;
    private final FxRateService fxRateService;
    private final MarketCalendar marketCalendar;
    private final TradingClock tradingClock;

    /** 해당 날짜의 스냅샷을 전 사용자에 대해 찍는다. 거래일이 아니면 0 을 돌려준다. */
    @Transactional
    public int createDailySnapshot(LocalDate date) {
        if (!marketCalendar.isTradingDay(date)) {
            log.info("[스냅샷] {} 은 거래일이 아니라 건너뛴다", date);
            return 0;
        }
        // 반드시 그날 확정 환율을 쓴다. 오늘 시세로 과거를 찍으면 추이가 거짓이 된다
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

    /** 오늘 기준으로 스냅샷을 찍는다. */
    @Transactional
    public int createDailySnapshot() {
        return createDailySnapshot(tradingClock.today());
    }

    /** 놓친 날의 본인 스냅샷을 다시 찍는다. 거래일이 아니거나 환율·보유가 없으면 false 다. */
    @Transactional
    public boolean backfill(Long userId, LocalDate date) {
        // 미래 날짜는 막는다. 대체 환율로 미래 스냅샷이 생기면 차트가 오지 않은 날까지 뻗는다
        if (date.isAfter(tradingClock.today()) || !marketCalendar.isTradingDay(date)) {
            return false;
        }
        return fxRateService.findByDate(date)
                .map(rate -> snapshotOne(userId, date, rate))
                .orElse(false);
    }

    /** 사용자 한 명의 스냅샷을 찍는다. 보유가 없으면 찍지 않는다. */
    private boolean snapshotOne(Long userId, LocalDate date, FxRateResponse rate) {
        // 그날 이하의 마지막 종가를 쓴다. 최신 종가로 과거를 찍으면 추이가 거짓이 된다
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

    /** 기간의 자산 추이를 조회한다. */
    @Transactional(readOnly = true)
    public List<SnapshotResponse> series(Long userId, LocalDate from, LocalDate to) {
        return snapshotMapper.findByRange(userId, from, to);
    }

    /** 시작·끝 스냅샷 두 건으로 기간 수익률을 만든다. 스냅샷이 없으면 null 이다. */
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
