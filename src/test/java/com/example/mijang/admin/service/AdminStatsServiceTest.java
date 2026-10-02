package com.example.mijang.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.admin.domain.AdminStatsPeriod;
import com.example.mijang.admin.dto.AdminPopularStockResponse;
import com.example.mijang.admin.dto.AdminStatsCounts;
import com.example.mijang.admin.mapper.AdminStatsMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdminStatsServiceTest {

    @Nested
    @DisplayName("KST 달력 기간")
    class 기간 {

        @Test
        @DisplayName("월간은 이번 달 1일부터 오늘까지이고 이전 달도 같은 일수만 센다")
        void 월간() {
            var window = AdminStatsPeriod.MONTH.window(LocalDate.of(2026, 8, 27));

            assertThat(window.fromDate()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(window.toDate()).isEqualTo(LocalDate.of(2026, 8, 27));
            assertThat(window.previousFromUtc()).isEqualTo(LocalDateTime.of(2026, 6, 30, 15, 0));
            assertThat(window.previousToUtc()).isEqualTo(LocalDateTime.of(2026, 7, 27, 15, 0));
            assertThat(window.fromUtc()).isEqualTo(LocalDateTime.of(2026, 7, 31, 15, 0));
            assertThat(window.toUtc()).isEqualTo(LocalDateTime.of(2026, 8, 27, 15, 0));
        }

        @Test
        @DisplayName("주간은 월요일부터 오늘까지다")
        void 주간() {
            var window = AdminStatsPeriod.WEEK.window(LocalDate.of(2026, 8, 27));

            assertThat(window.fromDate()).isEqualTo(LocalDate.of(2026, 8, 24));
            assertThat(window.toDate()).isEqualTo(LocalDate.of(2026, 8, 27));
            assertThat(window.previousFromUtc()).isEqualTo(LocalDateTime.of(2026, 8, 16, 15, 0));
            assertThat(window.previousToUtc()).isEqualTo(LocalDateTime.of(2026, 8, 20, 15, 0));
        }

        @Test
        @DisplayName("알 수 없는 기간은 월간으로 본다")
        void 기본값() {
            assertThat(AdminStatsPeriod.from("unknown")).isEqualTo(AdminStatsPeriod.MONTH);
        }
    }

    @Test
    @DisplayName("현재·이전 기간을 비교하고 판단 기록률을 계산한다")
    void 통계계산() {
        Stats mapper = new Stats(counts(10, 8, 20, 5, 4, 3, 2), 5);
        mapper.todayTransactions = 3;
        mapper.popular = List.of(new AdminPopularStockResponse(
                "AAPL", "Apple Inc.", "애플", 7, 5));

        var response = new AdminStatsService(mapper)
                .stats("1M", LocalDate.of(2026, 8, 27));

        assertThat(response.period()).isEqualTo("1M");
        assertThat(response.newUserChangeRate()).isEqualByComparingTo("100.0");
        assertThat(response.judgmentRate()).isEqualByComparingTo("25.0");
        assertThat(response.todayTransactionCount()).isEqualTo(3);
        assertThat(response.popularStocks()).extracting(AdminPopularStockResponse::symbol)
                .containsExactly("AAPL");
        assertThat(mapper.ranges).hasSize(1);
        assertThat(mapper.popularLimit).isEqualTo(5);
    }

    @Test
    @DisplayName("비교 분모나 매매 기록이 0이면 거짓 0%를 만들지 않는다")
    void 분모없음() {
        Stats mapper = new Stats(counts(3, 0, 0, 0, 0, 0, 0), 0);

        var response = new AdminStatsService(mapper)
                .stats("1D", LocalDate.of(2026, 8, 27));

        assertThat(response.newUserChangeRate()).isNull();
        assertThat(response.judgmentRate()).isNull();
    }

    private AdminStatsCounts counts(int users, int activeUsers, int transactions, int judgments,
                                    int posts, int comments, int watches) {
        return new AdminStatsCounts(
                users, activeUsers, transactions, judgments, posts, comments, watches);
    }

    private static class Stats implements AdminStatsMapper {
        private final AdminStatsCounts answer;
        private final int previousNewUsers;
        final List<String> ranges = new ArrayList<>();
        List<AdminPopularStockResponse> popular = List.of();
        int popularLimit;
        int todayTransactions;

        Stats(AdminStatsCounts answer, int previousNewUsers) {
            this.answer = answer;
            this.previousNewUsers = previousNewUsers;
        }

        @Override
        public AdminStatsCounts countActivities(LocalDateTime from, LocalDateTime to) {
            ranges.add(from + "|" + to);
            return answer;
        }

        @Override public int countNewUsers(LocalDateTime from, LocalDateTime to) {
            return previousNewUsers;
        }

        @Override public int countTransactions(LocalDateTime from, LocalDateTime to) {
            return todayTransactions;
        }

        @Override
        public List<AdminPopularStockResponse> findPopularStocks(int limit) {
            popularLimit = limit;
            return popular;
        }
    }
}
