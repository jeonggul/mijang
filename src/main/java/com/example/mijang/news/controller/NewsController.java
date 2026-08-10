package com.example.mijang.news.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.news.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 뉴스 API. 개발명세서(API) NEWS-001 · 화면 SR-010 — 확장(부록 C)
 */
@RestController
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /** NEWS-001 종목별 뉴스 목록 */
    @GetMapping("/api/stocks/{symbol}/news")
    public ApiResponse<Void> list(@PathVariable String symbol) {
        throw new UnsupportedOperationException("TODO NEWS-001: 제목·요약·원문 링크 목록");
    }
}
