package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.dto.HoldingResponse;
import com.example.mijang.portfolio.mapper.HoldingMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 보유 현황 조회·재계산. 개발명세서(API) PORT-001 — 평균단가는 이동평균, 평균매수환율은 금액가중평균.
 */
@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingMapper holdingMapper;

    public List<HoldingResponse> findByUser(Long userId) {
        throw new UnsupportedOperationException("TODO PORT-001: 보유 현황 + 현재가 조합");
    }

    public void recalculate(Long userId, String symbol) {
        throw new UnsupportedOperationException("TODO: 매매 기록 전체를 되짚어 평단·평균환율 재계산");
    }
}
