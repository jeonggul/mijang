package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.dto.ProfitLossResponse;
import com.example.mijang.portfolio.service.ProfitLossService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자의 손익을 내려주는 API 컨트롤러다. */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final ProfitLossService profitLossService;

    /** 손익 요인 분해를 조회한다. symbol 을 주면 그 종목만, 생략하면 전체다. */
    @GetMapping("/pnl")
    public ApiResponse<ProfitLossResponse> pnl(@LoginUser SessionUser me,
                                               @RequestParam(required = false) String symbol) {
        return ApiResponse.ok(symbol == null
                ? profitLossService.ofUser(me.userId())
                : profitLossService.ofSymbol(me.userId(), symbol));
    }
}
