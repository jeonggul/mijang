package com.example.mijang.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.admin.dto.AdminLogResponse;
import com.example.mijang.admin.dto.BatchLogResponse;
import com.example.mijang.admin.mapper.AdminLogMapper;
import com.example.mijang.admin.mapper.BatchLogMapper;
import com.example.mijang.admin.service.AdminService;
import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.dto.StockSearchResponse;
import com.example.mijang.stock.mapper.StockMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 관리자 작업.
 *
 * <p>DB 도 스프링도 부르지 않는다. 여기서 보려는 것은 셋이다 —
 * <b>지우지 않고 상태만 바꾸는가</b>(2.6), <b>한 일을 남기는가</b>(2.2),
 * <b>기록이 실패해도 본 작업이 살아남는가</b>(2.3).
 */
class AdminServiceTest {

    private static final Stock APPLE = new Stock(1L, "AAPL", "Apple Inc.", "애플", "NASDAQ",
            "STOCK", "Technology", null, true, true, null, LocalDateTime.of(2026, 8, 1, 0, 0));

    /** 남긴 기록을 모아 두는 가짜 매퍼. 터지게 만들 수도 있다. */
    private static class Logs implements AdminLogMapper {
        final List<String> written = new ArrayList<>();
        boolean explode;

        @Override public int insert(Long adminId, String action, String targetType,
                                    String targetId, String targetLabel, String detail, String result) {
            if (explode) {
                throw new IllegalStateException("로그 저장 실패");
            }
            written.add(String.join("|", action, targetType, targetId,
                    String.valueOf(targetLabel), String.valueOf(detail), result));
            return 1;
        }

        Integer askedLimit;
        String askedQ;
        java.util.List<String> askedTypes;
        java.time.LocalDateTime askedSince;

        @Override public List<AdminLogResponse> findRecent(
                int limit, String q, java.util.List<String> targetTypes,
                java.time.LocalDateTime since) {
            askedLimit = limit; askedQ = q; askedTypes = targetTypes; askedSince = since;
            return List.of();
        }
    }

    /** 상태 변경을 붙잡아 두는 가짜 종목 매퍼. */
    private static class Stocks implements StockMapper {
        Stock found = APPLE;
        String setSymbol;
        Boolean setActive;
        String setReason;
        boolean deleted;

        @Override public Stock findBySymbol(String symbol) { return found; }
        @Override public java.time.LocalDateTime now() { return LocalDateTime.now(); }
        @Override public int deactivateNotSyncedSince(java.time.LocalDateTime threshold) { return 0; }
        @Override public List<String> findActiveSymbols() { return List.of(); }
        @Override public List<StockSearchResponse> searchByPrefix(String q, int limit) { return List.of(); }
        @Override public List<StockSearchResponse> findByFilter(String exchange, String assetClass,
                int offset, int limit) { return List.of(); }
        @Override public int countByFilter(String exchange, String assetClass) { return 0; }
        @Override public int upsert(String symbol, String name, String exchange,
                String assetClass, boolean fractionable) { return 1; }
        @Override public int updateNameKo(String symbol, String nameKo) { return 1; }
        @Override public int countWithNameKo() { return 0; }
        @Override public int updateSecurityType(String symbol, String securityType,
                String isin, String assetClass) { return 1; }
        @Override public int countWithSecurityType() { return 0; }

        @Override public List<StockSearchResponse> findForAdmin(Boolean active, String assetClass,
                                                                String q, int limit) {
            return List.of();
        }
        @Override public int countForAdmin(Boolean active, String assetClass, String q) { return 0; }

        @Override public int setActive(String symbol, boolean active, String reason) {
            setSymbol = symbol; setActive = active; setReason = reason;
            return 1;
        }
    }

    private static class Batches implements BatchLogMapper {
        final List<String> finished = new ArrayList<>();
        Long nextId = 1L;

        @Override public int insertStart(String jobName, LocalDateTime startedAt) { return 1; }
        @Override public Long findLastInsertedId() { return nextId; }
        @Override public int finish(Long id, LocalDateTime finishedAt, int durationMs,
                                    int processedCount, String status, String message) {
            finished.add(status + "|" + processedCount + "|" + String.valueOf(message));
            return 1;
        }
        @Override public List<BatchLogResponse> findLatestPerJob() { return List.of(); }
    }

    private static AdminService service(Stocks stocks, Logs logs) {
        /* 동기화는 이 테스트의 관심사가 아니라 null 로 둔다. 부르는 경로를 밟지 않는다 */
        return new AdminService(stocks, null, logs, new Batches());
    }

    @Nested
    @DisplayName("종목 활성·비활성 — 지우지 않는다")
    class 종목전환 {

        @Test
        @DisplayName("비활성으로 내리면 사유가 함께 저장된다")
        void 비활성화() {
            Stocks stocks = new Stocks();
            Logs logs = new Logs();

            service(stocks, logs).toggleStock(1L, "aapl", false, "상장폐지");

            assertThat(stocks.setSymbol).isEqualTo("AAPL");       // 대문자로 정규화된다
            assertThat(stocks.setActive).isFalse();
            assertThat(stocks.setReason).isEqualTo("상장폐지");
            assertThat(stocks.deleted).isFalse();                  // 지우는 경로는 아예 없다
        }

        /* 다시 올릴 때 사유가 남아 있으면 왜 비활성인지 헷갈린다 */
        @Test
        @DisplayName("복원하면 사유를 비운다")
        void 복원() {
            Stocks stocks = new Stocks();

            service(stocks, new Logs()).toggleStock(1L, "AAPL", true, "무시될 사유");

            assertThat(stocks.setActive).isTrue();
            assertThat(stocks.setReason).isNull();
        }

        @Test
        @DisplayName("없는 종목이면 404 이고 아무 것도 바꾸지 않는다")
        void 없는종목() {
            Stocks stocks = new Stocks();
            stocks.found = null;
            Logs logs = new Logs();

            assertThatThrownBy(() -> service(stocks, logs).toggleStock(1L, "ZZZZ", false, "x"))
                    .isInstanceOf(BusinessException.class);

            assertThat(stocks.setSymbol).isNull();
            /* 하지 않은 일이 기록되면 더 나쁘다(2.3) */
            assertThat(logs.written).isEmpty();
        }
    }

    @Nested
    @DisplayName("한 일을 남긴다")
    class 운영로그 {

        /* 원본이 사라져도 무슨 종목이었는지 남아야 한다(2.2) */
        @Test
        @DisplayName("표시용 이름을 함께 적는다")
        void 라벨() {
            Logs logs = new Logs();

            service(new Stocks(), logs).toggleStock(1L, "AAPL", false, "상장폐지");

            assertThat(logs.written).hasSize(1);
            assertThat(logs.written.get(0)).contains("Apple Inc.").contains("AAPL");
        }

        @Test
        @DisplayName("무엇을 했는지가 상세에 남는다")
        void 상세() {
            Logs logs = new Logs();

            service(new Stocks(), logs).toggleStock(1L, "AAPL", false, "상장폐지");

            assertThat(logs.written.get(0)).contains("비활성화").contains("상장폐지");
        }

        /* 기록이 중요하지만 기록 때문에 운영이 막히면 안 된다(2.3) */
        @Test
        @DisplayName("기록이 실패해도 본 작업은 되돌리지 않는다")
        void 기록실패() {
            Stocks stocks = new Stocks();
            Logs logs = new Logs();
            logs.explode = true;

            service(stocks, logs).toggleStock(1L, "AAPL", false, "상장폐지");

            assertThat(stocks.setActive).isFalse();   // 본 작업은 그대로 끝났다
            assertThat(logs.written).isEmpty();
        }
    }

    @Nested
    @DisplayName("배치 실행 기록")
    class 배치로그 {

        @Test
        @DisplayName("성공하면 건수와 함께 SUCCESS 로 남는다")
        void 성공() {
            Batches batches = new Batches();

            int n = new BatchLogWriter(batches).run("종목 마스터 동기화", () -> 9847);

            assertThat(n).isEqualTo(9847);
            assertThat(batches.finished).containsExactly("SUCCESS|9847|null");
        }

        /* 삼키면 스케줄러가 성공한 줄 안다 */
        @Test
        @DisplayName("실패하면 FAILED 로 남기고 예외를 다시 던진다")
        void 실패() {
            Batches batches = new Batches();

            assertThatThrownBy(() -> new BatchLogWriter(batches).run("일봉 수집", () -> {
                throw new IllegalStateException("벤더 429");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(batches.finished).hasSize(1);
            assertThat(batches.finished.get(0)).startsWith("FAILED|0|").contains("429");
        }

        /* 휴장일에 안 돈 것과 실패해서 못 돈 것은 다르다(2.4) */
        @Test
        @DisplayName("건너뛴 것은 SKIPPED 로 남는다")
        void 건너뜀() {
            Batches batches = new Batches();

            new BatchLogWriter(batches).skip("일별 스냅샷", "거래일이 아니다");

            assertThat(batches.finished).containsExactly("SKIPPED|0|거래일이 아니다");
        }
    }

    /*
     * 운영 로그 필터. 화면의 버튼 하나가 여러 target_type 을 뜻하는 매핑이 서버에 있고,
     * 안 걸린 조건은 SQL 로 내려가지 않아야 한다.
     */
    @Nested
    @DisplayName("운영 로그 필터")
    class 운영로그필터 {

        private AdminService logService(Logs logs) {
            return new AdminService(new Stocks(), null, logs, new Batches());
        }

        @Test
        @DisplayName("조건을 안 주면 아무것도 걸지 않는다")
        void 조건없음() {
            Logs logs = new Logs();
            logService(logs).recentLogs(50, null, null, 0);

            assertThat(logs.askedQ).isNull();
            assertThat(logs.askedTypes).isNull();
            assertThat(logs.askedSince).isNull();
        }

        @Test
        @DisplayName("콘텐츠 한 칸이 게시글·댓글·신고·공지를 묶는다")
        void 콘텐츠묶음() {
            Logs logs = new Logs();
            logService(logs).recentLogs(50, null, "CONTENT", 0);

            assertThat(logs.askedTypes).containsExactlyInAnyOrder("POST", "COMMENT", "REPORT", "NOTICE");
        }

        @Test
        @DisplayName("모르는 종류면 거르지 않는다 — 감사 기록은 빈 화면보다 전체가 안전하다")
        void 모르는종류() {
            Logs logs = new Logs();
            logService(logs).recentLogs(50, null, "WHATEVER", 0);

            assertThat(logs.askedTypes).isNull();
        }

        @Test
        @DisplayName("공백뿐인 검색어는 조건으로 치지 않는다")
        void 빈검색어() {
            Logs logs = new Logs();
            logService(logs).recentLogs(50, "   ", null, 0);

            assertThat(logs.askedQ).isNull();
        }

        @Test
        @DisplayName("기간을 주면 그만큼 거슬러 올라간 시각이 내려간다")
        void 기간() {
            Logs logs = new Logs();
            logService(logs).recentLogs(50, null, null, 7);

            assertThat(logs.askedSince).isNotNull();
            assertThat(logs.askedSince).isBefore(java.time.LocalDateTime.now());
            assertThat(logs.askedSince).isAfter(java.time.LocalDateTime.now().minusDays(8));
        }
    }
}
