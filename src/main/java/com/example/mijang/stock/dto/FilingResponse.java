package com.example.mijang.stock.dto;

import java.time.LocalDate;

/**
 * SEC 공시 1건.
 *
 * <p>개발명세서(MVC) · 종목 · dto — 종목 상세의 공시 목록에 쓴다.
 *
 * @param form        공시 종류 (10-K 연간보고서, 10-Q 분기보고서, 8-K 수시공시 등)
 * @param filingDate  제출일
 * @param reportDate  보고 기준일. 8-K 처럼 없는 공시도 있다.
 * @param documentUrl 원문 링크. 영문 원문으로 바로 연결된다.
 */
public record FilingResponse(
        String form,
        LocalDate filingDate,
        LocalDate reportDate,
        String accessionNumber,
        String documentUrl) {
}
