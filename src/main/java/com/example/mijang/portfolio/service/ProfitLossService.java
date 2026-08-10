package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.dto.ProfitLossResponse;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 손익 요인 분해. 개발명세서(API) PORT-002 — 주가손익과 환차손익을 나눠 계산한다.
 */
@Service
@RequiredArgsConstructor
public class ProfitLossService {

    private final HoldingMapper holdingMapper;

    public ProfitLossResponse breakdown(Long userId) {
        throw new UnsupportedOperationException("TODO PORT-002: 기획서 6장 계산식으로 분해");
    }
}
