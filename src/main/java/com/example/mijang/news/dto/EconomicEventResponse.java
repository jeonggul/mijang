package com.example.mijang.news.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** 경제 지표 발표 일정 한 건을 담는다. 시각은 미 동부시각(ET) 그대로 내려주고 변환은 화면이 한다. INFO-07. */
public record EconomicEventResponse(
        LocalDate date,
        LocalTime timeEt,
        String name,
        String source,
        String importance,
        String note) {

    public static final String IMPORTANCE_HIGH = "HIGH";
    public static final String IMPORTANCE_NORMAL = "NORMAL";
}
