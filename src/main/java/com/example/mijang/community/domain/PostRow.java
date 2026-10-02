/*
 * PostRow — posts 한 줄, DB 모양 그대로
 *
 * 이 파일이 하는 일
 *   매퍼가 꺼내 오는 원본이다. 화면이 쓰는 모양(PostSummary·PostDetail)으로
 *   바꾸는 일은 서비스가 한다.
 *   왜 나누는가 — MyBatis 는 record 를 "선언 순서"로 채운다. 중첩된 객체는 그렇게
 *   못 채우는데 매매 카드가 중첩이다. 매퍼는 납작한 줄만 꺼내고, 카드로 접는 것은
 *   서비스가 맡으면 SQL 이 화면 모양을 따라다니지 않아도 된다.
 */
package com.example.mijang.community.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * posts 조회 결과 한 줄.
 *
 * <p><b>컬럼 순서가 곧 매핑이다.</b> 이 선언 순서와 {@code PostMapper.xml} 의 SELECT
 * 순서가 어긋나면 값이 조용히 뒤바뀐다.
 *
 * @param shareholder  "주주" 배지를 달지. 수량은 담지 않는다 — 화면에 내보내지 않기로 했다
 * @param tradePnlKrw  붙인 매매가 매도일 때만 값이 있다. 매수는 null
 */
public record PostRow(
        Long id,
        String board,
        String symbol,
        String title,
        String content,
        Long authorId,
        String authorName,
        boolean shareholder,
        BigDecimal priceAtWrite,
        String tradeSide,
        String tradeSymbol,
        BigDecimal tradePrice,
        LocalDateTime tradeAt,
        BigDecimal tradePnlKrw,
        BigDecimal tradePnlRate,
        long likeCount,
        long commentCount,
        long viewCount,
        LocalDateTime createdAt,
        /* PUBLISHED · HIDDEN · DELETED. 목록에서는 걸러지지만 내 글 화면은 이유를 밝힌다 */
        String status) {
}
