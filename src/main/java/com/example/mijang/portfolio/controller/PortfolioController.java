package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.dto.ProfitLossResponse;
import com.example.mijang.portfolio.service.ProfitLossService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 손익 분해 API. 개발명세서(API) PORT-002 · 화면 SR-006
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final ProfitLossService profitLossService;

    /** PORT-002 주가손익 / 환차손익 분해 */
    @GetMapping("/pnl")
    public ApiResponse<ProfitLossResponse> pnl() {
        return ApiResponse.ok(profitLossService.breakdown(null));
    }
}
