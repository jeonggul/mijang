package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mijang.portfolio.controller.TransactionController;
import com.example.mijang.portfolio.dto.TransactionResponse;
import com.example.mijang.portfolio.service.TransactionService;
import com.example.mijang.security.SessionUser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** CSV 응답의 필터 전달·헤더·스프레드시트 안전성을 고정한다. */
class TransactionControllerTest {

    @Test
    @DisplayName("현재 필터 전체를 한글 CSV 로 내려주고 수식 문자열을 실행하지 않는다")
    void csvExport() {
        TransactionService service = mock(TransactionService.class);
        TransactionResponse row = new TransactionResponse(
                1L, "AAPL", "Apple, \"Inc\"", "SELL",
                new BigDecimal("1.25"), new BigDecimal("200"), new BigDecimal("1400"),
                new BigDecimal("1.50"), LocalDateTime.of(2026, 8, 20, 16, 0),
                LocalDate.of(2026, 8, 20), "  =HYPERLINK(\"https://bad.example\")",
                new BigDecimal("250"), "CONFIDENT", new BigDecimal("-1200.50"));
        when(service.exportRows(7L, "AAPL", "SELL", 2026)).thenReturn(List.of(row));
        TransactionController controller = new TransactionController(service);

        ResponseEntity<byte[]> response = controller.export(
                new SessionUser(7L, "tester", "USER"), "AAPL", "SELL", 2026);

        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment; filename=\"mijang-transactions-")
                .endsWith(".csv\"");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");

        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF\"거래일\"");
        assertThat(csv).contains("\"Apple, \"\"Inc\"\"\"");
        assertThat(csv).contains("\"'  =HYPERLINK(\"\"https://bad.example\"\")\"");
        assertThat(csv).contains(",350000,-1200.5,");
        assertThat(csv.split("\\r\\n")).hasSize(2);
        verify(service).exportRows(7L, "AAPL", "SELL", 2026);
    }
}
