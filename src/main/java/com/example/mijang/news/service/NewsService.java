package com.example.mijang.news.service;

import com.example.mijang.news.mapper.NewsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 뉴스 수집·조회. 개발명세서(API) NEWS-001 · 수집원은 Finnhub — 확장(부록 C)
 */
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsMapper newsMapper;

    public void collect(String symbol) {
        throw new UnsupportedOperationException("TODO: Finnhub 뉴스 수집 후 종목 매핑");
    }
}
