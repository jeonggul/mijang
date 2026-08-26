/*
 * CapitalGainsResponse — 양도소득세 참고 계산 결과
 *
 * 이 파일이 하는 일
 *   양도세 화면(SR-009-1)의 두 카드 — 그 해 실현손익과 과세 추정.
 *   참고값이다. 세무 자문이 아니라는 경고를 화면이 반드시 함께 띄운다.
 */
package com.example.mijang.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 양도소득세 참고 계산. 개발명세서(API) GLOBAL-06 · 화면 SR-009-1
 *
 * @param year            계산한 해
 * @param sellCount       그 해 매도 건수
 * @param gainKrw         실현 이익 합(원). 이익 난 매도만
 * @param lossKrw         실현 손실 합(원). 음수로 나간다
 * @param netKrw          통산 실현손익(원)
 * @param basicDeductionKrw 기본공제. 해외주식 연 250만원
 * @param taxableKrw      과세표준. 통산에서 공제를 뺀 값, 0 아래로 내려가지 않는다
 * @param taxRate         세율. 지방소득세 포함 0.22
 * @param estimatedTaxKrw 예상 세액(원)
 * @param availableYears  매도가 있는 해 목록. 최신이 앞이다
 */
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
