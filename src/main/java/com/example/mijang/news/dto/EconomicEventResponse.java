package com.example.mijang.news.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 경제 지표 발표 일정 1건.
 *
 * <p>개발명세서(MVC) · 뉴스·정보 · dto — 기능명세서 INFO-07 경제 캘린더
 *
 * <p>시각은 미 동부시각(ET) 그대로 내려준다. 한국시간 변환은 화면에서 한다. 서머타임 때문에
 * 같은 08:30 이 한국시간으로 21:30 이 되기도 22:30 이 되기도 하는데, 그 계산을 서버에서 미리
 * 해버리면 사용자 시간대가 바뀔 때 손댈 곳이 늘어난다.
 *
 * @param importance HIGH 는 시장이 크게 움직이는 발표(FOMC·CPI·고용지표 등)
 * @param note       부가 설명. FOMC 의 경제전망 발표 회의 표시 등에 쓴다.
 */
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
