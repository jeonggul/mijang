package com.example.mijang.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.stock.domain.ChartRange;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실시간 구간의 폭.
 *
 * <p>하루 치를 다 그리면 봉이 800개가 넘어 지금 무슨 일이 벌어지는지가 묻힌다.
 * 하루 전체는 1일 버튼이 맡는다.
 */
class LiveRangeTest {

    @Test
    @DisplayName("실시간은 다섯 시간, 1일은 하루")
    void 구간폭() {
        assertThat(ChartRange.LIVE.lookback()).isEqualTo(Duration.ofHours(5));
        assertThat(ChartRange.ONE_DAY.lookback()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    @DisplayName("실시간과 1일은 같은 분봉을 쓴다 — 폭만 다르다")
    void 같은시간대() {
        assertThat(ChartRange.LIVE.timeframe()).isEqualTo("1Min");
        assertThat(ChartRange.ONE_DAY.timeframe()).isEqualTo("1Min");
        assertThat(ChartRange.LIVE.intraday()).isTrue();
    }

    @Test
    @DisplayName("분봉은 저장하지 않는다 — 종목당 하루 800건이 넘는다")
    void 저장안함() {
        assertThat(ChartRange.LIVE.stored()).isFalse();
        assertThat(ChartRange.ONE_DAY.stored()).isFalse();
        assertThat(ChartRange.THREE_MONTH.stored()).isTrue();
    }
}
