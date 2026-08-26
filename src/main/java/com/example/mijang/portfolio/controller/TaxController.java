/*
 * TaxController — 양도소득세 참고 계산 API
 *
 * 이 파일이 하는 일
 *   양도세 화면(SR-009-1)이 부르는 하나 — 연도별 실현손익과 과세 추정.
 */
package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.dto.CapitalGainsResponse;
import com.example.mijang.portfolio.service.TaxService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 양도소득세 참고 계산 API. 개발명세서(API) GLOBAL-06
 *
 * <p>참고값이다 — 세무 자문이 아니라는 경고는 화면 상단에 고정돼 있다.
 */
@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    /** 연도별 실현손익·과세 추정. {@code year} 를 비우면 매도가 있는 가장 최근 해. */
    @GetMapping("/capital-gains")
    public ApiResponse<CapitalGainsResponse> capitalGains(@LoginUser SessionUser me,
                                                          @RequestParam(required = false) Integer year) {
        return ApiResponse.ok(taxService.capitalGains(me.userId(), year));
    }
}
