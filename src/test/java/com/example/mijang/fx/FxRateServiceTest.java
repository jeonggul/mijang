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
    @Test
    @DisplayName("수집이 낡으면 확정 환율을 주되 낡은 시각은 딸려 보내지 않는다")
    void 낡은수집() {
        Instant 두주전 = Instant.now().minusSeconds(14 * 24 * 60 * 60);
        LocalDate 오늘 = LocalDate.now();
        when(quoteMapper.findLatest("USD"))
                .thenReturn(new FxQuote("USD", new BigDecimal("1416.07"), 두주전));
        when(rateMapper.findByDate(오늘))
                .thenReturn(FxRate.confirmed(오늘, new BigDecimal("1373.21")));

        FxRateResponse response = service.latest().orElseThrow();

        assertThat(response.rate()).isEqualByComparingTo("1373.21");
        assertThat(response.rateDate()).isEqualTo(오늘);
        assertThat(response.quotedAt()).isNull();
        assertThat(response.lastUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("수집이 아예 없어도 확정 환율로 답한다")
    void 수집없음() {
        LocalDate 오늘 = LocalDate.now();
        when(quoteMapper.findLatest("USD")).thenReturn(null);
        when(rateMapper.findByDate(오늘))
                .thenReturn(FxRate.confirmed(오늘, new BigDecimal("1373.21")));

        FxRateResponse response = service.latest().orElseThrow();

        assertThat(response.rate()).isEqualByComparingTo("1373.21");
        assertThat(response.lastUpdatedAt()).isNull();
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
