/*
 * DividendConfirmForm — 예상 배당 확정
 *
 * 이 파일이 하는 일
 *   확정 모달(SR-016-1)이 보내는 내용이다. 벤더가 만든 예상 금액을
 *   증권사 앱에서 확인한 실제 입금액으로 바꾼다. 확정하는 순간부터
 *   손익 집계에 포함된다.
 */
package com.example.mijang.dividend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 배당 확정 입력. 개발명세서(API) PROFIT-12 · 화면 SR-016-1
 */
@Getter
@Setter
public class DividendConfirmForm {

    /** 실제 입금액(USD). */
    @NotNull
    @Positive
    private BigDecimal netAmountUsd;

    /** 증권사가 적용한 환율. 비워 두면 지급일 환율을 쓴다. */
    @Positive
    private BigDecimal fxRate;

    /** 실제 지급일. 예상과 다르면 고쳐 보낸다. 비우면 그대로 둔다. */
    private LocalDate payDate;
}
