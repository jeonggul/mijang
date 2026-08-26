package com.example.mijang.admin.service;

import com.example.mijang.admin.domain.AdminStatsPeriod;
import com.example.mijang.admin.dto.AdminStatsCounts;
import com.example.mijang.admin.dto.AdminStatsResponse;
import com.example.mijang.admin.mapper.AdminStatsMapper;
import com.example.mijang.common.time.TradingClock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 방문 추적 없이 현재 DB에서 계산 가능한 관리자 통계를 만든다. */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final AdminStatsMapper mapper;

    @Transactional(readOnly = true)
    public AdminStatsResponse stats(String periodCode) {
        return stats(periodCode, LocalDate.now(TradingClock.SERVICE_ZONE));
    }

    /** 날짜를 고정해 기간 경계를 검증할 수 있는 내부 진입점. */
    AdminStatsResponse stats(String periodCode, LocalDate today) {
        AdminStatsPeriod.Window window = AdminStatsPeriod.from(periodCode).window(today);
        AdminStatsCounts current = mapper.countActivities(window.fromUtc(), window.toUtc());
        int previousNewUsers = mapper.countNewUsers(
                window.previousFromUtc(), window.previousToUtc());
        AdminStatsPeriod.Window todayWindow = AdminStatsPeriod.DAY.window(today);
        int todayTransactions = window.code().equals(AdminStatsPeriod.DAY.code())
                ? current.transactionCount()
                : mapper.countTransactions(todayWindow.fromUtc(), todayWindow.toUtc());

        return new AdminStatsResponse(
                window.code(),
                window.fromDate(),
                window.toDate(),
                current,
                previousNewUsers,
                changeRate(current.newUserCount(), previousNewUsers),
                ratio(current.judgmentCount(), current.transactionCount()),
                todayTransactions,
                mapper.findPopularStocks(5));
    }

    private BigDecimal changeRate(int current, int previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(int part, int total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
