package com.example.mijang.dividend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.dto.HolderAtExDate;
import com.example.mijang.dividend.dto.StockDividendTabResponse;
import com.example.mijang.dividend.mapper.StockDividendMapper;
import com.example.mijang.dividend.service.StockDividendQueryService;
import com.example.mijang.dividend.service.StockDividendSyncService;
import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.dto.HighLow;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 종목 배당 탭 (INFO-06).
 *
 * <p>벤더도 DB 도 부르지 않는다 — 여기서 보려는 것은 요약 계산이다.
 * 연간 합은 최근 1년 · 특별배당 제외, 연속 증배는 완결된 해끼리 비교한다.
 */
class StockDividendQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    private static class Master implements StockDividendMapper {
        List<StockDividend> rows = List.of();
        @Override public int upsert(StockDividend d) { return 1; }
        @Override public List<StockDividend> findBySymbol(String symbol) { return rows; }
        @Override public LocalDateTime findLastSyncedAt(String symbol) { return LocalDateTime.now(); }
        @Override public List<StockDividend> findByExDateBetween(LocalDate f, LocalDate t) { return List.of(); }
        @Override public List<HolderAtExDate> findHoldersAtExDate(String s, LocalDate e) { return List.of(); }
        @Override public List<String> findHeldSymbols() { return List.of(); }
    }

    /** 수집을 건너뛰는 가짜 — 신선하다고 답한다. */
    private static class NoSync extends StockDividendSyncService {
        NoSync(StockDividendMapper mapper) { super(null, mapper); }
        @Override public void ensureFresh(String symbol) { }
    }

    private static class Prices implements DailyPriceMapper {
        BigDecimal close;
        @Override public List<CandleResponse> findByRange(String s, LocalDate f, LocalDate t) { return List.of(); }
        @Override public CandleResponse findLatest(String s) {
            return close == null ? null
                    : new CandleResponse(TODAY, null, null, null, close, 0L);
        }
        @Override public BigDecimal findCloseOn(String s, LocalDate d) { return null; }
        @Override public BigDecimal findPreviousClose(String s) { return null; }
        @Override public HighLow findHighLow(String s, LocalDate f) { return null; }
        @Override public int upsert(String s, LocalDate d, BigDecimal o, BigDecimal h,
                BigDecimal l, BigDecimal c, long v) { return 1; }
        @Override public LocalDate findLastTradeDate(String s) { return null; }
    }

    private static StockDividendQueryService service(Master master, Prices prices) {
        return new StockDividendQueryService(new NoSync(master), master, prices);
    }

    /** 분기 배당 이력을 시작 연도부터 작년까지 만든다. 주당 배당은 해마다 조금씩 오른다. */
    private static List<StockDividend> quarterly(String symbol, int fromYear, String baseRate,
                                                 String yearlyStep) {
        List<StockDividend> rows = new ArrayList<>();
        BigDecimal rate = new BigDecimal(baseRate);
        for (int year = fromYear; year < TODAY.getYear(); year++) {
            for (int q = 0; q < 4; q++) {
                LocalDate ex = LocalDate.of(year, q * 3 + 2, 10);
                rows.add(new StockDividend(symbol, ex, "CASH", rate,
                        ex, ex.plusDays(3), ex.plusDays(3), false, false, null, "id"));
            }
            rate = rate.add(new BigDecimal(yearlyStep));
        }
        rows.sort((a, b) -> b.exDate().compareTo(a.exDate()));
        return rows;
    }

    @Test
    @DisplayName("연간 배당금은 최근 1년 합이고, 특별배당은 빼고 센다")
    void 연간합은특별배당을뺀다() {
        Master master = new Master();
        List<StockDividend> rows = new ArrayList<>();
        // 최근 1년 안: 정규 4건 × $0.25 + 특별 1건 $1.00
        for (int i = 0; i < 4; i++) {
            LocalDate ex = TODAY.minusMonths(i * 3L + 1);
            rows.add(new StockDividend("AAPL", ex, "CASH", new BigDecimal("0.25"),
                    ex, ex.plusDays(3), ex.plusDays(3), false, false, null, "id"));
        }
        LocalDate specialEx = TODAY.minusMonths(2);
        rows.add(new StockDividend("AAPL", specialEx, "CASH", BigDecimal.ONE,
                specialEx, specialEx.plusDays(3), specialEx.plusDays(3), true, false, null, "id"));
        master.rows = rows;

        StockDividendTabResponse out = service(master, new Prices()).tab("AAPL");

        assertThat(out.annualAmountUsd()).isEqualByComparingTo("1.00");   // 0.25 × 4
        assertThat(out.perYear()).isEqualTo(4);
        assertThat(out.yieldPct()).isNull();   // 종가가 없으면 수익률도 없다
    }

    @Test
    @DisplayName("배당수익률 = 최근 1년 합 ÷ 최신 종가")
    void 수익률은종가로나눈다() {
        Master master = new Master();
        LocalDate ex = TODAY.minusMonths(1);
        master.rows = List.of(new StockDividend("AAPL", ex, "CASH", BigDecimal.ONE,
                ex, ex.plusDays(3), ex.plusDays(3), false, false, null, "id"));
        Prices prices = new Prices();
        prices.close = new BigDecimal("200");

        StockDividendTabResponse out = service(master, prices).tab("AAPL");

        assertThat(out.yieldPct()).isEqualByComparingTo("0.50");   // 1 ÷ 200 × 100
    }

    @Test
    @DisplayName("연속 증배는 완결된 해의 연간 합끼리 비교해 센다")
    void 연속증배를센다() {
        Master master = new Master();
        master.rows = quarterly("SCHD", TODAY.getYear() - 6, "0.20", "0.01");

        StockDividendTabResponse out = service(master, new Prices()).tab("SCHD");

        // 6개 해 중 비교 가능한 증가는 5번 — 첫 해 앞은 알 수 없다
        assertThat(out.streakYears()).isEqualTo(5);
    }

    @Test
    @DisplayName("증배가 끊긴 해 앞은 세지 않는다")
    void 끊기면멈춘다() {
        Master master = new Master();
        List<StockDividend> rows = new ArrayList<>(quarterly("SCHD", TODAY.getYear() - 3, "0.20", "0.01"));
        // 4년 전은 3년 전보다 배당이 많았다 — 3년 전에서 증배가 끊긴다
        for (int q = 0; q < 4; q++) {
            LocalDate ex = LocalDate.of(TODAY.getYear() - 4, q * 3 + 2, 10);
            rows.add(new StockDividend("SCHD", ex, "CASH", new BigDecimal("0.50"),
                    ex, ex.plusDays(3), ex.plusDays(3), false, false, null, "id"));
        }
        master.rows = rows;

        StockDividendTabResponse out = service(master, new Prices()).tab("SCHD");

        assertThat(out.streakYears()).isEqualTo(2);
    }

    @Test
    @DisplayName("지급일이 아직 오지 않은 건은 예정 표시가 붙는다")
    void 예정표시() {
        Master master = new Master();
        LocalDate ex = TODAY.minusDays(3);
        master.rows = List.of(new StockDividend("AAPL", ex, "CASH", new BigDecimal("0.25"),
                ex, TODAY.plusDays(2), TODAY.plusDays(2), false, false, null, "id"));

        StockDividendTabResponse out = service(master, new Prices()).tab("AAPL");

        assertThat(out.history()).hasSize(1);
        assertThat(out.history().get(0).upcoming()).isTrue();
    }

    @Test
    @DisplayName("배당이 없는 종목은 빈 값으로 답한다")
    void 무배당은빈값이다() {
        StockDividendTabResponse out = service(new Master(), new Prices()).tab("GOOG");

        assertThat(out.history()).isEmpty();
        assertThat(out.annualAmountUsd()).isEqualByComparingTo("0");
        assertThat(out.streakYears()).isZero();
        assertThat(out.yieldPct()).isNull();
    }
}
