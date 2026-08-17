/*
 * TransactionController — 매매 기록 API
 *
 * 이 파일이 하는 일
 *   거래 입력·목록·삭제 화면이 부르는 것들을 내준다.
 *   전부 로그인이 필요하다. 누구의 기록인지는 요청에서 받지 않고 토큰에서 꺼낸다 —
 *   요청에서 받으면 남의 기록을 보거나 남의 이름으로 저장할 수 있게 된다.
 */
package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.common.response.PageResponse;
import com.example.mijang.portfolio.dto.TransactionForm;
import com.example.mijang.portfolio.dto.TransactionResponse;
import com.example.mijang.portfolio.service.TransactionService;
import com.example.mijang.security.LoginUser;
import com.example.mijang.security.SessionUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매매 기록 API. 개발명세서(API) ACCOUNT-01·06 · 화면 SR-006·SR-007
 *
 * <p>전부 인증이 필요하다. 사용자 식별자는 요청에서 받지 않고 토큰에서 꺼낸다 —
 * 받으면 남의 기록을 조회하거나 남의 이름으로 저장할 수 있다.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /** 매매 기록 등록. {@code ACCOUNT-01}·{@code ACCOUNT-02} */
    @PostMapping
    public ApiResponse<Long> create(@LoginUser SessionUser me,
                                    @Valid @RequestBody TransactionForm form) {
        return ApiResponse.ok(transactionService.create(me.userId(), form));
    }

    /**
     * 목록. {@code ACCOUNT-06}
     *
     * @param symbol 주면 그 종목만. 생략하면 전체
     */
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @LoginUser SessionUser me,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<TransactionResponse> items = transactionService.list(me.userId(), symbol, page, size);
        long total = transactionService.count(me.userId(), symbol);
        return ApiResponse.ok(PageResponse.of(items, page, size, total));
    }

    /** 삭제. 지우지 않고 표시만 한다(2.9). */
    @DeleteMapping("/{txId}")
    public ApiResponse<Void> delete(@LoginUser SessionUser me, @PathVariable Long txId) {
        transactionService.delete(me.userId(), txId);
        return ApiResponse.ok(null);
    }
}
