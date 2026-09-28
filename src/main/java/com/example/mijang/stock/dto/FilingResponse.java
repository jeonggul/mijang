package com.example.mijang.stock.dto;

import java.time.LocalDate;

/** SEC 공시 1건의 응답이다. reportDate 는 8-K 처럼 없는 공시도 있다. */
public record FilingResponse(
        String form,
        LocalDate filingDate,
        LocalDate reportDate,
        String accessionNumber,
        String documentUrl) {
}
