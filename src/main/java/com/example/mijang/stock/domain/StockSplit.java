/*
 * StockSplit — 주식 분할 한 건
 *
 * 이 파일이 하는 일
 *   "이 날부터 한 주가 몇 주가 되었는가" 를 담는다.
 *
 *   왜 필요한가
 *     시세는 이미 분할이 반영된 값으로 들어온다 — AlpacaStockClient.bars 가
 *     adjustment=split 으로 받는다. 그런데 사용자의 매매 기록은 그날 실제로 체결한
 *     수량과 단가 그대로다. 4:1 분할이 있었다면 기록에는 10주 $400 인데 시세는 $100 이라,
 *     10 × $100 = $1,000 으로 계산된다. 실제로는 40주 × $100 = $4,000 이다.
 *     평가금액이 4분의 1로 줄어 보인다.
 *
 *   그래서 원장은 그대로 두고, 보유를 계산할 때만 분할 이전 거래를 보정한다.
 *   원장은 "그날 무슨 일이 있었는가" 의 기록이라 나중에 고쳐 쓰지 않는다.
 */
package com.example.mijang.stock.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/**
 * 분할 한 건. {@code stock_splits} 한 행이다.
 *
 * @param exDate  분할 기준일. <b>이 날부터</b> 조정된 수량·주가로 거래된다
 * @param oldRate 분할 전 주식 수. 4:1 분할이면 1
 * @param newRate 분할 후 주식 수. 4:1 분할이면 4
 */
public record StockSplit(String symbol,
                         LocalDate exDate,
                         String splitType,
                         BigDecimal oldRate,
                         BigDecimal newRate) {

    /**
     * 보정 배수. 분할 전 1주가 몇 주가 되었는가.
     *
     * <p>4:1 분할이면 4, 1:10 병합(역분할)이면 0.1 이다.
     * 나누어떨어지지 않는 비율(3:2 → 1.5)이 있어 자리수를 넉넉히 둔다.
     *
     * <p>비율이 깨져 있으면(0 이하) 1 을 돌려준다 — 0 으로 나누어 계산을 터뜨리느니
     * 보정을 안 한 값이 낫다. 어차피 벤더가 그런 값을 줄 일은 없다.
     */
    public BigDecimal factor() {
        if (oldRate == null || newRate == null
                || oldRate.signum() <= 0 || newRate.signum() <= 0) {
            return BigDecimal.ONE;
        }
        return newRate.divide(oldRate, MathContext.DECIMAL64);
    }
}
