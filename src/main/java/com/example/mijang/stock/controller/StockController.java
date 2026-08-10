package com.example.mijang.stock.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.dto.StockDetailResponse;
import com.example.mijang.stock.dto.StockSearchResponse;
import com.example.mijang.stock.service.DailyPriceService;
import com.example.mijang.stock.service.StockSearchService;
import com.example.mijang.stock.service.StockService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 종목 API. 개발명세서(API) STOCK-001~003 · 화면 SR-002, SR-003
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockSearchService stockSearchService;
    private final StockService stockService;
    private final DailyPriceService dailyPriceService;

    /** STOCK-001 종목 검색 */
    @GetMapping("/search")
    public ApiResponse<List<StockSearchResponse>> search(@RequestParam("q") String q) {
        return ApiResponse.ok(stockSearchService.search(q));
    }

    /** STOCK-002 종목 상세 */
    @GetMapping("/{symbol}")
    public ApiResponse<StockDetailResponse> detail(@PathVariable String symbol) {
        return ApiResponse.ok(stockService.detail(symbol));
    }

    /** STOCK-003 일봉 조회 */
    @GetMapping("/{symbol}/candles")
    public ApiResponse<List<CandleResponse>> candles(@PathVariable String symbol,
                                                     @RequestParam(defaultValue = "1M") String range) {
        return ApiResponse.ok(dailyPriceService.candles(symbol, range));
    }
}
