package com.example.mijang.dividend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.config.FxProperties;
import com.example.mijang.dividend.domain.Dividend;
import com.example.mijang.dividend.domain.StockDividend;
import com.example.mijang.dividend.dto.DividendResponse;
import com.example.mijang.dividend.dto.HolderAtExDate;
import com.example.mijang.dividend.mapper.DividendMapper;
import com.example.mijang.dividend.mapper.StockDividendMapper;
import com.example.mijang.dividend.service.DividendEstimateService;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.domain.FxRate;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import com.example.mijang.fx.mapper.FxRateMapper;
import com.example.mijang.fx.service.FxRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 예상 배당 생성 (PROFIT-12).
 *
 * <p>DB 를 부르지 않는다 — 여기서 보려는 것은 산출식(주당 × 수량 × 0.85)과
 * "이미 있으면 건드리지 않는다" 는 멱등 규칙이다.
 */
class DividendEstimateServiceTest {

    private static final LocalDate ASOF = LocalDate.of(2026, 8, 26);
    private static final LocalDate EX = LocalDate.of(2026, 8, 10);
    private static final LocalDate PAY = LocalDate.of(2026, 8, 13);

    private static class Master implements StockDividendMapper {
        List<StockDividend> events = List.of();
        Map<String, List<HolderAtExDate>> holders = Map.of();

        @Override public int upsert(StockDividend d) { return 1; }
        @Override public List<StockDividend> findBySymbol(String symbol) { return List.of(); }
        @Override public LocalDateTime findLastSyncedAt(String symbol) { return null; }
        @Override public List<StockDividend> findByExDateBetween(LocalDate f, LocalDate t) { return events; }
        @Override public List<HolderAtExDate> findHoldersAtExDate(String symbol, LocalDate exDate) {
            return holders.getOrDefault(symbol, List.of());
        }
        @Override public List<String> findHeldSymbols() { return List.of(); }
    }

    /** 넣은 것을 들고 있는 가짜. duplicate 면 INSERT IGNORE 처럼 0을 답한다. */
    private static class Personal implements DividendMapper {
        final List<Dividend> saved = new ArrayList<>();
        boolean duplicate;

        @Override public int insert(Dividend d) { saved.add(d); return 1; }
        @Override public int insertIgnore(Dividend d) {
            if (duplicate) return 0;
            saved.add(d); return 1;
        }
        @Override public Long findLastInsertedId() { return 1L; }
        @Override public List<DividendResponse> findByUser(Long userId) { return List.of(); }
        @Override public Dividend findById(Long id, Long userId) { return null; }
        @Override public int confirm(Long id, Long u, BigDecimal n, BigDecimal f, BigDecimal k, LocalDate p) { return 1; }
        @Override public int update(Long id, Long u, BigDecimal n, BigDecimal f, BigDecimal k, LocalDate p) { return 1; }
        @Override public int softDelete(Long id, Long userId) { return 1; }
        @Override public BigDecimal sumConfirmedKrwBetween(Long u, LocalDate f, LocalDate t) { return null; }
        @Override public long countEstimated(Long u) { return 0; }
        @Override public BigDecimal sumEstimatedKrw(Long u) { return null; }
        @Override public DividendResponse findNextUpcoming(Long u, LocalDate today) { return null; }
    }

    private static class Rates implements FxRateMapper {
        FxRate byDate;
        @Override public FxRate findByDate(LocalDate d) { return byDate; }
        @Override public FxRate findLatestBefore(LocalDate d, int days) { return null; }
        @Override public int insertIgnore(FxRate r) { return 1; }
    }

    private static class Quotes implements FxQuoteMapper {
        @Override public int insertIgnore(FxQuote q) { return 1; }
        @Override public FxQuote findLatest(String c) { return null; }
        @Override public FxQuote findLastOfDate(String c, LocalDate d) { return null; }
    }

    private static DividendEstimateService service(Master master, Personal personal, Rates rates) {
        FxProperties props = new FxProperties();
        props.setSubstituteLookbackDays(10);
        return new DividendEstimateService(master, personal,
                new FxRateService(rates, new Quotes(), props));
    }

    private static StockDividend event(String symbol, String rate) {
        return new StockDividend(symbol, EX, "CASH", new BigDecimal(rate),
                EX, PAY, PAY, false, false, null, "uuid");
    }

    @Test
    @DisplayName("배당락일 보유자마다 예상 배당이 생긴다 — 주당 × 수량 × (1−0.15)")
    void 보유자마다예상이생긴다() {
        Master master = new Master();
        master.events = List.of(event("SCHD", "0.2645"));
        master.holders = Map.of("SCHD", List.of(
                new HolderAtExDate(1L, 1L, new BigDecimal("22")),
                new HolderAtExDate(2L, 2L, new BigDecimal("10"))));
        Personal personal = new Personal();
        Rates rates = new Rates();
        rates.byDate = FxRate.confirmed(PAY, new BigDecimal("1400"));

        int created = service(master, personal, rates).produce(ASOF);

        assertThat(created).isEqualTo(2);
        Dividend first = personal.saved.get(0);
        assertThat(first.status()).isEqualTo("ESTIMATED");
        assertThat(first.source()).isEqualTo("VENDOR");
        assertThat(first.quantityAtExDate()).isEqualByComparingTo("22");
        assertThat(first.grossAmountUsd()).isEqualByComparingTo("5.8190");
        assertThat(first.netAmountUsd()).isEqualByComparingTo("4.9462");   // × 0.85
        assertThat(first.netAmountKrw()).isEqualByComparingTo("6924.68");  // × 1400
        assertThat(first.payDate()).isEqualTo(PAY);
    }

    @Test
    @DisplayName("이미 있으면 건드리지 않는다 — 직접 입력이 이긴다")
    void 이미있으면넘어간다() {
        Master master = new Master();
        master.events = List.of(event("SCHD", "0.2645"));
        master.holders = Map.of("SCHD", List.of(new HolderAtExDate(1L, 1L, BigDecimal.TEN)));
        Personal personal = new Personal();
        personal.duplicate = true;
        Rates rates = new Rates();
        rates.byDate = FxRate.confirmed(PAY, new BigDecimal("1400"));

        int created = service(master, personal, rates).produce(ASOF);

        assertThat(created).isZero();
        assertThat(personal.saved).isEmpty();
    }

    @Test
    @DisplayName("환율이 어디에도 없으면 그 이벤트는 건너뛴다 — 0으로 채우지 않는다")
    void 환율이없으면건너뛴다() {
        Master master = new Master();
        master.events = List.of(event("SCHD", "0.2645"));
        master.holders = Map.of("SCHD", List.of(new HolderAtExDate(1L, 1L, BigDecimal.TEN)));
        Personal personal = new Personal();

        int created = service(master, personal, new Rates()).produce(ASOF);

        assertThat(created).isZero();
    }

    @Test
    @DisplayName("지급일이 비어 있으면 배당락일을 지급일로 적는다")
    void 지급일이없으면배당락일이다() {
        Master master = new Master();
        master.events = List.of(new StockDividend("SCHD", EX, "CASH",
                new BigDecimal("0.25"), EX, null, null, false, false, null, "uuid"));
        master.holders = Map.of("SCHD", List.of(new HolderAtExDate(1L, 1L, BigDecimal.TEN)));
        Personal personal = new Personal();
        Rates rates = new Rates();
        rates.byDate = FxRate.confirmed(EX, new BigDecimal("1400"));

        service(master, personal, rates).produce(ASOF);

        assertThat(personal.saved.get(0).payDate()).isEqualTo(EX);
    }

    @Test
    @DisplayName("주당 배당이 0이면 만들지 않는다")
    void 영배당은만들지않는다() {
        Master master = new Master();
        master.events = List.of(event("SCHD", "0"));
        master.holders = Map.of("SCHD", List.of(new HolderAtExDate(1L, 1L, BigDecimal.TEN)));
        Personal personal = new Personal();
        Rates rates = new Rates();
        rates.byDate = FxRate.confirmed(PAY, new BigDecimal("1400"));

        assertThat(service(master, personal, rates).produce(ASOF)).isZero();
    }
}
