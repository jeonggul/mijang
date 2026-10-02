package com.example.mijang.fx;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.config.FxProperties;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.domain.FxRate;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import com.example.mijang.fx.mapper.FxRateMapper;
import com.example.mijang.fx.service.FxConfirmService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 확정 환율 만들기.
 *
 * <p>벤더도 DB 도 부르지 않는다 — 무료 한도가 월 1,000회뿐이라 빌드마다 축내면 안 되고,
 * 여기서 보려는 것은 <b>어느 값을 고르는가</b> 하나뿐이다.
 */
class FxConfirmServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 18);

    /** 저장된 것을 들고 있는 가짜 매퍼. 넣은 값을 그대로 돌려준다. */
    private static class Rates implements FxRateMapper {
        final List<FxRate> saved = new ArrayList<>();
        FxRate byDate;
        FxRate before;

        @Override public FxRate findByDate(LocalDate d) { return byDate; }
        @Override public FxRate findLatestBefore(LocalDate d, int days) { return before; }
        @Override public int insertIgnore(FxRate r) { saved.add(r); return 1; }
    }

    private static class Quotes implements FxQuoteMapper {
        FxQuote last;
        @Override public int insertIgnore(FxQuote q) { return 1; }
        @Override public FxQuote findLatest(String c) { return last; }
        @Override public FxQuote findLastOfDate(String c, LocalDate d) { return last; }
    }

    private FxConfirmService service(Rates r, Quotes q) {
        FxProperties p = new FxProperties();
        p.setSubstituteLookbackDays(10);
        return new FxConfirmService(q, r, p);
    }

    @Test
    @DisplayName("그날 시세가 있으면 그 값으로 확정한다")
    void 시세가있으면() {
        Rates r = new Rates();
        Quotes q = new Quotes();
        q.last = new FxQuote("USD", new BigDecimal("1416.0701"), Instant.now());

        var out = service(r, q).confirm(DAY);

        assertThat(out).isPresent();
        assertThat(out.get().usdKrw()).isEqualByComparingTo("1416.0701");
        assertThat(out.get().substituted()).isFalse();
        assertThat(r.saved).hasSize(1);
    }

    @Test
    @DisplayName("그날 시세가 없으면 직전 값을 복사하고 표시를 남긴다")
    void 시세가없으면() {
        Rates r = new Rates();
        r.before = FxRate.confirmed(DAY.minusDays(3), new BigDecimal("1410.0000"));
        Quotes q = new Quotes();          // last 가 null — 그날 시세 없음

        var out = service(r, q).confirm(DAY);

        assertThat(out).isPresent();
        assertThat(out.get().usdKrw()).isEqualByComparingTo("1410.0000");
        assertThat(out.get().substituted()).isTrue();
        assertThat(out.get().substitutedFrom()).isEqualTo(DAY.minusDays(3));
    }

    @Test
    @DisplayName("이미 확정된 날은 건드리지 않는다")
    void 이미확정() {
        Rates r = new Rates();
        r.byDate = FxRate.confirmed(DAY, new BigDecimal("1400.0000"));
        Quotes q = new Quotes();
        q.last = new FxQuote("USD", new BigDecimal("9999.0000"), Instant.now());

        var out = service(r, q).confirm(DAY);

        assertThat(out).isPresent();
        assertThat(out.get().usdKrw()).isEqualByComparingTo("1400.0000");
        // 한 번 정한 값을 덮으면 그것으로 계산해 둔 손익과 어긋난다
        assertThat(r.saved).isEmpty();
    }

    @Test
    @DisplayName("시세도 직전 값도 없으면 확정하지 않는다")
    void 아무것도없으면() {
        var out = service(new Rates(), new Quotes()).confirm(DAY);
        assertThat(out).isEmpty();
    }
}
