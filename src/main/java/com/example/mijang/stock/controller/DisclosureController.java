package com.example.mijang.stock.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.stock.dto.FilingResponse;
import com.example.mijang.stock.dto.FinancialFactResponse;
import com.example.mijang.stock.service.DisclosureService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공시·재무제표 API. 출처는 SEC EDGAR.
 *
 * <p>개발명세서(MVC) · 종목 · controller — 종목 상세 화면의 공시/재무 영역에 붙는다.
 */
@RestController
@RequestMapping("/api/stocks/{symbol}")
@RequiredArgsConstructor
public class DisclosureController {

    private final DisclosureService disclosureService;

    /**
     * 공시 목록.
     *
     * @param form 특정 종류만 볼 때 지정 (10-K, 10-Q, 8-K). 비우면 전체.
     */
    @GetMapping("/filings")
    public ApiResponse<List<FilingResponse>> filings(
            @PathVariable String symbol,
            @RequestParam(required = false) String form,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(disclosureService.filings(symbol, form, limit));
    }

    /**
     * 재무 지표 시계열.
     *
     * @param metric revenue · netIncome · operatingIncome · assets · equity · eps
     */
    @GetMapping("/financials")
    public ApiResponse<List<FinancialFactResponse>> financials(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "revenue") String metric,
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(disclosureService.financials(symbol, metric, limit));
    }

    /** 티커에 대응하는 SEC CIK. 연동 확인용. */
    @GetMapping("/cik")
    public ApiResponse<Map<String, Object>> cik(@PathVariable String symbol) {
        return ApiResponse.ok(Map.of(
                "symbol", symbol.toUpperCase(),
                "cik", disclosureService.cikOf(symbol),
                "supportedMetrics", disclosureService.supportedMetrics()));
    }
}
