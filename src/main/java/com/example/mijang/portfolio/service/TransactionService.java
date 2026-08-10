package com.example.mijang.portfolio.service;

import com.example.mijang.portfolio.dto.TransactionForm;
import com.example.mijang.portfolio.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 매매 기록 CRUD. 개발명세서(API) PORT-003~005 · 저장 후 holdings 를 재계산한다.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionMapper transactionMapper;

    public Long create(Long userId, TransactionForm form) {
        throw new UnsupportedOperationException("TODO PORT-003: 원장 저장 후 holdings 재계산");
    }

    public void delete(Long userId, Long txId) {
        throw new UnsupportedOperationException("TODO PORT-005: 소유자 확인 후 삭제, holdings 재계산");
    }
}
