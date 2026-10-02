/*
 * DividendEstimateService — 예상 배당 생성 (PROFIT-12)
 *
 * 이 파일이 하는 일
 *   종목 배당 마스터(stock_dividends)와 매매 기록을 결합해, 배당락일에
 *   보유하고 있던 사람마다 예상 배당(ESTIMATED)을 만든다. 사용자는 실제
 *   입금액으로 확정만 하면 된다. 예상은 확정 전까지 손익 집계에서 빠진다.
 */
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

/**
 * 예상 배당 생성. 개발명세서(API) PROFIT-12 · 화면 SR-016
 *
 * <p>산출식은 [[2.1 기능 명세서]] 그대로다 —
 * 예상 세후 = 주당 배당금 × 배당락일 보유 수량 × (1 − 원천징수 15%).
 * 원화 환산은 지급일 환율이되, 지급일이 아직 오지 않았으면 지금 환율로
 * 어림한다 — 예상값이므로 확정 때 실제 환율로 바뀐다.
 *
 * <p>넣기는 INSERT IGNORE 다. 같은 (포트폴리오·종목·지급일)이 있으면 —
 * 사용자가 먼저 직접 입력했든 지난 배치가 만들었든 — 건드리지 않는다.
 * 그래서 같은 날 몇 번을 돌려도 안전하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendEstimateService {

    /**
     * 배당락일을 거슬러 보는 구간. 지급일이 배당락일보다 한 달쯤 늦는 종목이
     * 있어, 며칠 치만 보면 수집이 늦었을 때 그 사이 배당락일을 놓친다.
     */
    private static final int LOOKBACK_DAYS = 45;

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int USD_SCALE = 4;
    private static final int KRW_SCALE = 2;

    private final StockDividendMapper stockDividendMapper;
    private final DividendMapper dividendMapper;
    private final FxRateService fxRateService;

    /** 마지막 배당락일 기준으로 만든다. 배치가 부른다. */
    @Transactional
    public int produceLatest() {
        return produce(LocalDate.now(TradingClock.SERVICE_ZONE));
    }

    /**
     * {@code asOf} 까지 배당락일이 지난 이벤트로 예상 배당을 만든다.
     *
     * <p>배당락일이 아직 오지 않은 이벤트는 만들지 않는다 — 그날까지 보유할지
     * 알 수 없어 수량이 확정되지 않았다.
     *
     * @return 새로 만든 예상 배당 수
     */
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

    /** 지급일 환율. 지급일이 없거나 아직 오지 않았으면 지금까지의 값으로 어림한다. */
    private BigDecimal estimateFxRate(StockDividend event, LocalDate asOf) {
        LocalDate payDate = payDate(event);
        BigDecimal rate = fxRateService.rateOf(payDate.isAfter(asOf) ? asOf : payDate);
        return rate != null ? rate : fxRateService.rateOf(asOf);
    }

    /** 지급일이 비어 있으면 배당락일로 적는다 — pay_date 는 비울 수 없는 컬럼이다. */
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
