package com.example.mijang.dividend.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.dividend.dto.StockDividendTabResponse;
import com.example.mijang.dividend.service.StockDividendQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 종목 배당 탭(INFO-06)용 공개 API를 제공한다. */
@RestController
@RequiredArgsConstructor
public class StockDividendController {

    private final StockDividendQueryService queryService;

    /** 종목 배당 이력·요약을 조회하며, 데이터가 낡았으면 수집부터 한다. */
    @GetMapping("/api/stocks/{symbol}/dividends")
    public ApiResponse<StockDividendTabResponse> dividends(@PathVariable String symbol) {
        return ApiResponse.ok(queryService.tab(symbol));
    }
}
