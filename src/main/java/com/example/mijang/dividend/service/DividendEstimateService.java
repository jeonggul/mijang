package com.example.mijang.dividend.service;

import com.example.mijang.common.time.TradingClock;
import com.example.mijang.dividend.domain.Dividend;
import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.dto.HolderAtExDate;
import com.example.mijang.dividend.mapper.DividendMapper;
import com.example.mijang.dividend.mapper.StockDividendMapper;
import com.example.mijang.fx.service.FxRateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배당락일 보유자별 예상 배당(ESTIMATED)을 INSERT IGNORE 로 생성한다. PROFIT-12 · SR-016. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendEstimateService {

    /** 배당락일을 거슬러 보는 구간. 짧으면 수집이 늦었을 때 배당락일을 놓친다. */
    private static final int LOOKBACK_DAYS = 45;

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int USD_SCALE = 4;
    private static final int KRW_SCALE = 2;

    private final StockDividendMapper stockDividendMapper;
    private final DividendMapper dividendMapper;
    private final FxRateService fxRateService;

    /** 오늘 기준으로 예상 배당을 생성한다. */
    @Transactional
    public int produceLatest() {
        return produce(LocalDate.now(TradingClock.SERVICE_ZONE));
    }

    /** asOf 까지 배당락일이 지난 이벤트로 예상 배당을 만들고 생성 건수를 반환한다. */
    @Transactional
    public int produce(LocalDate asOf) {
        List<StockDividend> events = stockDividendMapper.findByExDateBetween(
                asOf.minusDays(LOOKBACK_DAYS), asOf);
        int created = 0;
        for (StockDividend event : events) {
            if (event.amountPerShare().signum() <= 0) {
                continue;
            }
            BigDecimal fxRate = estimateFxRate(event, asOf);
            if (fxRate == null) {
                log.warn("예상 배당 건너뜀 — {} {} 환율 없음", event.symbol(), event.exDate());
                continue;
            }
            for (HolderAtExDate holder : stockDividendMapper.findHoldersAtExDate(
                    event.symbol(), event.exDate())) {
                created += dividendMapper.insertIgnore(estimated(event, holder, fxRate));
            }
        }
        return created;
    }

    /** 지급일 환율을 구하되, 지급일이 아직 오지 않았으면 현재까지의 값으로 어림한다. */
    private BigDecimal estimateFxRate(StockDividend event, LocalDate asOf) {
        LocalDate payDate = payDate(event);
        BigDecimal rate = fxRateService.rateOf(payDate.isAfter(asOf) ? asOf : payDate);
        return rate != null ? rate : fxRateService.rateOf(asOf);
    }

    /** 지급일이 비어 있으면 배당락일로 대신한다 — pay_date 는 비울 수 없는 컬럼이다. */
    private static LocalDate payDate(StockDividend event) {
        return event.payableDate() != null ? event.payableDate() : event.exDate();
    }

    private static Dividend estimated(StockDividend event, HolderAtExDate holder,
                                      BigDecimal fxRate) {
        BigDecimal gross = event.amountPerShare().multiply(holder.quantity())
                .setScale(USD_SCALE, RoundingMode.HALF_UP);
        BigDecimal net = gross.multiply(ONE.subtract(Dividend.DEFAULT_WITHHOLDING))
                .setScale(USD_SCALE, RoundingMode.HALF_UP);
        BigDecimal krw = net.multiply(fxRate).setScale(KRW_SCALE, RoundingMode.HALF_UP);
        return new Dividend(null, holder.userId(), holder.portfolioId(), event.symbol(),
                event.exDate(), payDate(event), event.amountPerShare(), holder.quantity(),
                gross, net, Dividend.DEFAULT_WITHHOLDING, fxRate, krw,
                "ESTIMATED", "VENDOR", null);
    }
}
