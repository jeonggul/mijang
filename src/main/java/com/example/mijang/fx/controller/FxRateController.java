/*
 * FxRateController — 환율 API
 *
 * 이 파일이 하는 일
 *   환율 두 가지를 내준다 — 지금 값과 그날 확정값.
 *
 *   둘 다 우리 DB 에서 나온다. 이 경로가 벤더를 부르지 않는 것이 요점이다.
 *   비로그인도 부를 수 있다. 환율은 공개 정보이고, 매매 기록 화면이 로그인 전에도
 *   환율을 보여줄 수 있어야 한다.
 */
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

/** 환율. 개발명세서(API) {@code GLOBAL-01} */
@RestController
@RequestMapping("/api/fx")
@RequiredArgsConstructor
public class FxRateController {

    private final FxRateService fxRateService;

    /**
     * 환율 조회.
     *
     * <p>날짜를 주면 <b>그날 확정값</b>, 안 주면 <b>지금 값</b>이다. 쓰임이 다르기 때문이다 —
     * 손익 계산은 확정값을, 원화 환산 표시는 지금 값을 봐야 한다.
     *
     * <p>없어도 오류가 아니다. {@code data} 가 null 로 나가고 화면이 그것을 보고 판단한다
     * ([[미장-API명세서]] 1.6).
     */
    @GetMapping("/rates")
    public ApiResponse<FxRateResponse> rate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(date == null
                ? fxRateService.latest().orElse(null)
                : fxRateService.findByDate(date).orElse(null));
    }
}
