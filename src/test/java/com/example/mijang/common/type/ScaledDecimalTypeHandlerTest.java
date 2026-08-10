package com.example.mijang.common.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * scale 고정 규칙이 [[미장-DB명세서]] 4.1 과 어긋나지 않는지 잠가 둔다.
 *
 * <p>여기가 틀리면 손익이 틀린다. 조용히 깨지는 종류라 테스트로 못 박는다.
 */
class ScaledDecimalTypeHandlerTest {

    @Test
    @DisplayName("수량은 6자리로 내린다 — 없는 주식을 만들지 않기 위해")
    void quantityTruncatesToSixPlaces() {
        var handler = new QuantityTypeHandler();

        assertThat(handler.scale()).isEqualTo(6);
        // 7자리째가 9여도 올리지 않는다
        assertThat(handler.normalize(new BigDecimal("1.9999999")))
                .isEqualByComparingTo("1.999999");
        assertThat(handler.normalize(new BigDecimal("12.5"))).isEqualByComparingTo("12.500000");
    }

    @Test
    @DisplayName("달러 단가는 4자리 — 센트로 깎으면 체결가가 바뀐다")
    void usdAmountKeepsFourPlaces() {
        var handler = new UsdAmountTypeHandler();

        assertThat(handler.scale()).isEqualTo(4);
        assertThat(handler.normalize(new BigDecimal("196.4012")))
                .isEqualByComparingTo("196.4012");
        assertThat(handler.normalize(new BigDecimal("196.40125")))
                .isEqualByComparingTo("196.4013");
    }

    @Test
    @DisplayName("원화 금액은 2자리 — 원 단위 절사는 화면의 몫이다")
    void krwAmountKeepsTwoPlaces() {
        var handler = new KrwAmountTypeHandler();

        assertThat(handler.scale()).isEqualTo(2);
        assertThat(handler.normalize(new BigDecimal("276540.567")))
                .isEqualByComparingTo("276540.57");
    }

    @Test
    @DisplayName("환율은 4자리 — 환차손익의 입력값이다")
    void fxRateKeepsFourPlaces() {
        var handler = new FxRateTypeHandler();

        assertThat(handler.scale()).isEqualTo(4);
        assertThat(handler.normalize(new BigDecimal("1408.4")))
                .isEqualByComparingTo("1408.4000");
    }

    @Test
    @DisplayName("비율은 4자리 소수 — 0.0408 이 +4.08%")
    void ratioKeepsFourPlaces() {
        var handler = new RatioTypeHandler();

        assertThat(handler.scale()).isEqualTo(4);
        assertThat(handler.normalize(new BigDecimal("0.040812")))
                .isEqualByComparingTo("0.0408");
    }

    @Test
    @DisplayName("null 은 그대로 통과한다 — target_price 처럼 NULL 허용 컬럼이 있다")
    void nullPassesThrough() {
        assertThat(new UsdAmountTypeHandler().normalize(null)).isNull();
    }

    @Test
    @DisplayName("나눗셈으로 생긴 긴 소수도 컬럼 scale 로 맞춰진다")
    void longDivisionResultIsNormalized() {
        // 이동평균 단가 = 총액 / 수량 처럼 scale 이 제멋대로인 값
        BigDecimal avgPrice = new BigDecimal("2500").divide(new BigDecimal("13.65"), 12,
                java.math.RoundingMode.HALF_UP);

        assertThat(avgPrice.scale()).isEqualTo(12);
        assertThat(new UsdAmountTypeHandler().normalize(avgPrice).scale()).isEqualTo(4);
    }
}
