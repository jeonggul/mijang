/*
 * SnapshotController — 리포트 API
 *
 * 이 파일이 하는 일
 *   리포트 화면이 부르는 두 가지를 내준다 — 기간별 자산 추이와 기간 수익률.
 *   여기서 계산하지 않는다. 배치가 미리 찍어 둔 값을 읽어서 넘길 뿐이다.
 */
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

/**
 * 리포트 API. 개발명세서(API) PROFIT-06·09 · 화면 SR-009
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;

    /**
     * 자산 추이. {@code PROFIT-09}
     *
     * <p>{@code from} 을 생략하면 최근 3개월이다. 차트의 기본 구간과 맞춘다.
     */
    @GetMapping("/series")
    public ApiResponse<List<SnapshotResponse>> series(
            @LoginUser SessionUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = (to == null) ? LocalDate.now() : to;
        LocalDate begin = (from == null) ? end.minusMonths(3) : from;
        return ApiResponse.ok(snapshotService.series(me.userId(), begin, end));
    }

    /**
     * 기간 수익률. {@code PROFIT-06}
     *
     * <p>스냅샷이 없으면 {@code data} 가 null 이다. 가입 직후이거나 배치가 아직
     * 한 번도 돌지 않은 경우로, 오류가 아니다.
     */
    /**
     * 놓친 날 스냅샷 다시 찍기. {@code 2.4}
     *
     * <p>배치가 실패한 날은 차트에 구멍으로 남는다. 그 날짜를 넣어 다시 부를 수 있어야
     * 한다고 정해 두었는데(2.4) 부를 입구가 없었다.
     *
     * <p><b>부른 사람 것만</b> 다시 찍는다. 이미 있으면 덮어쓴다(2.5).
     *
     * @return 찍었으면 true. 거래일이 아니거나 그날 환율·보유가 없으면 false
     */
    @PostMapping("/snapshots")
    public ApiResponse<Boolean> backfill(
            @LoginUser SessionUser me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(snapshotService.backfill(me.userId(), date));
    }

    @GetMapping("/period")
    public ApiResponse<PeriodReturnResponse> period(
            @LoginUser SessionUser me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(snapshotService.periodReturn(me.userId(), from, to));
    }
}
