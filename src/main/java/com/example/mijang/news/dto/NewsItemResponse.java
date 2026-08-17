/*
 * NewsItemResponse — 종목 뉴스 한 건
 *
 * 이 파일이 하는 일
 *   뉴스 목록에 한 줄로 뜨는 값이다. 제목·요약·출처·발행 시각·원문 링크.
 *
 *   본문은 담지 않는다. 전재는 저작권 문제가 되고, 원문 링크로 보내는 것이
 *   언론사와도 사용자와도 맞는 방식이다([[미장-외부-데이터-출처]] 4장).
 */
package com.example.mijang.news.dto;

import java.time.Instant;

public record NewsItemResponse(
        String headline,
        String summary,
        String source,
        Instant publishedAt,
        String url,
        String imageUrl) {
}
