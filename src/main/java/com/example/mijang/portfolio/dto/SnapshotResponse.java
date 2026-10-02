package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 자산 추이 차트의 점 하나가 되는 일별 스냅샷 응답이다. */
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
