package com.example.mijang.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * XBRL 재무 항목 한 기간치.
 *
 * <p>개발명세서(MVC) · 종목 · dto
 *
 * @param tag       실제로 값을 찾아낸 XBRL 태그. 회사마다 다르므로 어떤 태그를 썼는지 남긴다.
 * @param quarterly 분기 실적이면 true, 누적(반기·연간)이면 false
 * @param filed     제출일. 같은 기간이 정정 제출되면 이 값이 가장 큰 것만 남긴다.
 */
public record FinancialFactResponse(
        String tag,
        String unit,
        LocalDate start,
        LocalDate end,
        BigDecimal value,
        boolean quarterly,
        String form,
        Integer fiscalYear,
        String fiscalPeriod,
        LocalDate filed) {
}
