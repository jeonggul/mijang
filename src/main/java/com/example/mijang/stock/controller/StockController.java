package com.example.mijang.stock.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.common.response.PageResponse;
import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.dto.ChartResponse;
import com.example.mijang.stock.dto.StockMetricsResponse;
import com.example.mijang.stock.dto.StockDetailResponse;
import com.example.mijang.stock.dto.StockSearchResponse;
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

/**
 * 종목 API. 개발명세서(API) SEARCH-01~04 · PRICE-02·04·05 · 화면 SR-004, SR-005
 *
 * <p>전부 GET 이고 비로그인도 부를 수 있다([[미장-기획서]] 4장).
 * {@code SecurityConfig} 가 {@code GET /api/stocks/**} 만 열어 두었다 — 같은 경로의
 * 쓰기 API 까지 열리지 않게 메서드를 못 박은 것이다(2.9).
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockSearchService stockSearchService;
    private final StockService stockService;
    private final DailyPriceService dailyPriceService;
    private final ChartService chartService;
    private final StockMetricsService stockMetricsService;

    /**
     * 종목 검색. {@code SEARCH-01}·{@code SEARCH-02}
     *
     * <p>자동완성이 글자마다 부른다. 빈 검색어는 서비스가 DB 를 보지 않고 빈 목록을 돌려준다.
     */
    @GetMapping("/search")
    public ApiResponse<List<StockSearchResponse>> search(@RequestParam("q") String q) {
        return ApiResponse.ok(stockSearchService.search(q));
    }

    /**
     * 시장·자산군별 목록. {@code SEARCH-03}·{@code SEARCH-04}
     *
     * <p>페이지로 나눠 준다. 검색 화면 아래의 전체 목록이 "더보기" 로 이어 받는다.
     *
     * @param exchange   NASDAQ·NYSE 등. 생략하면 전체
     * @param assetClass STOCK 또는 ETF. 생략하면 전체
     * @param page       0 부터
     * @param size       한 번에 받을 건수
     */
    @GetMapping
    public ApiResponse<PageResponse<StockSearchResponse>> list(
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String assetClass,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(stockSearchService.list(exchange, assetClass, page, size));
    }

    /** 종목 상세. {@code PRICE-02}·{@code PRICE-04} */
    @GetMapping("/{symbol}")
    public ApiResponse<StockDetailResponse> detail(@PathVariable String symbol) {
        return ApiResponse.ok(stockService.detail(symbol));
    }

    /**
     * 일봉. {@code PRICE-05}
     *
     * @param range 1M·3M·6M·1Y·5Y. 모르는 값이면 1M 로 본다
     */
    /**
     * 차트. {@code PRICE-05}·{@code PRICE-07}
     *
     * <p>{@code /candles} 와 달리 분봉·주봉·월봉까지 낸다. 화면의 기간 버튼이 이쪽을 부른다.
     *
     * @param range LIVE·1D·1W·1M·3M·1Y·5Y·ALL. 모르는 값은 3M 으로 본다
     */
    @GetMapping("/{symbol}/chart")
    public ApiResponse<ChartResponse> chart(@PathVariable String symbol,
                                            @RequestParam(defaultValue = "3M") String range) {
        return ApiResponse.ok(chartService.chart(symbol, range));
    }

    /**
     * 투자 지표. {@code PRICE-09}·{@code INFO-03}
     *
     * <p>시세가 아니라 종목 정보다. 출처가 Alpaca 가 아니라 Finnhub 인 이유가 그것이다.
     *
     * <p>상세와 따로 두는 이유 — 벤더가 다르고, 지표가 없어도 시세와 차트는 보여야 한다.
     * 한 응답에 묶으면 한쪽이 막혔을 때 다른 쪽까지 못 보게 된다.
     */
    @GetMapping("/{symbol}/metrics")
    public ApiResponse<StockMetricsResponse> metrics(@PathVariable String symbol) {
        return ApiResponse.ok(stockMetricsService.metrics(symbol));
    }

    @GetMapping("/{symbol}/candles")
    public ApiResponse<List<CandleResponse>> candles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1M") String range) {
        return ApiResponse.ok(dailyPriceService.candles(symbol, range));
    }
}
