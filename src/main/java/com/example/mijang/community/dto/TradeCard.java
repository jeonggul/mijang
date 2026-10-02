package com.example.mijang.community.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 게시글에 첨부된 매매 스냅샷을 담는다. 손익 두 값은 매도에만 있고 매수는 null 이다. */
public record TradeCard(
        String side,
        String symbol,
        BigDecimal price,
        LocalDateTime tradedAt,
        BigDecimal realizedPnlKrw,
        BigDecimal realizedPnlRate) {
}
