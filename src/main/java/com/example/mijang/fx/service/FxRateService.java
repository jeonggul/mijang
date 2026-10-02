package com.example.mijang.fx.service;

import com.example.mijang.config.FxProperties;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.domain.FxRate;
import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.fx.dto.FxRateResponse;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import com.example.mijang.fx.mapper.FxRateMapper;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DB 에 쌓인 환율(현재·확정)을 조회해 내준다. {@code GLOBAL-01} · {@code PRICE-03} */
@Service
@RequiredArgsConstructor
public class FxRateService {

    private static final String USD = "USD";

    /** 이보다 오래된 시세는 "지금 값" 으로 내주지 않는다. */
    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private final FxRateMapper rateMapper;
    private final FxQuoteMapper quoteMapper;
    private final FxProperties props;
    private final AdminSettingService settingService;

    /** 그날 환율을 숫자 하나로 준다. 없으면 null 이다. */
    @Transactional(readOnly = true)
    public BigDecimal rateOf(LocalDate date) {
        return findByDate(date).map(FxRateResponse::rate).orElse(null);
    }

    /** 현재 환율을 준다. 시세가 없거나 낡았으면 확정값으로 물러난다. */
    @Transactional(readOnly = true)
    public Optional<FxRateResponse> latest() {
        FxQuote quote = quoteMapper.findLatest(USD);
        // 낡은 시세는 현재 값으로 내주지 않고 확정값으로 물러난다.
        if (quote != null && quote.quotedAt().isAfter(Instant.now().minus(STALE_AFTER))) {
            return Optional.of(FxRateResponse.ofLive(quote, LocalDate.now()));
        }
        // 낡은 수집 시각은 싣지 않는다 — 화면이 환율 자체를 낡은 것으로 오해한다.
        return findByDate(LocalDate.now());
    }

    /** 그날 확정 환율을 준다. 없으면 저장 없이 직전 값으로 대체해 답한다. */
    @Transactional(readOnly = true)
    public Optional<FxRateResponse> findByDate(LocalDate date) {
        FxRate exact = rateMapper.findByDate(date);
        if (exact != null) {
            return Optional.of(FxRateResponse.ofDaily(exact));
        }
        // 운영 설정이 대체를 꺼 두면 직전 값으로 메우지 않는다.
        if (!settingService.isOn(AdminSettingKey.FX_FALLBACK_ENABLED)) {
            return Optional.empty();
        }
        FxRate previous = rateMapper.findLatestBefore(date, props.getSubstituteLookbackDays());
        if (previous == null) {
            return Optional.empty();
        }
        return Optional.of(FxRateResponse.ofDaily(
                FxRate.substitute(date, previous.usdKrw(), previous.rateDate(),
                                  previous.collectedAt())));
    }
}
