package com.example.mijang.community.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** posts 조회 결과 한 줄을 담는다. 선언 순서가 PostMapper.xml SELECT 순서와 어긋나면 값이 뒤바뀐다. */
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
        /* PUBLISHED · HIDDEN · DELETED */
        String status) {
}
