package com.example.mijang.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.mijang.config.FxProperties;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.domain.FxRate;
import com.example.mijang.fx.dto.FxRateResponse;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import com.example.mijang.fx.mapper.FxRateMapper;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.support.FixedSettings;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 지금 환율.
 *
 * <p>수집값이 신선하면 그것을, 낡았으면 그날 확정 환율을 내준다.
 * 갈라지는 자리가 두 시간이다.
 */
@ExtendWith(MockitoExtension.class)
class FxRateServiceTest {

    @Mock FxRateMapper rateMapper;
    @Mock FxQuoteMapper quoteMapper;
    @Mock FxProperties properties;
    /* 대체 여부가 시험 결과를 흔들지 않도록 마이그레이션 기본값(켬) 그대로 고정한다.
       @Mock 으로 두면 isOn 이 기본 false 라 대체가 꺼진 채로 도는데,
       실제 운영값과 달라 여기서 통과한 것이 화면에서는 다르게 나온다 */
    @Spy FixedSettings settingService = new FixedSettings();
    @InjectMocks FxRateService service;

    @Test
    @DisplayName("수집이 신선하면 그 값을 지금 환율로 쓴다")
    void 신선한수집() {
        Instant 방금 = Instant.now().minusSeconds(10 * 60);
        when(quoteMapper.findLatest("USD"))
                .thenReturn(new FxQuote("USD", new BigDecimal("1373.21"), 방금));

        FxRateResponse response = service.latest().orElseThrow();

        assertThat(response.rate()).isEqualByComparingTo("1373.21");
        assertThat(response.quotedAt()).isEqualTo(방금);
    }

    /*
     * 낡은 수집 시각을 확정값에 얹어 내보내면, 값은 오늘 것인데 화면의 "마지막 갱신"만
     * 2주 전으로 떠서 환율 자체가 낡은 것으로 읽힌다. 실제로 그렇게 오해한 적이 있다.
     * 시각을 비워 보내야 화면이 확정 기준일을 대신 보여 준다.
     */
    /*
     * 예전에는 낡은 수집 시각을 확정값에 얹어 내보냈다. 값은 오늘 것인데 화면의
     * "마지막 갱신" 만 2주 전으로 떠서 환율 자체가 낡은 것으로 읽혔다.
     * 지금은 그 확정값을 실제로 받아 넣은 시각이 따라간다.
     */
    @Test
    @DisplayName("수집이 낡으면 확정 환율과 그 값을 받아 넣은 시각을 준다")
    void 낡은수집() {
        Instant 두주전 = Instant.now().minusSeconds(14 * 24 * 60 * 60);
        Instant 받아넣은시각 = Instant.parse("2026-08-31T06:00:00Z");
        LocalDate 오늘 = LocalDate.now();
        when(quoteMapper.findLatest("USD"))
                .thenReturn(new FxQuote("USD", new BigDecimal("1416.07"), 두주전));
        when(rateMapper.findByDate(오늘))
                .thenReturn(FxRate.confirmed(오늘, new BigDecimal("1373.21"), 받아넣은시각));

        FxRateResponse response = service.latest().orElseThrow();

        assertThat(response.rate()).isEqualByComparingTo("1373.21");
        assertThat(response.rateDate()).isEqualTo(오늘);
        assertThat(response.quotedAt()).isNull();
        /* 낡은 수집 시각(두주전)이 아니라 그 확정값의 시각이어야 한다 */
        assertThat(response.lastUpdatedAt()).isEqualTo(받아넣은시각);
    }

    /* 복사해 온 값에 복사한 순간을 적으면 오늘 새로 받아 온 값처럼 보인다 */
    @Test
    @DisplayName("대체된 값은 원본을 받아 넣은 시각을 그대로 들고 온다")
    void 대체값의시각() {
        Instant 금요일에받음 = Instant.parse("2026-08-28T06:00:00Z");
        LocalDate 토요일 = LocalDate.of(2026, 8, 29);
        LocalDate 금요일 = LocalDate.of(2026, 8, 28);
        when(rateMapper.findByDate(토요일)).thenReturn(null);
        when(rateMapper.findLatestBefore(org.mockito.ArgumentMatchers.eq(토요일),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(FxRate.confirmed(금요일, new BigDecimal("1373.21"), 금요일에받음));

        FxRateResponse response = service.findByDate(토요일).orElseThrow();

        assertThat(response.substituted()).isTrue();
        assertThat(response.substitutedFrom()).isEqualTo(금요일);
        assertThat(response.lastUpdatedAt()).isEqualTo(금요일에받음);
    }

    @Test
    @DisplayName("수집이 아예 없어도 확정 환율과 그 시각으로 답한다")
    void 수집없음() {
        Instant 받아넣은시각 = Instant.parse("2026-08-31T06:00:00Z");
        LocalDate 오늘 = LocalDate.now();
        when(quoteMapper.findLatest("USD")).thenReturn(null);
        when(rateMapper.findByDate(오늘))
                .thenReturn(FxRate.confirmed(오늘, new BigDecimal("1373.21"), 받아넣은시각));

        FxRateResponse response = service.latest().orElseThrow();

        assertThat(response.rate()).isEqualByComparingTo("1373.21");
        assertThat(response.lastUpdatedAt()).isEqualTo(받아넣은시각);
    }

    @Test
    @DisplayName("확정도 수집도 없으면 비어 있음이다")
    void 아무것도없음() {
        when(quoteMapper.findLatest("USD")).thenReturn(null);
        when(rateMapper.findByDate(LocalDate.now())).thenReturn(null);
        /* 대체가 켜져 있어도 거슬러 올라갈 값이 없으면 비어 있음이다 */
        when(rateMapper.findLatestBefore(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);

        assertThat(service.latest()).isEmpty();
    }
}
