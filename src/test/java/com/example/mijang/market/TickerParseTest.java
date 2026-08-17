package com.example.mijang.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.market.domain.Tickers;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 티커 모양 검사.
 *
 * <p>이 값은 벤더에 보내는 제어 프레임에 실리고, 그 경로는 로그인 없이 부를 수 있다.
 * 모양이 아닌 것이 새어 들어가면 프레임 구조를 바깥에서 정하게 된다.
 *
 * <p>실제 코드({@link Tickers})를 그대로 부른다. 규칙을 여기에 옮겨 적으면 코드가
 * 바뀌어도 시험은 통과해 버린다.
 */
class TickerParseTest {

    private boolean ok(String s) {
        return Tickers.valid(s);
    }

    @Test
    @DisplayName("실제 티커는 통과한다")
    void 정상() {
        assertThat(ok("A")).isTrue();
        assertThat(ok("AAPL")).isTrue();
        assertThat(ok("BRK.B")).isTrue();
        assertThat(ok("KIM.PRL")).isTrue();
        assertThat(ok("BBBY.WS")).isTrue();
        assertThat(ok("TQQQ")).isTrue();
    }

    @Test
    @DisplayName("프레임을 깨뜨리는 글자는 막힌다")
    void 주입시도() {
        assertThat(ok("A\"],\"X\":[\"*")).isFalse();   // JSON 구조 끼워넣기
        assertThat(ok("*")).isFalse();                  // 전 종목 구독
        assertThat(ok("A\\")).isFalse();
        assertThat(ok("{\"action\":\"auth\"}")).isFalse();
        assertThat(ok("A B")).isFalse();
        assertThat(ok("")).isFalse();
        assertThat(ok(null)).isFalse();
    }

    @Test
    @DisplayName("너무 길거나 소문자로 시작하면 막힌다")
    void 모양이아닌것() {
        assertThat(ok("ABCDEFGHIJK")).isFalse();        // 11자
        assertThat(ok(".AAPL")).isFalse();
        assertThat(ok("1AAPL")).isFalse();
    }

    @Test
    @DisplayName("쉼표 목록에서 모양이 맞는 것만 남는다 — 스트림 입구")
    void 목록읽기() {
        assertThat(Tickers.parseCsv("aapl, BRK.B ,*,A\"],\"X\":[\"*,ABCDEFGHIJK,"))
                .containsExactlyInAnyOrder("AAPL", "BRK.B");
        assertThat(Tickers.parseCsv("")).isEmpty();
        assertThat(Tickers.parseCsv(null)).isEmpty();
    }

    @Test
    @DisplayName("구독 갱신 입구도 같은 규칙을 쓴다")
    void 구독갱신도같은규칙() {
        assertThat(Tickers.clean(Arrays.asList("msft", "*", null, " nvda ", "A B")))
                .containsExactlyInAnyOrder("MSFT", "NVDA");
        assertThat(Tickers.clean(null)).isEmpty();
    }
}
