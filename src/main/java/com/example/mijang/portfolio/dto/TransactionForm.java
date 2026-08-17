/*
 * TransactionForm — 매매 기록 입력
 *
 * 이 파일이 하는 일
 *   화면이 보내는 거래 한 건의 내용이다.
 *   수량·단가 같은 숫자와 함께 판단 메모를 받는다. 환율은 비워 보낼 수 있고,
 *   그러면 서버가 그날 환율을 대신 채운다.
 */
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

/**
 * 매매 기록 입력. 개발명세서(API) ACCOUNT-01
 *
 * <p>판단 메모(매수 사유·목표가·심리)는 MVP 에 포함된다. 기획서 부록 B.
 */
@Getter
@Setter
public class TransactionForm {

    @NotBlank
    private String symbol;

    /** BUY 또는 SELL. 그 외 값은 여기서 걸러 서비스가 갈래만 보게 한다. */
    @NotBlank
    @Pattern(regexp = "BUY|SELL", message = "BUY 또는 SELL 이어야 합니다")
    private String side;

    /** 소수점 매수를 지원한다(ACCOUNT-03). 0은 거래가 아니므로 막는다. */
    @NotNull
    @Positive
    private BigDecimal quantity;

    /** 체결 단가(USD). */
    @NotNull
    @Positive
    private BigDecimal price;

    /**
     * 적용 환율.
     *
     * <p><b>비워 두면 거래일 환율로 자동으로 채운다</b>(2.7). 그래서 필수가 아니다.
     */
    @Positive
    private BigDecimal fxRate;

    /** 수수료(USD). 0 이 기본이다. */
    @PositiveOrZero
    private BigDecimal fee;

    /** 체결 시각. 거래일은 서버가 이 값에서 ET 기준으로 뽑는다. */
    @NotNull
    private LocalDateTime tradedAt;

    // ── 판단 메모 — 이 서비스의 차별점 ──

    /** 매수 사유. 길이만 막고 내용은 강요하지 않는다. */
    @Size(max = 2000)
    private String buyReason;

    @Positive
    private BigDecimal targetPrice;

    /** CONFIDENT·NEUTRAL·ANXIOUS·FOMO 중 하나. 비워 둘 수 있다. */
    @Pattern(regexp = "CONFIDENT|NEUTRAL|ANXIOUS|FOMO",
             message = "허용되지 않는 값입니다")
    private String sentiment;
}
