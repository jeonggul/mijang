/*
 * TradeCard — 글에 붙은 매매 한 건
 *
 * 이 파일이 하는 일
 *   "나 이거 샀다 / 팔았다" 를 글 안에 카드로 보여 주기 위한 값이다.
 *   전부 등록 시점에 박아 둔 스냅샷이라 나중에 매매를 고쳐도 이 카드는 안 바뀐다.
 *   수량은 담지 않는다. 화면에 안 보여 주기로 한 값을 응답에 실을 이유가 없다.
 */
package com.example.mijang.community.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 게시글에 첨부된 매매. 화면 SR-009
 *
 * <p>{@code realizedPnlKrw}·{@code realizedPnlRate} 는 <b>매도에만</b> 있다.
 * 매수 시점에는 확정된 손익이 없다 — 0 으로 채우면 "본전" 으로 읽힌다.
 *
 * @param side  {@code BUY} 또는 {@code SELL}
 * @param price 1주당 체결 단가 (USD)
 */
public record TradeCard(
        String side,
        String symbol,
        BigDecimal price,
        LocalDateTime tradedAt,
        BigDecimal realizedPnlKrw,
        BigDecimal realizedPnlRate) {
}
