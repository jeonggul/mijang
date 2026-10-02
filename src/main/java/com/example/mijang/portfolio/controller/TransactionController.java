/*
 * TransactionController — 매매 기록 API
 *
 * 이 파일이 하는 일
 *   거래 입력·목록·삭제 화면이 부르는 것들을 내준다.
 *   전부 로그인이 필요하다. 누구의 기록인지는 요청에서 받지 않고 토큰에서 꺼낸다 —
 *   요청에서 받으면 남의 기록을 보거나 남의 이름으로 저장할 수 있게 된다.
 */
package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.common.response.PageResponse;
import com.example.mijang.portfolio.dto.TransactionForm;
import com.example.mijang.portfolio.dto.TransactionResponse;
import com.example.mijang.portfolio.service.TransactionService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매매 기록 API. 개발명세서(API) ACCOUNT-01·06 · 화면 SR-006·SR-007
 *
 * <p>전부 인증이 필요하다. 사용자 식별자는 요청에서 받지 않고 토큰에서 꺼낸다 —
 * 받으면 남의 기록을 조회하거나 남의 이름으로 저장할 수 있다.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TransactionService transactionService;

    /** 매매 기록 등록. {@code ACCOUNT-01}·{@code ACCOUNT-02} */
    @PostMapping
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @Valid @RequestBody TransactionForm form) {
        return ApiResponse.ok(transactionService.create(me.userId(), form));
    }

    /**
     * 목록. {@code ACCOUNT-06}
     *
     * @param symbol 주면 그 종목만. 생략하면 전체
     */
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @LoginUser SessionUser me,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<TransactionResponse> items = transactionService.list(me.userId(), symbol, page, size);
        long total = transactionService.count(me.userId(), symbol);
        return ApiResponse.ok(PageResponse.of(items, page, size, total));
    }

    /** 현재 필터의 전체 매매 원장을 UTF-8 CSV 로 내려준다. */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @LoginUser SessionUser me,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) Integer year) {
        List<TransactionResponse> rows = transactionService.exportRows(
                me.userId(), symbol, side, year);
        String filename = "mijang-transactions-" + LocalDate.now(KST) + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(csv(rows));
    }

    /**
     * 이 사용자가 거래한 적 있는 종목 티커. 목록 화면 종목 필터가 쓴다({@code ACCOUNT-06}).
     *
     * <p>보유 목록({@code /api/portfolio/holdings})으로 대신할 수 없다 — 그쪽은 수량이 남은
     * 종목만 준다. 전량 매도한 종목의 기록도 찾을 수 있어야 한다.
     */
    @GetMapping("/symbols")
    public ApiResponse<List<String>> symbols(@LoginUser SessionUser me) {
        return ApiResponse.ok(transactionService.tradedSymbols(me.userId()));
    }

    /** 한 건 조회. 수정 화면이 값을 채울 때 쓴다. */
    @GetMapping("/{txId}")
    public ApiResponse<TransactionResponse> detail(@LoginUser SessionUser me,
                                                   @PathVariable Long txId) {
        return ApiResponse.ok(transactionService.detail(me.userId(), txId));
    }

    /** 수정. {@code ACCOUNT-04} 고치면 보유 현황이 다시 계산된다. */
    @PatchMapping("/{txId}")
    public ApiResponse<Void> update(@LoginUser SessionUser me,
                                    @PathVariable Long txId,
                                    @Valid @RequestBody TransactionForm form) {
        transactionService.update(me.userId(), txId, form);
        return ApiResponse.ok(null);
    }

    /** 삭제. 지우지 않고 표시만 한다(2.9). */
    @DeleteMapping("/{txId}")
    public ApiResponse<Void> delete(@LoginUser SessionUser me, @PathVariable Long txId) {
        transactionService.delete(me.userId(), txId);
        return ApiResponse.ok(null);
    }

    /** 엑셀에서 한글이 깨지지 않게 UTF-8 BOM 을 붙인다. */
    private static byte[] csv(List<TransactionResponse> rows) {
        StringBuilder out = new StringBuilder("\uFEFF");
        appendRow(out,
                text("거래일"), text("체결시각"), text("종목"), text("종목명"), text("구분"),
                text("수량"), text("체결단가(USD)"), text("적용환율(KRW/USD)"),
                text("수수료(USD)"), text("체결금액(KRW)"), text("실현손익(KRW)"),
                text("매수사유"), text("목표가(USD)"), text("심리"));
        for (TransactionResponse row : rows) {
            appendRow(out,
                    text(row.tradeDate()), text(row.tradedAt()), text(row.symbol()), text(row.name()),
                    text("BUY".equals(row.side()) ? "매수" : "매도"),
                    number(row.quantity()), number(row.price()), number(row.fxRate()),
                    number(row.fee()), number(amountKrw(row)), number(row.realizedPnlKrw()),
                    text(row.buyReason()), number(row.targetPrice()), text(row.sentiment()));
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder out, String... cells) {
        out.append(String.join(",", cells)).append("\r\n");
    }

    /** 사용자·벤더 문자열을 따옴표로 감싸고 스프레드시트 수식 실행을 막는다. */
    private static String text(Object raw) {
        String value = raw == null ? "" : raw.toString();
        int first = 0;
        while (first < value.length() && Character.isWhitespace(value.charAt(first))) {
            first++;
        }
        if (first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal amountKrw(TransactionResponse row) {
        if (row.quantity() == null || row.price() == null || row.fxRate() == null) {
            return null;
        }
        return row.quantity().multiply(row.price()).multiply(row.fxRate())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
