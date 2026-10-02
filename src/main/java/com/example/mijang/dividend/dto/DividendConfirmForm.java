package com.example.mijang.dividend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 예상 배당 확정 모달(SR-016-1)의 입력을 담는다. PROFIT-12. */
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
