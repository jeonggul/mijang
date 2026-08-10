package com.example.mijang.stock.service;

import com.example.mijang.stock.dto.StockSearchResponse;
import com.example.mijang.stock.mapper.StockMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 종목 검색. 개발명세서(API) STOCK-001
 */
@Service
@RequiredArgsConstructor
public class StockSearchService {

    private final StockMapper stockMapper;

    public List<StockSearchResponse> search(String q) {
        throw new UnsupportedOperationException("TODO STOCK-001: 전방 일치 인덱스로 검색");
    }
}
