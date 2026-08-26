/*
 * DividendController — 배당 API
 *
 * 이 파일이 하는 일
 *   배당 관리 화면(SR-016)이 부르는 다섯 가지 — 목록, 요약, 직접 입력,
 *   확정, 수정·삭제. 경로는 API 명세서 7장을 따른다.
 */
package com.example.mijang.dividend.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.dividend.dto.DividendConfirmForm;
import com.example.mijang.dividend.dto.DividendForm;
import com.example.mijang.dividend.dto.DividendResponse;
import com.example.mijang.dividend.dto.DividendSummaryResponse;
import com.example.mijang.dividend.service.DividendService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배당 API. 개발명세서(API) PROFIT-11·12 · 화면 SR-016
 */
@RestController
@RequestMapping("/api/dividends")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;

    /** 배당 내역. 최근 지급일이 위로 온다. */
    @GetMapping
    public ApiResponse<List<DividendResponse>> list(@LoginUser SessionUser me) {
        return ApiResponse.ok(dividendService.list(me.userId()));
    }

    /** 요약 띠 — 올해 누적·확정 대기·다음 배당. */
    @GetMapping("/summary")
    public ApiResponse<DividendSummaryResponse> summary(@LoginUser SessionUser me) {
        return ApiResponse.ok(dividendService.summary(me.userId()));
    }

    /** 직접 입력(1차). 바로 확정 상태가 된다. */
    @PostMapping
    public ApiResponse<DividendResponse> create(@LoginUser SessionUser me,
                                                @Valid @RequestBody DividendForm form) {
        return ApiResponse.ok(dividendService.create(me.userId(), form));
    }

    /** 예상 → 확정. 이미 확정이면 409. */
    @PostMapping("/{id}/confirm")
    public ApiResponse<DividendResponse> confirm(@LoginUser SessionUser me,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody DividendConfirmForm form) {
        return ApiResponse.ok(dividendService.confirm(me.userId(), id, form));
    }

    /** 수정. */
    @PatchMapping("/{id}")
    public ApiResponse<DividendResponse> update(@LoginUser SessionUser me,
                                                @PathVariable Long id,
                                                @Valid @RequestBody DividendForm form) {
        return ApiResponse.ok(dividendService.update(me.userId(), id, form));
    }

    /** 삭제. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@LoginUser SessionUser me, @PathVariable Long id) {
        dividendService.delete(me.userId(), id);
        return ApiResponse.ok(null);
    }
}
