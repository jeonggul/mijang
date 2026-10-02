package com.example.mijang.dividend;

import com.example.mijang.support.FixedSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.FxProperties;
import com.example.mijang.dividend.domain.Dividend;
import com.example.mijang.dividend.dto.DividendConfirmForm;
import com.example.mijang.dividend.dto.DividendForm;
import com.example.mijang.dividend.dto.DividendResponse;
import com.example.mijang.dividend.dto.DividendSummaryResponse;
import com.example.mijang.dividend.mapper.DividendMapper;
import com.example.mijang.dividend.service.DividendService;
import com.example.mijang.fx.domain.FxQuote;
import com.example.mijang.fx.domain.FxRate;
import com.example.mijang.fx.mapper.FxQuoteMapper;
import com.example.mijang.fx.mapper.FxRateMapper;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.mapper.PortfolioMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 배당 기록의 규칙.
 *
 * <p>DB 를 부르지 않는다 — 여기서 보려는 것은 직접 입력이 바로 확정이 되는지,
 * 환율을 비우면 지급일 환율로 채우는지, 확정이 두 번 되지 않는지다.
 */
class DividendServiceTest {

    private static final Long USER = 1L;
    private static final LocalDate PAY = LocalDate.of(2026, 8, 14);

    /** 넣은 것을 들고 있는 가짜 매퍼. */
    private static class Dividends implements DividendMapper {
        final List<Dividend> saved = new ArrayList<>();
        Dividend byId;
        boolean duplicate;
        int confirmChanged = 1;
        BigDecimal confirmedSum;
        long estimatedCount;
        BigDecimal estimatedSum;

        @Override public int insert(Dividend d) {
            if (duplicate) throw new DuplicateKeyException("uk_dividends_pf_symbol_pay");
            saved.add(d); return 1;
        }
        @Override public int insertIgnore(Dividend d) {
            if (duplicate) return 0;
            saved.add(d); return 1;
        }
        @Override public Long findLastInsertedId() { return 17L; }
        @Override public List<DividendResponse> findByUser(Long userId) { return List.of(); }
        @Override public Dividend findById(Long id, Long userId) { return byId; }
        @Override public int confirm(Long id, Long userId, BigDecimal net, BigDecimal fx,
                BigDecimal krw, LocalDate payDate) { return confirmChanged; }
        @Override public int update(Long id, Long userId, BigDecimal net, BigDecimal fx,
                BigDecimal krw, LocalDate payDate) { return 1; }
        @Override public int softDelete(Long id, Long userId) { return byId == null ? 0 : 1; }
        @Override public BigDecimal sumConfirmedKrwBetween(Long u, LocalDate f, LocalDate t) { return confirmedSum; }
        @Override public long countEstimated(Long u) { return estimatedCount; }
        @Override public BigDecimal sumEstimatedKrw(Long u) { return estimatedSum; }
        @Override public DividendResponse findNextUpcoming(Long u, LocalDate today) { return null; }
    }

    private static class Portfolios implements PortfolioMapper {
        @Override public Long findDefaultId(Long userId) { return 1L; }
        @Override public int insertDefault(Long userId) { return 1; }
        @Override public Long findLastInsertedId() { return 1L; }
    }

    /** 지급일 환율만 답하는 가짜. */
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

    private DividendService service(Dividends dividends, Rates rates) {
        FxProperties props = new FxProperties();
        props.setSubstituteLookbackDays(10);
        return new DividendService(dividends,
                new Portfolios(), new FxRateService(rates, new Quotes(), props, new FixedSettings()));
    }

    private static Dividend estimated() {
        return new Dividend(17L, USER, 1L, "SCHD", PAY.minusDays(2), PAY,
                new BigDecimal("0.2645"), new BigDecimal("22"), new BigDecimal("5.82"),
                new BigDecimal("4.94"), new BigDecimal("0.15"), new BigDecimal("1400"),
                new BigDecimal("6916.00"), "ESTIMATED", "VENDOR", null);
    }

    @Test
    @DisplayName("직접 입력한 배당은 바로 확정 상태다 — 실수령액을 적는 것 자체가 확정이다")
    void 직접입력은바로확정이다() {
        Dividends dividends = new Dividends();
        DividendForm form = new DividendForm();
        form.setSymbol("schd");
        form.setPayDate(PAY);
        form.setNetAmountUsd(new BigDecimal("4.94"));
        form.setFxRate(new BigDecimal("1402.10"));

        DividendResponse out = service(dividends, new Rates()).create(USER, form);

        assertThat(out.status()).isEqualTo("CONFIRMED");
        assertThat(out.symbol()).isEqualTo("SCHD");
        assertThat(out.netAmountKrw()).isEqualByComparingTo("6926.37");
        assertThat(dividends.saved).hasSize(1);
        assertThat(dividends.saved.get(0).source()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("환율을 비우면 지급일 환율로 채운다")
    void 환율을비우면지급일환율이다() {
        Dividends dividends = new Dividends();
        Rates rates = new Rates();
        rates.byDate = FxRate.confirmed(PAY, new BigDecimal("1400.0000"));
        DividendForm form = new DividendForm();
        form.setSymbol("SCHD");
        form.setPayDate(PAY);
        form.setNetAmountUsd(new BigDecimal("10"));

        DividendResponse out = service(dividends, rates).create(USER, form);

        assertThat(out.fxRate()).isEqualByComparingTo("1400");
        assertThat(out.netAmountKrw()).isEqualByComparingTo("14000.00");
    }

    @Test
    @DisplayName("환율이 어디에도 없으면 저장하지 않는다 — 0으로 채우면 집계에서 조용히 빠진다")
    void 환율이없으면거절한다() {
        DividendForm form = new DividendForm();
        form.setSymbol("SCHD");
        form.setPayDate(PAY);
        form.setNetAmountUsd(new BigDecimal("10"));

        assertThatThrownBy(() -> service(new Dividends(), new Rates()).create(USER, form))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.FX_RATE_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 종목·지급일이 이미 있으면 409다")
    void 중복이면거절한다() {
        Dividends dividends = new Dividends();
        dividends.duplicate = true;
        DividendForm form = new DividendForm();
        form.setSymbol("SCHD");
        form.setPayDate(PAY);
        form.setNetAmountUsd(new BigDecimal("10"));
        form.setFxRate(new BigDecimal("1400"));

        assertThatThrownBy(() -> service(dividends, new Rates()).create(USER, form))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DIVIDEND_DUPLICATED);
    }

    @Test
    @DisplayName("예상 배당을 실제 입금액으로 확정한다")
    void 예상을확정한다() {
        Dividends dividends = new Dividends();
        dividends.byId = estimated();
        DividendConfirmForm form = new DividendConfirmForm();
        form.setNetAmountUsd(new BigDecimal("4.94"));
        form.setFxRate(new BigDecimal("1402.10"));

        DividendResponse out = service(dividends, new Rates()).confirm(USER, 17L, form);

        assertThat(out.status()).isEqualTo("CONFIRMED");
        assertThat(out.netAmountKrw()).isEqualByComparingTo("6926.37");
    }

    @Test
    @DisplayName("이미 확정된 배당은 다시 확정할 수 없다 — 명세서 1.6")
    void 두번확정은안된다() {
        Dividends dividends = new Dividends();
        dividends.byId = new Dividend(17L, USER, 1L, "SCHD", null, PAY,
                null, null, null, new BigDecimal("4.94"), new BigDecimal("0.15"),
                new BigDecimal("1400"), new BigDecimal("6916.00"),
                "CONFIRMED", "MANUAL", PAY.atStartOfDay());
        DividendConfirmForm form = new DividendConfirmForm();
        form.setNetAmountUsd(new BigDecimal("5.00"));

        assertThatThrownBy(() -> service(dividends, new Rates()).confirm(USER, 17L, form))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DIVIDEND_ALREADY_CONFIRMED);
    }

    @Test
    @DisplayName("조회와 갱신 사이에 남이 먼저 확정했어도 409다 — 조건부 갱신이 지킨다")
    void 동시확정은한쪽만성공한다() {
        Dividends dividends = new Dividends();
        dividends.byId = estimated();
        dividends.confirmChanged = 0;   // 갱신 시점에는 이미 확정된 뒤다
        DividendConfirmForm form = new DividendConfirmForm();
        form.setNetAmountUsd(new BigDecimal("4.94"));
        form.setFxRate(new BigDecimal("1400"));

        assertThatThrownBy(() -> service(dividends, new Rates()).confirm(USER, 17L, form))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DIVIDEND_ALREADY_CONFIRMED);
    }

    @Test
    @DisplayName("없는 기록을 지우면 404다")
    void 없는기록은지울수없다() {
        assertThatThrownBy(() -> service(new Dividends(), new Rates()).delete(USER, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DIVIDEND_NOT_FOUND);
    }

    @Test
    @DisplayName("요약 — 합이 없으면 null 이 아니라 0으로 답한다")
    void 요약은빈값을0으로답한다() {
        DividendSummaryResponse out = service(new Dividends(), new Rates()).summary(USER);

        assertThat(out.yearConfirmedKrw()).isEqualByComparingTo("0");
        assertThat(out.pendingCount()).isZero();
        assertThat(out.pendingKrw()).isEqualByComparingTo("0");
        assertThat(out.nextPayDate()).isNull();
    }
}
