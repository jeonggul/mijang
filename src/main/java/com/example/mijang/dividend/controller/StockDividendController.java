/*
 * StockDividendController — 종목 배당 일정 API
 *
 * 이 파일이 하는 일
 *   종목 화면 배당 탭(INFO-06)이 부르는 하나. 종목 검색·시세와 같은
 *   공개 정보라 비로그인도 볼 수 있다(SecurityConfig 의 GET /api/stocks/**).
 */
package com.example.mijang.dividend.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.dividend.dto.StockDividendTabResponse;
import com.example.mijang.dividend.service.StockDividendQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 종목 배당 일정 API. 개발명세서(API) INFO-06
 */
@RestController
@RequiredArgsConstructor
public class StockDividendController {

    private final StockDividendQueryService queryService;

    /** 종목 배당 이력·요약. 낡았으면 수집부터 한다. */
    @GetMapping("/api/stocks/{symbol}/dividends")
    public ApiResponse<StockDividendTabResponse> dividends(@PathVariable String symbol) {
        return ApiResponse.ok(queryService.tab(symbol));
    }
}
