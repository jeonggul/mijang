package com.example.mijang.market.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.market.domain.MarketSession;
import com.example.mijang.market.dto.MarketStatusResponse;
import com.example.mijang.market.service.MarketCalendarService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 헤더가 DB 거래일 달력 기준의 현재 장 상태를 조회하는 API. */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketStatusController {

    private final MarketCalendarService marketCalendarService;

    @GetMapping("/session")
    public ApiResponse<MarketStatusResponse> status() {
        MarketSession session = marketCalendarService.currentSession();
        return ApiResponse.ok(new MarketStatusResponse(
                session.name(), session.label(), session == MarketSession.REGULAR, Instant.now()));
    }
}
