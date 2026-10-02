/*
 * PortfolioController — 손익 API
 *
 * 이 파일이 하는 일
 *   대시보드가 손익을 물어보면 답해 준다.
 *   누구의 손익인지는 요청에서 받지 않고 로그인 토큰에서 꺼낸다 —
 *   요청에서 받으면 남의 손익을 들여다볼 수 있게 된다.
 */
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

/**
 * 손익 API. 개발명세서(API) PROFIT-03 · 화면 SR-003
 *
 * <p>인증이 필요하다. 남의 손익을 볼 수 있으면 안 된다.
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final ProfitLossService profitLossService;

    /**
     * 손익 요인 분해.
     *
     * <p>{@code symbol} 을 주면 그 종목만, 생략하면 전체다.
     *
     * <p>환율을 구하지 못하면 {@code data} 가 null 이다. 200 이며 오류가 아니다(2.6).
     */
    @GetMapping("/pnl")
    public ApiResponse<ProfitLossResponse> pnl(@LoginUser SessionUser me,
                                               @RequestParam(required = false) String symbol) {
        return ApiResponse.ok(symbol == null
                ? profitLossService.ofUser(me.userId())
                : profitLossService.ofSymbol(me.userId(), symbol));
    }
}
