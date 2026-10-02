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

/** 보유 종목 목록과 총 평가금액을 내려주는 API 컨트롤러다. */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    /** 보유 종목 목록을 조회한다. 수량 0 인 종목은 제외한다. */
    @GetMapping("/holdings")
    public ApiResponse<List<HoldingResponse>> holdings(@LoginUser SessionUser me) {
        return ApiResponse.ok(holdingService.findByUser(me.userId()));
    }

    /** 총 평가금액(원)을 조회한다. */
    @GetMapping("/total")
    public ApiResponse<BigDecimal> total(@LoginUser SessionUser me) {
        return ApiResponse.ok(holdingService.totalMarketValueKrw(me.userId()));
    }
}
