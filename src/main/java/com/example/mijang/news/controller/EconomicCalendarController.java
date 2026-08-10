package com.example.mijang.news.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.news.dto.EconomicEventResponse;
import com.example.mijang.news.service.EconomicCalendarService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경제 캘린더 API. 출처는 BLS(지표 발표)와 연준(FOMC).
 *
 * <p>개발명세서(API) INFO-07 · 기능명세서 INFO-07
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class EconomicCalendarController {

    private final EconomicCalendarService economicCalendarService;

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
}
