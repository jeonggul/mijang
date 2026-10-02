package com.example.mijang.news.dto;

import java.time.LocalDate;

/** 캘린더 이벤트 한 건을 담는다. type 은 ECONOMIC·EARNINGS·DIVIDEND 이며 거시엔 symbol 이 null 이다. */
public record CalendarEventResponse(LocalDate date, String type, String symbol,
                                    String title, String note) {

    public static final String TYPE_ECONOMIC = "ECONOMIC";
    public static final String TYPE_EARNINGS = "EARNINGS";
    public static final String TYPE_DIVIDEND = "DIVIDEND";
}
