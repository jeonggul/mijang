package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.dto.PeriodReturnResponse;
import com.example.mijang.portfolio.dto.SnapshotResponse;
import com.example.mijang.portfolio.service.SnapshotService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기간별 자산 추이와 기간 수익률을 내려주는 리포트 API 컨트롤러다. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;

    /** 자산 추이를 조회한다. from 을 생략하면 최근 3개월이다. */
    @GetMapping("/series")
    public ApiResponse<List<SnapshotResponse>> series(
            @LoginUser SessionUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = (to == null) ? LocalDate.now() : to;
        LocalDate begin = (from == null) ? end.minusMonths(3) : from;
        return ApiResponse.ok(snapshotService.series(me.userId(), begin, end));
    }

    /** 놓친 날의 본인 스냅샷을 다시 찍는다. 이미 있으면 덮어쓴다. */
    @PostMapping("/snapshots")
    public ApiResponse<Boolean> backfill(
            @LoginUser SessionUser me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(snapshotService.backfill(me.userId(), date));
    }

    /** 기간 수익률을 조회한다. 스냅샷이 없으면 data 가 null 이다. */
    @GetMapping("/period")
    public ApiResponse<PeriodReturnResponse> period(
            @LoginUser SessionUser me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(snapshotService.periodReturn(me.userId(), from, to));
    }
}
