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

/** 경제 지표·실적·배당 캘린더 API를 제공한다. INFO-07. */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class EconomicCalendarController {

    private final EconomicCalendarService economicCalendarService;
    private final CalendarService calendarService;

    /** 기간별 경제 지표 발표 일정을 조회한다. from·to 생략 시 오늘부터 1개월이다. */
    @GetMapping("/economic")
    public ApiResponse<List<EconomicEventResponse>> economic(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean highOnly) {

        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusMonths(1);
        return ApiResponse.ok(economicCalendarService.events(start, end, highOnly));
    }

    /** 다가오는 경제 지표 일정을 조회한다. */
    @GetMapping("/economic/upcoming")
    public ApiResponse<List<EconomicEventResponse>> upcoming(
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "true") boolean highOnly) {
        return ApiResponse.ok(economicCalendarService.upcoming(limit, highOnly));
    }

    /** 기간별 실적 발표 일정을 조회한다. mineOnly 면 내 종목(보유 ∪ 관심)으로 거르며, 내 종목이 없으면 빈 목록이다. */
    @GetMapping("/earnings")
    public ApiResponse<List<CalendarEventResponse>> earnings(
            @LoginUser SessionUser me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mineOnly) {
        Set<String> mine = mineOnly ? calendarService.mySymbols(me == null ? null : me.userId()) : null;
        return ApiResponse.ok(calendarService.earnings(from, to, mine));
    }

    /** 기간별 배당(배당락·지급) 일정을 조회한다. */
    @GetMapping("/dividends")
    public ApiResponse<List<CalendarEventResponse>> dividends(
            @LoginUser SessionUser me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mineOnly) {
        Set<String> mine = mineOnly ? calendarService.mySymbols(me == null ? null : me.userId()) : null;
        return ApiResponse.ok(calendarService.dividends(from, to, mine));
    }

    /** 오늘부터 한 달의 거시·실적·배당 일정을 날짜순으로 병합해 상위 limit 건을 낸다. */
    @GetMapping("/upcoming")
    public ApiResponse<List<CalendarEventResponse>> calendarUpcoming(
            @LoginUser SessionUser me,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(defaultValue = "false") boolean highOnly) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusMonths(1);
        Set<String> mine = mineOnly ? calendarService.mySymbols(me == null ? null : me.userId()) : null;

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
