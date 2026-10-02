package com.example.mijang.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.stock.client.AlphaVantageEarningsClient;
import com.example.mijang.stock.client.EarningsRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Alpha Vantage EARNINGS_CALENDAR CSV 파싱을 잠근다. 순수 함수라 벤더 없이 검증한다.
 */
class EarningsCsvParseTest {

    private static final String HEADER =
        "symbol,name,reportDate,fiscalDateEnding,estimate,currency,timeOfTheDay";

    @Test
    @DisplayName("정상 행을 EarningsRow 로 파싱한다 — estimate·time 포함")
    void parsesNormalRow() {
        String csv = HEADER + "\n"
            + "AEO,AMERICAN EAGLE,2026-09-09,2026-07-31,0.21,USD,post-market\n";
        List<EarningsRow> rows = AlphaVantageEarningsClient.parseCsv(csv);
        assertThat(rows).hasSize(1);
        EarningsRow r = rows.get(0);
        assertThat(r.symbol()).isEqualTo("AEO");
        assertThat(r.reportDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(r.fiscalDateEnding()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(r.estimateEps()).isEqualByComparingTo(new BigDecimal("0.21"));
        assertThat(r.timeOfDay()).isEqualTo("post-market");
    }

    @Test
    @DisplayName("빈 estimate·time 은 null 로 둔다")
    void handlesBlankOptionalFields() {
        String csv = HEADER + "\n" + "AACG,ATA,2026-09-09,2026-06-30,,USD,\n";
        List<EarningsRow> rows = AlphaVantageEarningsClient.parseCsv(csv);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).estimateEps()).isNull();
        assertThat(rows.get(0).timeOfDay()).isNull();
    }

    @Test
    @DisplayName("유료 안내 JSON·헤더 어긋남·빈 본문은 빈 리스트로 접는다")
    void rejectsNonCsv() {
        assertThat(AlphaVantageEarningsClient.parseCsv(
            "{\"Information\":\"premium endpoint\"}")).isEmpty();
        assertThat(AlphaVantageEarningsClient.parseCsv("")).isEmpty();
        assertThat(AlphaVantageEarningsClient.parseCsv(null)).isEmpty();
        assertThat(AlphaVantageEarningsClient.parseCsv("foo,bar\n1,2\n")).isEmpty();
    }

    @Test
    @DisplayName("reportDate 가 비거나 형식이 깨진 행은 건너뛴다")
    void skipsBadDateRows() {
        String csv = HEADER + "\n"
            + "GOOD,X,2026-10-01,2026-09-30,1.0,USD,pre-market\n"
            + "BAD,Y,,2026-09-30,1.0,USD,\n"
            + "BAD2,Z,not-a-date,2026-09-30,1.0,USD,\n";
        List<EarningsRow> rows = AlphaVantageEarningsClient.parseCsv(csv);
        assertThat(rows).extracting(EarningsRow::symbol).containsExactly("GOOD");
    }
}
