package com.example.mijang.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 매매 기록 입력. 개발명세서(API) PORT-003
 *
 * <p>판단 메모(매수 사유·목표가·심리)는 MVP 에 포함된다. 기획서 부록 B.
 */
@Getter
@Setter
public class TransactionForm {

    @NotBlank
    private String symbol;

    /** BUY / SELL */
    @NotBlank
    private String tradeType;

    @NotNull
    @Positive
    private BigDecimal quantity;

    @NotNull
    @Positive
    private BigDecimal unitPrice;

    /** 매수 시점 적용 환율 */
    @NotNull
    @Positive
    private BigDecimal fxRate;

    @NotNull
    private LocalDateTime tradedAt;

    // ── 판단 메모 ──
    private String reason;
    private BigDecimal targetPrice;
    private String sentiment;
}
