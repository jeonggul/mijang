package com.example.mijang.portfolio.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.portfolio.dto.TransactionForm;
import com.example.mijang.portfolio.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매매 기록 API. 개발명세서(API) PORT-003~005 · 화면 SR-005
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /** PORT-003 매매 기록 등록 */
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody TransactionForm form) {
        return ApiResponse.ok(transactionService.create(null, form));
    }

    /** PORT-004 매매 기록 목록 */
    @GetMapping
    public ApiResponse<Void> list() {
        throw new UnsupportedOperationException("TODO PORT-004: 페이징 목록 조회");
    }

    /** PORT-005 매매 기록 삭제 */
    @DeleteMapping("/{txId}")
    public ApiResponse<Void> delete(@PathVariable Long txId) {
        transactionService.delete(null, txId);
        return ApiResponse.ok(null);
    }
}
