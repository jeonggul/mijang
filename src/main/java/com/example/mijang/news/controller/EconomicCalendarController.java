package com.example.mijang.news.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.news.dto.CalendarEventResponse;
import com.example.mijang.news.dto.EconomicEventResponse;
import com.example.mijang.news.service.CalendarService;
import com.example.mijang.news.service.EconomicCalendarService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경제 캘린더 API. 출처는 BLS(지표 발표)와 연준(FOMC), 그리고 어닝·배당(4.14).
 *
 * <p>개발명세서(API) INFO-07 · 기능명세서 INFO-07
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class EconomicCalendarController {

    private final EconomicCalendarService economicCalendarService;
    private final CalendarService calendarService;

    /**
     * 기간별 경제 지표 발표 일정.
     *
     * @param from     생략하면 오늘
     * @param to       생략하면 from 으로부터 1개월
     * @param highOnly true 면 FOMC·CPI·고용지표 등 큰 발표만
     */
    @GetMapping("/economic")
    public ApiResponse<List<EconomicEventResponse>> economic(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean highOnly) {

        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusMonths(1);
        return ApiResponse.ok(economicCalendarService.events(start, end, highOnly));
    }

    /** 다가오는 일정. 대시보드 위젯용. */
    @GetMapping("/economic/upcoming")
    public ApiResponse<List<EconomicEventResponse>> upcoming(
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "true") boolean highOnly) {
        return ApiResponse.ok(economicCalendarService.upcoming(limit, highOnly));
    }

    /**
     * 기간별 실적 발표 일정(4.14).
     *
     * <p>{@code mineOnly} 가 true 이고 비로그인이면 내 종목이 빈 집합이라 결과도 빈 목록이다
     * — 거시 일정과 달리 실적·배당은 종목 종속이라 비로그인 "내 종목만" 은 의미가 없다.
     */
    @GetMapping("/earnings")
    public ApiResponse<List<CalendarEventResponse>> earnings(
            @LoginUser SessionUser me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mineOnly) {
        Set<String> mine = mineOnly ? calendarService.mySymbols(me == null ? null : me.userId()) : Set.of();
        return ApiResponse.ok(calendarService.earnings(from, to, mine));
    }

    /** 기간별 배당(배당락·배당 지급) 일정(4.14). */
    @GetMapping("/dividends")
    public ApiResponse<List<CalendarEventResponse>> dividends(
            @LoginUser SessionUser me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mineOnly) {
        Set<String> mine = mineOnly ? calendarService.mySymbols(me == null ? null : me.userId()) : Set.of();
        return ApiResponse.ok(calendarService.dividends(from, to, mine));
    }

    /**
     * 오늘부터 한 달, 거시·실적·배당을 한 줄로 모은 대시보드 위젯용 목록.
     *
     * <p>날짜순으로 정렬해 상위 {@code limit} 건만 낸다. 세 출처를 한 화면 위젯에 섞어
     * 보여줄 때, 화면이 세 번 호출해 각자 정렬·병합하는 것보다 서버가 한 번에 정리해
     * 주는 편이 낫다.
     */
    @GetMapping("/upcoming")
    public ApiResponse<List<CalendarEventResponse>> calendarUpcoming(
            @LoginUser SessionUser me,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(defaultValue = "false") boolean highOnly) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusMonths(1);
        Set<String> mine = mineOnly ? calendarService.mySymbols(me == null ? null : me.userId()) : Set.of();

        List<CalendarEventResponse> merged = new ArrayList<>();
        for (EconomicEventResponse e : economicCalendarService.events(today, end, highOnly)) {
            merged.add(new CalendarEventResponse(e.date(), CalendarEventResponse.TYPE_ECONOMIC,
                    null, e.name(), e.note()));
        }
        merged.addAll(calendarService.earnings(today, end, mine));
        merged.addAll(calendarService.dividends(today, end, mine));

        return ApiResponse.ok(merged.stream()
                .sorted(Comparator.comparing(CalendarEventResponse::date))
                .limit(limit)
                .toList());
    }
}
