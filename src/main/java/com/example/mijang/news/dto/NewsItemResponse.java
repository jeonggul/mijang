package com.example.mijang.news.dto;

import java.time.Instant;

/** 종목 뉴스 한 건의 응답을 담는다. 본문은 전재하지 않고 원문 링크만 준다. */
public record NewsItemResponse(
        String headline,
        String summary,
        String source,
        Instant publishedAt,
        String url,
        String imageUrl) {
}
