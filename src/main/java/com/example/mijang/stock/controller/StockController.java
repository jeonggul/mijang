package com.example.mijang.stock.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.common.response.PageResponse;
import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.dto.ChartResponse;
import com.example.mijang.stock.dto.StockMetricsResponse;
import com.example.mijang.stock.dto.StockDetailResponse;
import com.example.mijang.stock.dto.StockSearchResponse;
import com.example.mijang.news.service.CalendarService;
import com.example.mijang.stock.service.ChartService;
import com.example.mijang.stock.service.StockMetricsService;
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

/** 종목 검색·상세·차트·지표 API 다. 전부 GET 이고 비로그인도 부를 수 있다. */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockSearchService stockSearchService;
    private final StockService stockService;
    private final DailyPriceService dailyPriceService;
    private final ChartService chartService;
    private final StockMetricsService stockMetricsService;
    private final CalendarService calendarService;

    /** 종목을 검색한다. 빈 검색어는 빈 목록을 돌려준다. */
    @GetMapping("/search")
    public ApiResponse<List<StockSearchResponse>> search(@RequestParam("q") String q) {
        return ApiResponse.ok(stockSearchService.search(q));
    }

    /** 시장·자산군별 종목 목록을 페이지로 나눠 돌려준다. 조건을 생략하면 전체다. */
    @GetMapping
    public ApiResponse<PageResponse<StockSearchResponse>> list(
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String assetClass,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(stockSearchService.list(exchange, assetClass, page, size));
    }

    /** 종목 상세를 돌려준다. */
    @GetMapping("/{symbol}")
    public ApiResponse<StockDetailResponse> detail(@PathVariable String symbol) {
        return ApiResponse.ok(stockService.detail(symbol));
    }

    /** 기간(LIVE~ALL)별 차트를 돌려준다. 모르는 값은 3M 으로 본다. */
    @GetMapping("/{symbol}/chart")
    public ApiResponse<ChartResponse> chart(@PathVariable String symbol,
                                            @RequestParam(defaultValue = "3M") String range) {
        return ApiResponse.ok(chartService.chart(symbol, range));
    }

    /** 투자 지표를 돌려준다. 벤더가 달라 상세와 분리돼 있다. */
    @GetMapping("/{symbol}/metrics")
    public ApiResponse<StockMetricsResponse> metrics(@PathVariable String symbol) {
        return ApiResponse.ok(stockMetricsService.metrics(symbol));
    }

    /** 기간(1M~5Y)별 일봉을 돌려준다. 모르는 값은 1M 로 본다. */
    @GetMapping("/{symbol}/candles")
    public ApiResponse<List<CandleResponse>> candles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1M") String range) {
        return ApiResponse.ok(dailyPriceService.candles(symbol, range));
    }

    /** 이 종목의 다음 실적·배당락 일정을 돌려준다. 없으면 필드가 null 이다. */
    @GetMapping("/{symbol}/next-events")
    public ApiResponse<CalendarService.NextEvents> nextEvents(@PathVariable String symbol) {
        return ApiResponse.ok(calendarService.nextEvents(symbol));
    }
}
