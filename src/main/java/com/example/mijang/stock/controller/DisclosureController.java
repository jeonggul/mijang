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

/** SEC EDGAR 를 출처로 하는 공시·재무제표 API 다. */
@RestController
@RequestMapping("/api/stocks/{symbol}")
@RequiredArgsConstructor
public class DisclosureController {

    private final DisclosureService disclosureService;

    /** 공시 목록을 돌려준다. form(10-K 등)을 지정하면 그 종류만, 비우면 전체다. */
    @GetMapping("/filings")
    public ApiResponse<List<FilingResponse>> filings(
            @PathVariable String symbol,
            @RequestParam(required = false) String form,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(disclosureService.filings(symbol, form, limit));
    }

    /** 재무 지표(revenue·netIncome 등) 시계열을 돌려준다. */
    @GetMapping("/financials")
    public ApiResponse<List<FinancialFactResponse>> financials(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "revenue") String metric,
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(disclosureService.financials(symbol, metric, limit));
    }

    /** 티커에 대응하는 SEC CIK 를 돌려준다. 연동 확인용이다. */
    @GetMapping("/cik")
    public ApiResponse<Map<String, Object>> cik(@PathVariable String symbol) {
        return ApiResponse.ok(Map.of(
                "symbol", symbol.toUpperCase(),
                "cik", disclosureService.cikOf(symbol),
                "supportedMetrics", disclosureService.supportedMetrics()));
    }
}
