package com.example.mijang.stock.service;

import com.example.mijang.stock.dto.StockDetailResponse;
import com.example.mijang.stock.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 종목 마스터·상세. 개발명세서(API) STOCK-002
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMapper stockMapper;

    public StockDetailResponse detail(String symbol) {
        throw new UnsupportedOperationException("TODO STOCK-002: 마스터 + 캐시 현재가 조합");
    }
}
