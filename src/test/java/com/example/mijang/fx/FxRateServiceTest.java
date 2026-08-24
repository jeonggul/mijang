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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FxRateServiceTest {

    @Mock FxRateMapper rateMapper;
    @Mock FxQuoteMapper quoteMapper;
    @Mock FxProperties properties;
    @InjectMocks FxRateService service;

    @Test
    void 오래된시세로확정환율을반환해도마지막갱신시각은보존한다() {
        Instant lastUpdatedAt = Instant.now().minusSeconds(3 * 60 * 60);
        LocalDate today = LocalDate.now();
        when(quoteMapper.findLatest("USD"))
                .thenReturn(new FxQuote("USD", new BigDecimal("1416.07"), lastUpdatedAt));
        when(rateMapper.findByDate(today))
                .thenReturn(FxRate.confirmed(today, new BigDecimal("1410.00")));

        FxRateResponse response = service.latest().orElseThrow();

        assertThat(response.rate()).isEqualByComparingTo("1410.00");
        assertThat(response.quotedAt()).isNull();
        assertThat(response.lastUpdatedAt()).isEqualTo(lastUpdatedAt);
    }
}
