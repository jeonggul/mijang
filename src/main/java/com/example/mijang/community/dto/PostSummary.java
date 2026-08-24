/*
 * PostSummary — 목록에 한 줄로 뜨는 게시글
 *
 * 이 파일이 하는 일
 *   목록 화면이 그리는 값이다. 본문 전체 대신 앞머리만 담는다 —
 *   스무 줄짜리 목록에 본문을 통째로 실어 보내면 대부분 버려진다.
 */
package com.example.mijang.community.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 게시글 목록 한 줄. 개발명세서(API) COM-001
 *
 * @param excerpt      본문 앞머리. 목록에 회색 한 줄로 뜬다
 * @param shareholder  작성자가 그 종목을 들고 있었는지. 켜지면 "주주" 배지가 붙고
 *                     수량은 나가지 않는다
 * @param priceAtWrite 작성 시점 주가. 종목별 게시판만 값이 있다
 * @param trade        붙인 매매. 없으면 null
 */
public record PostSummary(
        Long id,
        String board,
        String symbol,
        String title,
        String excerpt,
        String authorName,
        boolean shareholder,
        BigDecimal priceAtWrite,
        TradeCard trade,
        long likeCount,
        long commentCount,
        LocalDateTime createdAt) {
}
