/*
 * FxRateService — 화면에 환율을 내주는 곳
 *
 * 이 파일이 하는 일
 *   화면이 환율을 물으면 우리 DB 만 읽어 답한다. 여기서는 벤더를 부르지 않는다.
 *
 *   내주는 값이 두 종류다.
 *     · 지금 환율  — fx_quotes 의 마지막 값. 원화 환산 표시에 쓴다
 *     · 그날 환율  — fx_rates 의 확정값. 손익 계산에 쓴다
 *   손익 화면과 단순 환산 표시가 서로 다른 숫자를 보게 되지만, 손익 숫자끼리
 *   어긋나 보이는 것보다 낫다.
 *
 *   그날 값이 아직 확정 전이면 직전 확정값으로 답하고 대체 사실을 함께 준다.
 *   환율이 없는 것은 오류가 아니다(미장-API명세서 1.6).
 */
package com.example.mijang.fx.service;

import com.example.mijang.config.FxProperties;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.domain.FxRate;
import com.example.mijang.fx.dto.FxRateResponse;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import com.example.mijang.fx.mapper.FxRateMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 환율 조회. {@code GLOBAL-01} · {@code PRICE-03} */
@Service
@RequiredArgsConstructor
public class FxRateService {

    private static final String USD = "USD";

    /**
     * 이보다 오래된 시세는 "지금 값" 으로 내주지 않는다.
     *
     * <p>벤더가 매시 정시에 갱신하고 우리는 매시 02분에 받는다. 두 시간이면 두 번을 연달아
     * 놓친 것이라 정상이 아니다.
     */
    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private final FxRateMapper rateMapper;
    private final FxQuoteMapper quoteMapper;
    private final FxProperties props;

    /**
     * 지금 환율. 원화 환산 표시가 쓴다.
     *
     * <p>시세가 하나도 없으면(수집 전) 확정값으로 물러난다. 그것도 없으면 비어 있음이다.
     */
    @Transactional(readOnly = true)
    public Optional<FxRateResponse> latest() {
        FxQuote quote = quoteMapper.findLatest(USD);
        /* 너무 오래된 값은 "지금 환율" 이 아니다. 수집이 멈춰 있으면 일주일 전 값을
           오늘 값으로 내주게 되고, 화면은 대체 표시도 못 붙인다 — ofLive 는 substituted 가
           false 라서다. 그럴 때는 확정값 쪽으로 물러난다(거기서는 대체 여부가 붙는다) */
        if (quote != null && quote.quotedAt().isAfter(Instant.now().minus(STALE_AFTER))) {
            return Optional.of(FxRateResponse.ofLive(quote, LocalDate.now()));
        }
        return findByDate(LocalDate.now());
    }

    /**
     * 그날 확정 환율. 손익 계산과 매매 기록 입력이 쓴다.
     *
     * <p>그날 행이 없으면 <b>거슬러 올라가 직전 값으로 답한다.</b> 다만 저장은 하지 않는다 —
     * 저장은 확정 배치의 몫이다. 조회가 행을 만들면 아직 오지 않은 날짜를 물었을 때
     * 미래 환율이 생겨 버린다.
     */
    @Transactional(readOnly = true)
    public Optional<FxRateResponse> findByDate(LocalDate date) {
        FxRate exact = rateMapper.findByDate(date);
        if (exact != null) {
            return Optional.of(FxRateResponse.ofDaily(exact));
        }
        FxRate previous = rateMapper.findLatestBefore(date, props.getSubstituteLookbackDays());
        if (previous == null) {
            return Optional.empty();
        }
        return Optional.of(FxRateResponse.ofDaily(
                FxRate.substitute(date, previous.usdKrw(), previous.rateDate())));
    }
}
