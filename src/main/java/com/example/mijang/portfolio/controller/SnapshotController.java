package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일별 스냅샷·기간 수익률 API. 개발명세서(API) SNAP-001 · 화면 SR-007
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;

    /** SNAP-001 기간 스냅샷 조회 */
    @GetMapping("/snapshots")
    public ApiResponse<Void> list(@RequestParam(required = false) String from,
                                  @RequestParam(required = false) String to) {
        throw new UnsupportedOperationException("TODO SNAP-001: 기간 스냅샷 조회");
    }
}
