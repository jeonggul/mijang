package com.example.mijang.fx.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.fx.dto.FxRateResponse;
import com.example.mijang.fx.service.FxRateService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 현재·확정 환율을 내주는 API 다. {@code GLOBAL-01} */
@RestController
@RequestMapping("/api/fx")
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateService fxRateService;

    /** 환율을 조회한다. 날짜를 주면 그날 확정값, 안 주면 현재 값을 준다. */
    @GetMapping("/rates")
    public ApiResponse<FxRateResponse> rate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(date == null
                ? fxRateService.latest().orElse(null)
                : fxRateService.findByDate(date).orElse(null));
    }
}
