package com.example.mijang.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.admin.mapper.AdminStatsMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 복합 집계 SQL이 실제 MySQL 스키마에서 실행되고 record로 매핑되는지 확인한다. */
@SpringBootTest
class AdminStatsMapperIntegrationTest {

    @Autowired
    private AdminStatsMapper mapper;

    @Test
    @DisplayName("기간 활동 집계가 모든 값을 빠짐없이 매핑한다")
    void 기간집계() {
        var counts = mapper.countActivities(
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.of(2100, 1, 1, 0, 0));

        assertThat(counts).isNotNull();
        assertThat(counts.newUserCount()).isGreaterThanOrEqualTo(0);
        assertThat(counts.activeUserCount()).isGreaterThanOrEqualTo(0);
        assertThat(counts.transactionCount()).isGreaterThanOrEqualTo(counts.judgmentCount());
        assertThat(counts.postCount()).isGreaterThanOrEqualTo(0);
        assertThat(counts.commentCount()).isGreaterThanOrEqualTo(0);
        assertThat(counts.watchCount()).isGreaterThanOrEqualTo(0);
        assertThat(mapper.countNewUsers(
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.of(2100, 1, 1, 0, 0))).isGreaterThanOrEqualTo(0);
        assertThat(mapper.countTransactions(
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.of(2100, 1, 1, 0, 0))).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("인기 종목은 요청 개수를 넘지 않고 종목별 한 행으로 매핑된다")
    void 인기종목() {
        var stocks = mapper.findPopularStocks(5);

        assertThat(stocks).hasSizeLessThanOrEqualTo(5);
        assertThat(stocks).extracting(stock -> stock.symbol()).doesNotHaveDuplicates();
        assertThat(stocks).allSatisfy(stock -> {
            assertThat(stock.watcherCount()).isGreaterThanOrEqualTo(0);
            assertThat(stock.holderCount()).isGreaterThanOrEqualTo(0);
        });
    }
}
