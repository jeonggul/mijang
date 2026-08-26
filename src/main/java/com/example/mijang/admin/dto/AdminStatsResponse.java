package com.example.mijang.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 관리자 통계 탭 응답. 방문 추적 없이 DB에서 증명되는 값만 담는다. */
public record AdminStatsResponse(
        String period,
        LocalDate fromDate,
        LocalDate toDate,
        AdminStatsCounts counts,
        int previousNewUserCount,
        BigDecimal newUserChangeRate,
        BigDecimal judgmentRate,
        int todayTransactionCount,
        List<AdminPopularStockResponse> popularStocks) {
}
