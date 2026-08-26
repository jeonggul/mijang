/*
 * AdminController — 관리자 API
 *
 * 이 파일이 하는 일
 *   관리자 화면이 부르는 것들을 내준다 — 종목 목록·토글·수동 동기화,
 *   배치 상태, 운영 로그.
 *   권한은 SecurityConfig 가 /api/admin/** 규칙으로 막는다. 여기서 다시
 *   확인하지 않는 이유는, 컨트롤러마다 넣으면 언젠가 빠뜨리는 곳이 생기기 때문이다.
 */
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

/**
 * 관리자 API. 개발명세서(API) ADMIN-01·02·07 · 화면 SR-013
 *
 * <p>권한은 {@code SecurityConfig} 의 {@code /api/admin/**} 규칙이 막는다(2.1).
 * 여기서 다시 확인하지 않는다 — 컨트롤러마다 넣으면 빠뜨리는 곳이 생긴다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final com.example.mijang.user.service.NotificationProducerService notificationProducerService;

    /** 종목 활성·비활성 전환. {@code ADMIN-01} */
    @PatchMapping("/stocks/active")
    public ApiResponse<Void> toggleStock(@LoginUser SessionUser me,
                                         @RequestBody @jakarta.validation.Valid ToggleRequest request) {
        adminService.toggleStock(me.userId(), request.symbol(), request.active(), request.reason());
        return ApiResponse.ok(null);
    }

    /**
     * 종목 마스터 수동 동기화. {@code ADMIN-01}
     *
     * <p>수십 초가 걸린다. 동기로 돌고 건수를 돌려준다(2.5).
     */
    @PostMapping("/stocks/sync")
    public ApiResponse<Integer> syncStocks(@LoginUser SessionUser me) {
        return ApiResponse.ok(adminService.syncStockMaster(me.userId()));
    }

    /**
     * 관리자용 종목 목록. {@code ADMIN-01}
     *
     * <p>{@code /api/stocks} 를 쓰지 않는다. 그쪽은 활성만 돌려주어 내려간 종목이 안 보인다.
     */
    @GetMapping("/stocks")
    public ApiResponse<List<StockSearchResponse>> stocks(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String assetClass,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(adminService.stocks(status, assetClass, q, Math.min(limit, 200)));
    }

    /**
     * 같은 조건의 전체 건수. {@code ADMIN-01}
     *
     * <p>목록은 {@code limit} 로 잘려 나가므로 "몇 건 중 몇 건을 보고 있는지" 를 알려면
     * 총계가 따로 필요하다. {@code AdminService.stockCount} 가 이미 있었는데 밖으로
     * 나오는 길이 없었다.
     */
    @GetMapping("/stocks/count")
    public ApiResponse<Integer> stockCount(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String assetClass,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(adminService.stockCount(status, assetClass, q));
    }

    /**
     * 알림 생성 수동 실행. {@code ADMIN-02}
     *
     * <p>스케줄(07:30)을 놓쳤거나 일봉을 늦게 받았을 때 다시 돌린다. 같은 날 두 번
     * 돌아도 안전하다 — 생성 질의가 "오늘 만든 같은 알림" 을 거른다.
     */
    @PostMapping("/batches/notifications")
    public ApiResponse<Integer> produceNotifications(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date) {
        /* 날짜를 주면 그날 치를 다시 만든다(스냅샷 백필과 같은 결). 안 주면
           마지막 마감 거래일 — 아직 장중이거나 개장 전이면 하루 물린다 */
        return ApiResponse.ok(date != null
                ? notificationProducerService.produce(date)
                : notificationProducerService.produceLatestClosed());
    }

    /** 배치 상태. {@code ADMIN-02} */
    @GetMapping("/batches")
    public ApiResponse<List<BatchLogResponse>> batches() {
        return ApiResponse.ok(adminService.batchStatus());
    }

    /** 운영 로그. {@code ADMIN-07} */
    @GetMapping("/logs")
    public ApiResponse<List<AdminLogResponse>> logs(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(adminService.recentLogs(limit));
    }

    /** 전환 요청 본문. 비활성으로 내릴 때만 사유가 의미 있다. */
    public record ToggleRequest(@NotBlank String symbol, boolean active, String reason) {
    }
}
