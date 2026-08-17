package com.example.mijang.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.news.dto.NewsItemResponse;
import com.example.mijang.news.service.NewsRanker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 뉴스 고르기.
 *
 * <p>받은 것을 그대로 뿌리면 기업이 뭘 했는지는 안 보이고 주식 얘기만 나온다.
 * 실제 응답에서 본 모양을 그대로 넣어 확인한다.
 */
class NewsRankerTest {

    private final NewsRanker ranker = new NewsRanker();

    private NewsItemResponse item(String headline, String source, String summary, int minutesAgo) {
        return new NewsItemResponse(headline, summary, source,
                Instant.now().minusSeconds(minutesAgo * 60L), "https://example.com/x", null);
    }

    @Test
    @DisplayName("시황만 나열하는 매체는 뺀다")
    void 잡음매체() {
        var out = ranker.rank(List.of(
                item("What's going on in today's session: S&P500 most active stocks", "ChartMill", "", 10),
                item("Apple opens new manufacturing facility in Texas", "CNBC", "", 20)),
                "Apple Inc. Common Stock", "AAPL");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).source()).isEqualTo("CNBC");
    }

    @Test
    @DisplayName("다른 회사 얘기는 뺀다")
    void 무관한기사() {
        var out = ranker.rank(List.of(
                item("Broadcom: XPU Growth Trajectory Underestimated", "SeekingAlpha", "", 10),
                item("Apple opens Texas manufacturing site", "CNBC", "", 20)),
                "Apple Inc. Common Stock", "AAPL");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).headline()).contains("Apple");
    }

    @Test
    @DisplayName("티커는 낱말로 본다 — AAL 이 small 에 걸리면 안 된다")
    void 티커부분일치() {
        var out = ranker.rank(List.of(
                item("Small caps lead the market higher", "CNBC", "no company here", 10)),
                "American Airlines Group Inc.", "AAL");
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("주식 얘기는 버리지 않고 뒤로 민다")
    void 주식얘기는뒤로() {
        var out = ranker.rank(List.of(
                item("Apple Stock: 64% of Viewers Have Owned It", "Benzinga", "", 5),
                item("Apple opens new manufacturing facility in Texas", "Yahoo", "", 60)),
                "Apple Inc. Common Stock", "AAPL");
        assertThat(out).hasSize(2);
        assertThat(out.get(0).headline()).contains("manufacturing");
        assertThat(out.get(1).headline()).contains("Stock");
    }

    @Test
    @DisplayName("변화형도 주식 얘기로 본다 — Rallies 를 놓치면 안 된다")
    void 변화형() {
        var out = ranker.rank(List.of(
                item("Nvidia Rallies 7% as Goldman Warns", "Yahoo", "", 5),
                item("Nvidia opens new AI research center", "Yahoo", "", 60)),
                "NVIDIA Corporation", "NVDA");
        assertThat(out.get(0).headline()).contains("research center");
    }

    @Test
    @DisplayName("거의 같은 제목은 하나만 남긴다")
    void 같은기사() {
        var out = ranker.rank(List.of(
                item("Apple opens new manufacturing facility in Texas", "CNBC", "", 10),
                item("Apple opens a new manufacturing facility in Texas today", "Yahoo", "", 20)),
                "Apple Inc. Common Stock", "AAPL");
        assertThat(out).hasSize(1);
    }

    @Test
    @DisplayName("낱말이 하나뿐인 제목이 남을 통째로 삼키지 않는다")
    void 짧은제목() {
        /* "Apple Q3" 는 걸러지고 나면 {apple} 하나만 남는다.
           짧은 쪽으로 나누면 apple 이 든 모든 제목과 1.0 이 되어 전부 중복으로 지워졌다 */
        var out = ranker.rank(List.of(
                item("Apple Q3", "CNBC", "", 10),
                item("Apple opens new manufacturing facility in Texas", "Reuters", "", 20),
                item("Apple hires former Tesla battery lead", "Bloomberg", "", 30)),
                "Apple Inc. Common Stock", "AAPL");
        assertThat(out).hasSize(3);
    }

    @Test
    @DisplayName("낱말이 충분하면 여전히 같은 기사로 묶는다")
    void 긴제목은그대로묶임() {
        var out = ranker.rank(List.of(
                item("Apple opens new manufacturing facility in Texas", "CNBC", "", 10),
                item("Apple opens new manufacturing facility in Texas this week", "Yahoo", "", 20)),
                "Apple Inc. Common Stock", "AAPL");
        assertThat(out).hasSize(1);
    }

    @Test
    @DisplayName("앞 여섯 건은 한 매체가 둘까지만 — 뒤는 상한이 없다")
    void 매체몰림() {
        /* 제목이 서로 달라야 한다. 비슷하면 중복 제거가 먼저 걸러 매체 상한을 확인할 수 없다 */
        String[] topics = {"launches wearable", "expands India factory", "hires chip designer",
                           "settles patent case", "opens Seoul store", "renews music deal",
                           "ships satellite feature", "buys mapping startup",
                           "cuts packaging waste", "adds Korean subtitles"};
        var many = new ArrayList<NewsItemResponse>();
        for (int i = 0; i < topics.length; i++) {
            many.add(item("Apple " + topics[i], "Benzinga", "", i));
        }
        many.add(item("Apple opens Texas plant", "CNBC", "", 99));

        var out = ranker.rank(many, "Apple Inc. Common Stock", "AAPL");
        /* 매체가 둘뿐이라 앞자리를 상한 2 로는 못 채운다. 상한이 풀려 자리가 다 찬다 */
        assertThat(out).hasSizeGreaterThan(6);
        assertThat(out.stream().limit(6).map(NewsItemResponse::source))
                .contains("CNBC");                       // 다른 매체가 앞자리에 들어간다
        assertThat(out.subList(0, 6)).hasSize(6);        // 자리가 비지 않는다
    }

    @Test
    @DisplayName("매체가 넉넉하면 앞자리에 한 매체가 둘까지만")
    void 매체가여럿일때() {
        var many = new ArrayList<NewsItemResponse>();
        String[] topics = {"launches wearable", "expands India factory", "hires chip designer",
                           "settles patent case", "opens Seoul store", "renews music deal"};
        for (int i = 0; i < topics.length; i++) {
            many.add(item("Apple " + topics[i], "Benzinga", "", i));
        }
        many.add(item("Apple opens Texas plant", "CNBC", "", 50));
        many.add(item("Apple adds satellite messaging", "Reuters", "", 51));
        many.add(item("Apple names new retail chief", "Bloomberg", "", 52));
        many.add(item("Apple unveils repair program", "Yahoo", "", 53));

        var out = ranker.rank(many, "Apple Inc. Common Stock", "AAPL");
        long inHead = out.stream().limit(6).filter(x -> "Benzinga".equals(x.source())).count();
        assertThat(inHead).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("ETF 처럼 이름이 길어도 회사 낱말로 찾는다")
    void 긴이름() {
        var out = ranker.rank(List.of(
                item("iShares Semiconductor ETF sees record inflows", "CNBC", "", 10)),
                "iShares Semiconductor ETF", "SOXX");
        assertThat(out).hasSize(1);
    }
}
