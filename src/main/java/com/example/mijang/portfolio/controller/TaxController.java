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

/** 연도별 실현손익과 양도소득세 참고 추정을 내려주는 API 컨트롤러다. */
@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    /** 연도별 실현손익과 과세 추정을 조회한다. year 를 비우면 매도가 있는 가장 최근 해다. */
    @GetMapping("/capital-gains")
    public ApiResponse<CapitalGainsResponse> capitalGains(@LoginUser SessionUser me,
                                                          @RequestParam(required = false) Integer year) {
        return ApiResponse.ok(taxService.capitalGains(me.userId(), year));
    }
}
