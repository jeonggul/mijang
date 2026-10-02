package com.example.mijang.news.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.news.dto.NewsItemResponse;
import com.example.mijang.news.service.StockNewsFetchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 종목 뉴스 API를 제공한다. 제목·요약·원문 링크만 주고 본문은 전재하지 않는다. */
@RestController
@RequiredArgsConstructor
public class NewsController {

    private final StockNewsFetchService newsFetchService;

    /** 종목별 뉴스 목록을 조회한다. NEWS-001 · INFO-01. */
    @GetMapping("/api/stocks/{symbol}/news")
    public ApiResponse<List<NewsItemResponse>> list(@PathVariable String symbol) {
        return ApiResponse.ok(newsFetchService.news(symbol));
    }
}
