package com.example.mijang.stock.service;

import com.example.mijang.stock.dto.CandleResponse;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 일봉 조회·수집. 개발명세서(API) STOCK-003 · 수집원은 Alpaca(SIP)
 */
@Service
@RequiredArgsConstructor
public class DailyPriceService {

    private final DailyPriceMapper dailyPriceMapper;

    public List<CandleResponse> candles(String symbol, String range) {
        throw new UnsupportedOperationException("TODO STOCK-003: range 파싱 후 기간 조회");
    }
}
