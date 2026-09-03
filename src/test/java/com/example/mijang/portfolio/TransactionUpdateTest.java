package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.domain.Holding;
import com.example.mijang.portfolio.domain.Transaction;
import com.example.mijang.portfolio.dto.TransactionForm;
import com.example.mijang.portfolio.mapper.PortfolioMapper;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.portfolio.service.HoldingService;
import com.example.mijang.portfolio.service.TransactionService;
import com.example.mijang.stock.domain.Stock;
import com.example.mijang.stock.mapper.StockMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 매매 기록 수정.
 *
 * <p>여기서 보려는 것은 셋이다 — <b>남의 기록에 닿지 않는가</b>,
 * <b>종목을 옮기면 양쪽을 다시 계산하는가</b>, <b>보유를 넘기는 수정은 막는가</b>.
 *
 * <p>가운데가 이 시험의 이유다. 옛 종목을 다시 계산하지 않으면 거기에 이 거래가
 * 남아 있는 것처럼 수량이 그대로 있는다. 화면에는 오류가 아니라 <b>그냥 틀린 수량</b>
 * 으로 보여서, 시험이 없으면 아무도 모른다.
 */
class TransactionUpdateTest {

    private static final Long USER = 7L;
    private static final Long TX = 100L;
    private static final Long PF = 1L;

    private TransactionMapper txMapper;
    private StockMapper stockMapper;
    private HoldingService holdingService;
    private TransactionService service;

    @BeforeEach
    void 준비() {
        txMapper = mock(TransactionMapper.class);
        stockMapper = mock(StockMapper.class);
        holdingService = mock(HoldingService.class);
        PortfolioMapper pfMapper = mock(PortfolioMapper.class);
        FxRateService fx = mock(FxRateService.class);
        TradingClock clock = new TradingClock();

        service = new TransactionService(txMapper, pfMapper, stockMapper, fx, holdingService, clock);

        when(stockMapper.findBySymbol(any())).thenAnswer(i ->
                new Stock(1L, i.getArgument(0), "이름", null, "NASDAQ", "STOCK",
                        null, null, true, true, null, null));
        when(holdingService.recalculate(anyLong(), anyLong(), any()))
                .thenReturn(holding("10"));
    }

    private static Holding holding(String qty) {
        return new Holding("AAPL", new BigDecimal(qty), new BigDecimal("100"),
                new BigDecimal("1400"), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static Transaction existing(String symbol) {
        return new Transaction(TX, USER, PF, symbol, "BUY",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1400"),
                BigDecimal.ZERO, LocalDateTime.of(2026, 3, 16, 16, 0),
                LocalDate.of(2026, 3, 16), "처음 사유", null, null);
    }

    private static TransactionForm form(String symbol) {
        TransactionForm f = new TransactionForm();
        f.setSymbol(symbol);
        f.setSide("BUY");
        f.setQuantity(new BigDecimal("12"));
        f.setPrice(new BigDecimal("110"));
        f.setFxRate(new BigDecimal("1380"));
        f.setFee(BigDecimal.ZERO);
        f.setTradedAt(LocalDateTime.of(2026, 3, 16, 16, 0));
        f.setBuyReason("고친 사유");
        return f;
    }

    @Nested
    @DisplayName("소유")
    class 소유 {

        /* 남의 기록 id 를 넣어 고쳐지면 원장이 통째로 뚫린다 */
        @Test
        @DisplayName("남의 기록이면 손대지 않고 404 다")
        void 남의기록() {
            when(txMapper.findById(TX, USER)).thenReturn(null);

            assertThatThrownBy(() -> service.update(USER, TX, form("AAPL")))
                    .isInstanceOf(BusinessException.class);

            verify(txMapper, never()).update(anyLong(), anyLong(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("종목 이동")
    class 종목이동 {

        /* 옛 종목을 안 건드리면 거기에 이 거래가 남아 있는 것처럼 수량이 남는다 */
        @Test
        @DisplayName("종목을 바꾸면 옛 종목과 새 종목을 모두 다시 계산한다")
        void 양쪽재계산() {
            when(txMapper.findById(TX, USER)).thenReturn(existing("AAPL"));

            service.update(USER, TX, form("TSLA"));

            verify(holdingService).recalculate(USER, PF, "AAPL");
            verify(holdingService).recalculate(USER, PF, "TSLA");
        }

        @Test
        @DisplayName("종목이 그대로면 한 번만 다시 계산한다")
        void 그대로면한번() {
            when(txMapper.findById(TX, USER)).thenReturn(existing("AAPL"));

            service.update(USER, TX, form("AAPL"));

            verify(holdingService).recalculate(USER, PF, "AAPL");
            verify(holdingService, never()).recalculate(eq(USER), eq(PF), eq("TSLA"));
        }

        /* 폐지 종목이라도 원래 그 종목이던 기록은 고칠 수 있어야 한다.
           못 고치면 과거의 오타가 영영 원장에 남는다 */
        @Test
        @DisplayName("폐지 종목이어도 원래 그 종목이면 고칠 수 있다")
        void 폐지종목유지() {
            when(txMapper.findById(TX, USER)).thenReturn(existing("DEAD"));
            when(stockMapper.findBySymbol("DEAD")).thenReturn(
                    new Stock(1L, "DEAD", "폐지", null, "NASDAQ", "STOCK",
                            null, null, true, false, "상장폐지", null));

            service.update(USER, TX, form("DEAD"));

            verify(holdingService).recalculate(USER, PF, "DEAD");
        }

        @Test
        @DisplayName("폐지 종목으로 옮기는 것은 막는다")
        void 폐지종목이동() {
            when(txMapper.findById(TX, USER)).thenReturn(existing("AAPL"));
            when(stockMapper.findBySymbol("DEAD")).thenReturn(
                    new Stock(1L, "DEAD", "폐지", null, "NASDAQ", "STOCK",
                            null, null, true, false, "상장폐지", null));

            assertThatThrownBy(() -> service.update(USER, TX, form("DEAD")))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("수량")
    class 수량 {

        /* 매수를 줄이면 뒤따르던 매도가 보유를 넘길 수 있다.
           저장 뒤 전체를 다시 계산해야만 드러난다.

           판단은 HoldingService.recalculate 안에 있다 — 최종 수량이 아니라 훑는 도중의
           최저 수량을 봐야 해서, 계산 결과를 쥐고 있는 그쪽이 판단할 자리다.
           여기서는 그 예외가 위로 전해져 트랜잭션이 되돌아가는지만 본다 */
        @Test
        @DisplayName("재계산이 보유 초과를 잡으면 그대로 던져 되돌린다")
        void 보유초과() {
            when(txMapper.findById(TX, USER)).thenReturn(existing("AAPL"));
            when(holdingService.recalculate(USER, PF, "AAPL"))
                    .thenThrow(new BusinessException(ErrorCode.TX_QUANTITY_EXCEEDS_HOLDING, "quantity"));

            assertThatThrownBy(() -> service.update(USER, TX, form("AAPL")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TX_QUANTITY_EXCEEDS_HOLDING);
        }

        @Test
        @DisplayName("고친 값이 그대로 저장된다")
        void 저장값() {
            when(txMapper.findById(TX, USER)).thenReturn(existing("AAPL"));

            service.update(USER, TX, form("AAPL"));

            verify(txMapper).update(eq(TX), eq(USER), eq("AAPL"), eq("BUY"),
                    eq(new BigDecimal("12")), eq(new BigDecimal("110")),
                    eq(new BigDecimal("1380")), eq(BigDecimal.ZERO),
                    any(), any(), eq("고친 사유"), any(), any());
        }
    }

    @Nested
    @DisplayName("거래일")
    class 거래일 {

        @Test
        @DisplayName("미래 날짜로는 고칠 수 없다")
        void 미래날짜() {
            when(txMapper.findById(TX, USER)).thenReturn(existing("AAPL"));
            TransactionForm f = form("AAPL");
            f.setTradedAt(LocalDateTime.now().plusDays(3));

            assertThatThrownBy(() -> service.update(USER, TX, f))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("한 건 조회도 남의 기록은 404 다")
    void 상세소유() {
        when(txMapper.findById(TX, USER)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(USER, TX))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("한 건 조회는 종목명을 붙여 준다")
    void 상세종목명() {
        when(txMapper.findById(TX, USER)).thenReturn(existing("AAPL"));

        assertThat(service.detail(USER, TX).name()).isEqualTo("이름");
    }
}
