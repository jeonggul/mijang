package com.example.mijang.dividend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.dividend.domain.StockEarnings;
import com.example.mijang.dividend.mapper.StockEarningsMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** stock_earnings upsert·조회가 실제 스키마에서 도는지 잠근다. @Transactional 로 롤백. */
@SpringBootTest
@Transactional
class StockEarningsMapperIntegrationTest {

    @Autowired StockEarningsMapper mapper;

    private StockEarnings row(String sym, LocalDate d, String eps) {
        return new StockEarnings(sym, d, d.minusMonths(1),
                eps == null ? null : new BigDecimal(eps), "post-market");
    }

    @Test
    @DisplayName("upsert 후 기간 조회로 다시 읽힌다")
    void upsertThenRange() {
        LocalDate d = LocalDate.of(2999, 1, 15);   // 미래 고정 — 실데이터와 안 겹침
        mapper.upsert(row("ZZTOP", d, "1.23"));
        var found = mapper.findByReportDateBetween(d.minusDays(1), d.plusDays(1));
        assertThat(found).anyMatch(e -> e.symbol().equals("ZZTOP")
                && e.estimateEps().compareTo(new BigDecimal("1.23")) == 0);
    }

    @Test
    @DisplayName("같은 (symbol, report_date) 재upsert 는 estimate 를 갱신하고 행을 늘리지 않는다")
    void upsertUpdatesInPlace() {
        LocalDate d = LocalDate.of(2999, 2, 20);
        mapper.upsert(row("ZZUP", d, "1.00"));
        mapper.upsert(row("ZZUP", d, "2.00"));
        var found = mapper.findByReportDateBetween(d, d);
        assertThat(found).filteredOn(e -> e.symbol().equals("ZZUP")).hasSize(1);
        assertThat(found).filteredOn(e -> e.symbol().equals("ZZUP"))
                .first().extracting(StockEarnings::estimateEps)
                .isEqualTo(new BigDecimal("2.000000"));
    }

    @Test
    @DisplayName("findNextBySymbol 은 기준일 이후 첫 발표를 준다")
    void findNext() {
        LocalDate base = LocalDate.of(2999, 3, 1);
        mapper.upsert(row("ZZNX", base.plusDays(10), "1"));
        mapper.upsert(row("ZZNX", base.plusDays(40), "1"));
        var next = mapper.findNextBySymbol("ZZNX", base);
        assertThat(next).isNotNull();
        assertThat(next.reportDate()).isEqualTo(base.plusDays(10));
    }
}
