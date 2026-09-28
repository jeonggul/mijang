package com.example.mijang.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 매매 기록 한 건의 입력 폼이다. */
@Getter
@Setter
public class TransactionForm {

    @NotBlank
    private String symbol;

    /** BUY 또는 SELL. */
    @NotBlank
    @Pattern(regexp = "BUY|SELL", message = "BUY 또는 SELL 이어야 합니다")
    private String side;

    /** 수량. 소수점 매수를 지원한다. */
    @NotNull
    @Positive
    private BigDecimal quantity;

    /** 체결 단가(USD). */
    @NotNull
    @Positive
    private BigDecimal price;

    /** 적용 환율. 비워 두면 거래일 환율로 자동으로 채운다. */
    @Positive
    private BigDecimal fxRate;

    /** 수수료(USD). 기본 0. */
    @PositiveOrZero
    private BigDecimal fee;

    /** 체결 시각. 거래일은 서버가 ET 기준으로 뽑는다. */
    @NotNull
    private LocalDateTime tradedAt;

    // 판단 메모

    /** 매수 사유. */
    @Size(max = 2000)
    private String buyReason;

    @Positive
    private BigDecimal targetPrice;

    /** 심리. CONFIDENT·NEUTRAL·ANXIOUS·FOMO 중 하나이며 비워 둘 수 있다. */
    @Pattern(regexp = "CONFIDENT|NEUTRAL|ANXIOUS|FOMO",
             message = "허용되지 않는 값입니다")
    private String sentiment;
}
