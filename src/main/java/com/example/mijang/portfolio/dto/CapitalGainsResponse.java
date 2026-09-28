package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

/** 연도별 실현손익과 양도소득세 참고 추정 응답이다. */
public record CapitalGainsResponse(
        int year,
        int sellCount,
        BigDecimal gainKrw,
        BigDecimal lossKrw,
        BigDecimal netKrw,
        BigDecimal basicDeductionKrw,
        BigDecimal taxableKrw,
        BigDecimal taxRate,
        BigDecimal estimatedTaxKrw,
        List<Integer> availableYears) {
}
