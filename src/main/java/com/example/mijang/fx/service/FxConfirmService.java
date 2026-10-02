package com.example.mijang.fx.service;

import com.example.mijang.config.FxProperties;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.domain.FxRate;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import com.example.mijang.fx.mapper.FxRateMapper;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 그날 마지막 시세를 {@code fx_rates} 확정값으로 옮긴다. {@code GLOBAL-01} */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxConfirmService {

    private static final String USD = "USD";

    private final FxQuoteMapper quoteMapper;
    private final FxRateMapper rateMapper;
    private final FxProperties props;

    /** 그날 환율을 확정한다. 이미 확정된 날은 손대지 않는다. */
    @Transactional
    public Optional<FxRate> confirm(LocalDate date) {
        FxRate already = rateMapper.findByDate(date);
        if (already != null) {
            return Optional.of(already);
        }

        FxQuote last = quoteMapper.findLastOfDate(USD, date);
        if (last != null) {
            // 수집 시각은 배치 시각이 아니라 벤더가 값을 찍은 시각을 쓴다.
            FxRate confirmed = FxRate.confirmed(date, last.basePrice(), last.quotedAt());
            rateMapper.insertIgnore(confirmed);
            log.info("[환율] {} 확정 {}", date, confirmed.usdKrw());
            return Optional.of(confirmed);
        }

        // 그날 시세가 없으면 직전 확정값을 복사하고 대체 사실을 남긴다.
        FxRate previous = rateMapper.findLatestBefore(date, props.getSubstituteLookbackDays());
        if (previous == null) {
            log.warn("[환율] {} 확정 실패 — 시세도 직전 값도 없다", date);
            return Optional.empty();
        }
        FxRate substitute = FxRate.substitute(date, previous.usdKrw(), previous.rateDate(),
                                              previous.collectedAt());
        rateMapper.insertIgnore(substitute);
        log.info("[환율] {} 확정 {} (← {} 복사)", date, substitute.usdKrw(), previous.rateDate());
        return Optional.of(substitute);
    }
}
