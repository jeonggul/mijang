/*
 * HoldingController — 보유 현황 API
 *
 * 이 파일이 하는 일
 *   포트폴리오 화면이 부르는 두 가지를 내준다 — 보유 종목 목록과 총 평가금액.
 */
package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.dto.HoldingResponse;
import com.example.mijang.portfolio.service.HoldingService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보유 현황 API. 개발명세서(API) ACCOUNT-04·05·07 · 화면 SR-003·SR-008
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    /** 보유 종목 목록. 수량 0 인 종목은 빠진다(2.4). */
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingResponse>> holdings(@LoginUser SessionUser me) {
        return ApiResponse.ok(holdingService.findByUser(me.userId()));
    }

    /** 총 평가금액(원). {@code ACCOUNT-07} */
    @GetMapping("/total")
    public ApiResponse<BigDecimal> total(@LoginUser SessionUser me) {
        return ApiResponse.ok(holdingService.totalMarketValueKrw(me.userId()));
    }
}
