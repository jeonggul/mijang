/*
 * DividendForm — 배당 직접 입력
 *
 * 이 파일이 하는 일
 *   직접 입력 모달(SR-016-2)이 보내는 내용이다. 실수령액은 개인 계좌 정보라
 *   API 로 얻을 수 없으므로 사용자가 적는다(PROFIT-11). 환율은 비워 보낼 수
 *   있고, 그러면 서버가 지급일 환율을 대신 채운다 — 매매 기록과 같은 규칙이다.
 */
package com.example.mijang.dividend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 배당 직접 입력. 개발명세서(API) PROFIT-11 · 화면 SR-016-2
 */
@Getter
@Setter
public class DividendForm {

    @NotBlank
    private String symbol;

    /** 지급일. 배당의 기준 날짜다 — 같은 종목·지급일은 한 건으로 본다(uk). */
    @NotNull
    private LocalDate payDate;

    /** 세후 실수령액(USD). 원천징수 후 실제 입금액이다. */
    @NotNull
    @Positive
    private BigDecimal netAmountUsd;

    /** 적용 환율. 비워 두면 지급일 환율로 자동으로 채운다. */
    @Positive
    private BigDecimal fxRate;
}
