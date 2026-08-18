/*
 * SnapshotResponse — 하루치 스냅샷
 *
 * 이 파일이 하는 일
 *   자산 추이 그래프의 점 하나다. 그날의 평가금액과 손익을 담는다.
 *   지난 값은 다시 계산할 수 없다 — 그날의 환율과 종가가 이미 지나갔기 때문에,
 *   매일 찍어 두지 않으면 영영 알 수 없다.
 */
package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일별 스냅샷 한 건. 개발명세서(API) PROFIT-09
 *
 * <p>자산 추이 차트의 점 하나다.
 */
public record SnapshotResponse(
        LocalDate snapshotDate,
        BigDecimal marketValueKrw,
        BigDecimal costBasisKrw,
        BigDecimal pricePnlKrw,
        BigDecimal fxPnlKrw,
        BigDecimal totalPnlKrw,
        BigDecimal returnRate,
        BigDecimal appliedFxRate,
        boolean fxSubstituted) {
}
