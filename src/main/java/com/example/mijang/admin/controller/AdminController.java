package com.example.mijang.admin.controller;

import com.example.mijang.admin.dto.AdminLogResponse;
import com.example.mijang.admin.dto.BatchLogResponse;
import com.example.mijang.admin.service.AdminService;
import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import com.example.mijang.stock.dto.StockSearchResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 API — 종목 목록·토글·수동 동기화, 배치 상태, 운영 로그를 내준다. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final com.example.mijang.user.service.NotificationProducerService notificationProducerService;
    private final com.example.mijang.dividend.service.StockDividendSyncService stockDividendSyncService;
    private final com.example.mijang.dividend.service.DividendEstimateService dividendEstimateService;

    /** 종목을 활성·비활성으로 전환한다. */
    @PatchMapping("/stocks/active")
    public ApiResponse<Void> toggleStock(@LoginUser SessionUser me,
                                         @RequestBody @jakarta.validation.Valid ToggleRequest request) {
        adminService.toggleStock(me.userId(), request.symbol(), request.active(), request.reason());
        return ApiResponse.ok(null);
    }

    /** 종목 마스터를 수동 동기화하고 건수를 돌려준다. */
    @PostMapping("/stocks/sync")
    public ApiResponse<Integer> syncStocks(@LoginUser SessionUser me) {
        return ApiResponse.ok(adminService.syncStockMaster(me.userId()));
    }

    /** 관리자용 종목 목록을 조회한다 — 비활성 종목까지 포함한다. */
    @GetMapping("/stocks")
    public ApiResponse<List<StockSearchResponse>> stocks(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String assetClass,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(
                adminService.stocks(status, assetClass, q, Math.min(limit, 200), offset));
    }

    /** 같은 조건의 종목 전체 건수를 돌려준다. */
    @GetMapping("/stocks/count")
    public ApiResponse<Integer> stockCount(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String assetClass,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(adminService.stockCount(status, assetClass, q));
    }

    /** 알림 생성을 수동 실행한다. 같은 날 두 번 돌아도 안전하다. */
    @PostMapping("/batches/notifications")
    public ApiResponse<Integer> produceNotifications(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        // 날짜를 주면 그날 치, 안 주면 마지막 마감 거래일 치를 만든다
        return ApiResponse.ok(date != null
                ? notificationProducerService.produce(date)
                : notificationProducerService.produceLatestClosed());
    }

    /** 배당 수집·예상 생성·알림을 수동 실행하고 새로 만든 예상 배당 수를 돌려준다. 몇 번을 돌려도 안전하다. */
    @PostMapping("/batches/dividends")
    public ApiResponse<Integer> produceDividends() {
        stockDividendSyncService.syncHeldSymbols();
        int created = dividendEstimateService.produceLatest();
        notificationProducerService.produceDividend(
                java.time.LocalDate.now(com.example.mijang.common.time.TradingClock.SERVICE_ZONE));
        return ApiResponse.ok(created);
    }

    /** 배치 상태를 조회한다. */
    @GetMapping("/batches")
    public ApiResponse<List<BatchLogResponse>> batches() {
        return ApiResponse.ok(adminService.batchStatus());
    }

    /** 운영 로그를 조회한다 — 검색어·종류·기간(0이면 전 기간) 필터를 받는다. */
    @GetMapping("/logs")
    public ApiResponse<List<AdminLogResponse>> logs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int days) {
        return ApiResponse.ok(adminService.recentLogs(limit, q, type, days));
    }

    /** 전환 요청 본문. 비활성으로 내릴 때만 사유가 의미 있다. */
    public record ToggleRequest(@NotBlank String symbol, boolean active, String reason) {
    }
}
