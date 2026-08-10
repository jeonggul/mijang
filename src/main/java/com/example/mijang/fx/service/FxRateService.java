package com.example.mijang.fx.service;

import com.example.mijang.fx.client.EximbankFxClient;
import com.example.mijang.fx.mapper.FxRateMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 환율 수집·조회. 개발명세서(API) FX-001 · 화면 SR-008
 */
@Service
@RequiredArgsConstructor
public class FxRateService {

    private final EximbankFxClient eximbankFxClient;
    private final FxRateMapper fxRateMapper;

    public BigDecimal findByDate(LocalDate baseDate) {
        throw new UnsupportedOperationException("TODO FX-001: 저장된 고시 환율 조회");
    }

    public void collectDaily() {
        throw new UnsupportedOperationException("TODO: 영업일 11시 이후 1회 수집 배치");
    }
}
