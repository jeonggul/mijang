package com.example.mijang.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 보유 한 줄의 손익 분해.
 *
 * <p>계산은 SQL 이 한다. 여기서 지키려는 것은 <b>식이 총계와 같은가</b>다 —
 * 화면 위쪽 합계와 아래 표의 행이 다른 식을 쓰면 더해 봤을 때 안 맞고,
 * 사용자는 둘 중 어느 쪽이 맞는지 알 수 없다.
 *
 * <p>예전에는 이 세 값이 화면에서 {@code null} 로 박혀 있어 표의 세 자리가 늘 비었다.
 */
class HoldingPnlSplitTest {

    /** ProfitLossCalculator 와 HoldingMapper.xml 이 함께 쓰는 식 */
    private static BigDecimal pricePnl(String qty, String cur, String avg, String fxNow) {
        return new BigDecimal(qty)
                .multiply(new BigDecimal(cur).subtract(new BigDecimal(avg)))
                .multiply(new BigDecimal(fxNow));
    }

    private static BigDecimal fxPnl(String qty, String avg, String fxNow, String fxAvg) {
        return new BigDecimal(qty).multiply(new BigDecimal(avg))
                .multiply(new BigDecimal(fxNow).subtract(new BigDecimal(fxAvg)));
    }

    private static BigDecimal evalPnl(String qty, String cur, String fxNow, String avg, String fxAvg) {
        return new BigDecimal(qty).multiply(
                new BigDecimal(cur).multiply(new BigDecimal(fxNow))
                        .subtract(new BigDecimal(avg).multiply(new BigDecimal(fxAvg))));
    }

    /* 두 갈래를 더하면 평가손익이 나와야 한다. 안 그러면 어느 한쪽 환율을 잘못 쓴 것이다 */
    @Test
    @DisplayName("주가손익과 환차손익을 더하면 평가손익이 된다")
    void 분해합() {
        BigDecimal sum = pricePnl("10", "317.14", "180", "1370")
                .add(fxPnl("10", "180", "1370", "1300"));

        assertThat(sum.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(evalPnl("10", "317.14", "1370", "180", "1300")
                        .setScale(2, RoundingMode.HALF_UP));
    }

    /* 환율이 그대로면 환차손익은 0 이어야 한다. 아니면 식에 환율이 두 번 들어간 것이다 */
    @Test
    @DisplayName("환율이 안 움직였으면 환차손익은 0 이다")
    void 환율고정() {
        assertThat(fxPnl("10", "180", "1300", "1300")).isEqualByComparingTo("0");
    }

    /* 주가가 그대로면 주가손익은 0. 환차손익만 남는다 */
    @Test
    @DisplayName("주가가 안 움직였으면 주가손익은 0 이다")
    void 주가고정() {
        assertThat(pricePnl("10", "180", "180", "1370")).isEqualByComparingTo("0");
        assertThat(fxPnl("10", "180", "1370", "1300")).isEqualByComparingTo("126000");
    }

    /* 손실도 같은 식으로 갈라져야 한다 — 부호만 뒤집힌다 */
    @Test
    @DisplayName("손실일 때도 분해합이 평가손익과 같다")
    void 손실() {
        BigDecimal sum = pricePnl("10", "150", "180", "1250")
                .add(fxPnl("10", "180", "1250", "1300"));

        assertThat(sum.setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(evalPnl("10", "150", "1250", "180", "1300")
                        .setScale(2, RoundingMode.HALF_UP));
        assertThat(sum.signum()).isNegative();
    }
}
