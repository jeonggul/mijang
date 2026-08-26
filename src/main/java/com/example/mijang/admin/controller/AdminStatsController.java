package com.example.mijang.admin.controller;

import com.example.mijang.admin.dto.AdminStatsResponse;
import com.example.mijang.admin.service.AdminStatsService;
import com.example.mijang.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 통계 API. 권한은 {@code /api/admin/**} 보안 규칙이 막는다. */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService service;

    @GetMapping
    public ApiResponse<AdminStatsResponse> stats(
            @RequestParam(defaultValue = "1M") String period) {
        return ApiResponse.ok(service.stats(period));
    }
}
