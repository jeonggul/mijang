/*
 * NewsController — 종목 뉴스 API
 *
 * 이 파일이 하는 일
 *   종목 상세의 뉴스 탭이 부르는 경로를 내준다.
 *
 *   본문은 담지 않는다. 제목·요약과 원문 링크만 준다 — 전재는 저작권 문제가 되고,
 *   원문으로 보내는 것이 언론사와도 사용자와도 맞는 방식이다.
 *
 *   로그인 없이 부를 수 있다. 종목 정보와 같은 성격이다.
 */
package com.example.mijang.news.controller;

import com.example.mijang.common.response.ApiResponse;
import com.example.mijang.news.dto.NewsItemResponse;
import com.example.mijang.news.service.StockNewsFetchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NewsController {

    private final StockNewsFetchService newsFetchService;

    /** 종목별 뉴스 목록. {@code NEWS-001}·{@code INFO-01} */
    @GetMapping("/api/stocks/{symbol}/news")
    public ApiResponse<List<NewsItemResponse>> list(@PathVariable String symbol) {
        return ApiResponse.ok(newsFetchService.news(symbol));
    }
}
