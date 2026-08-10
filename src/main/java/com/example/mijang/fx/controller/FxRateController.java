package com.example.mijang.fx.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.fx.service.FxRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 환율 API. 개발명세서(API) FX-001 · 화면 SR-008
 *
 * <p>MVC 시트에는 환율 컨트롤러 행이 없어 API 시트를 근거로 추가했다.
 * <p>TODO: 비영업일 처리 규칙이 미정이다. 직전 영업일 고시를 쓸지, 없음으로 응답할지 정해야 한다.
 */
@RestController
@RequestMapping("/api/fx")
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateService fxRateService;

    /** FX-001 일별 원달러 환율 조회. date 를 생략하면 오늘 기준. */
    @GetMapping("/rates")
    public ApiResponse<BigDecimal> rate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(fxRateService.findByDate(date == null ? LocalDate.now() : date));
    }
}
