package com.example.mijang.news.dto;

import java.time.LocalDate;

/**
 * 캘린더 이벤트 한 건. 화면이 type 으로 색·아이콘을 가른다.
 *
 * @param type ECONOMIC·EARNINGS·DIVIDEND. 거시엔 symbol 이 null
 */
public record CalendarEventResponse(LocalDate date, String type, String symbol,
                                    String title, String note) {

    public static final String TYPE_ECONOMIC = "ECONOMIC";
    public static final String TYPE_EARNINGS = "EARNINGS";
    public static final String TYPE_DIVIDEND = "DIVIDEND";
}
