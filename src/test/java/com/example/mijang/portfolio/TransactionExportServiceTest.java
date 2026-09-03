package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.time.TradingClock;
import com.example.mijang.fx.service.FxRateService;
import com.example.mijang.portfolio.mapper.PortfolioMapper;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import com.example.mijang.portfolio.service.HoldingService;
import com.example.mijang.portfolio.service.TransactionService;
import com.example.mijang.support.TestLedger;
import com.example.mijang.stock.mapper.StockMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CSV 전체 조회가 화면 필터를 서버 쿼리에 정확히 전달하는지 확인한다. */
class TransactionExportServiceTest {

    @Test
    @DisplayName("종목과 구분을 정규화하고 연도를 반열린 날짜 범위로 조회한다")
    void filters() {
        TransactionMapper mapper = mock(TransactionMapper.class);
        when(mapper.findForExport(7L, "AAPL", "SELL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))).thenReturn(List.of());
        TransactionService service = service(mapper);

        service.exportRows(7L, " aapl ", "sell", 2026);

        verify(mapper).findForExport(7L, "AAPL", "SELL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("허용되지 않은 거래 구분은 전체 조회로 풀지 않고 거절한다")
    void invalidSide() {
        TransactionService service = service(mock(TransactionMapper.class));

        assertThatThrownBy(() -> service.exportRows(7L, null, "DROP", null))
                .isInstanceOf(BusinessException.class);
    }

    private static TransactionService service(TransactionMapper mapper) {
        return new TransactionService(mapper,
                mock(PortfolioMapper.class), mock(StockMapper.class), mock(FxRateService.class),
                mock(HoldingService.class), TestLedger.of(mapper), mock(TradingClock.class));
    }
}
