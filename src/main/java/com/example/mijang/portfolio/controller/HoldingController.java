package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.dto.HoldingResponse;
import com.example.mijang.portfolio.service.HoldingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보유 현황 API. 개발명세서(API) PORT-001 · 화면 SR-005
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    /** PORT-001 보유 현황 조회 */
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingResponse>> holdings() {
        return ApiResponse.ok(holdingService.findByUser(null));
    }
}
