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
    private final AdminSettingService settingService;

    /**
     * 그날 환율을 숫자 하나로. 다른 범위가 계산에 쓴다.
     *
     * <p>{@code portfolio} 가 매매 기록을 저장할 때 사용자가 환율을 안 적으면 이 값으로
     * 채우고, 보유 평가액을 원화로 환산할 때도 쓴다([[미장-portfolio-구현]] 2.7).
     *
     * <p>{@link #findByDate} 와 같은 값을 보되 <b>포장을 벗겨 준다.</b> 부르는 쪽은 대체
     * 여부나 기준일이 아니라 곱할 숫자 하나만 필요하다.
     *
     * @return 없으면 <b>null</b>. 부르는 쪽이 "환율 없이는 저장하지 않는다" 를 판단한다 —
     *         0 을 돌려주면 그 기록만 손익에서 조용히 빠진다
     */
    @Transactional(readOnly = true)
    public BigDecimal rateOf(LocalDate date) {
        return findByDate(date).map(FxRateResponse::rate).orElse(null);
    }

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
        /* 낡은 수집 시각은 붙이지 않는다.
           예전에는 여기서 그 시각을 "마지막 갱신" 으로 실어 보냈는데, 값은 오늘 확정
           환율인데 화면에는 2주 전 날짜가 떴다. 사용자는 환율 자체가 2주 전 것이라고
           읽는다 — 실제로 그렇게 오해한 적이 있다.
           비워서 내보내면 화면이 확정 기준일을 대신 보여 준다. */
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
        /* 운영 설정이 대체를 꺼 두면 직전 영업일 값으로 메우지 않는다. 없는 것은 없다고
           답해야 화면이 "환율 없음" 을 띄우고, 손익 계산도 그 종목을 건너뛴다 */
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
