package com.example.mijang.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.news.dto.NewsItemResponse;
import com.example.mijang.news.mapper.NewsMapper;
import com.example.mijang.news.mapper.NewsStockMapper;
import com.example.mijang.news.service.NewsService;
import com.example.mijang.news.service.StockNewsFetchService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 뉴스 수집.
 *
 * <p>DB 도 벤더도 부르지 않는다. 여기서 보려는 것은 셋이다 —
 * <b>같은 기사를 두 번 쌓지 않는가</b>, <b>표 길이를 넘는 값에 저장이 깨지지 않는가</b>,
 * <b>한 종목이 실패해도 배치가 살아남는가</b>.
 */
class NewsCollectTest {

    private static final Instant AT = Instant.parse("2026-08-29T01:00:00Z");

    /** 표 대신 메모리에 담는 가짜. vendor_id 로 중복을 막는 것까지 흉내 낸다. */
    private static class News implements NewsMapper {
        final Map<String, Long> rows = new LinkedHashMap<>();
        final List<String> headlines = new ArrayList<>();
        long seq = 1;

        @Override public long countBySymbol(String symbol) { return rows.size(); }

        @Override public int insertIgnore(String vendorId, String headline, String summary,
                                          String url, String source, LocalDateTime publishedAt) {
            if (rows.containsKey(vendorId)) {
                return 0;
            }
            rows.put(vendorId, seq++);
            headlines.add(headline);
            return 1;
        }

        @Override public Long findIdByVendorId(String vendorId) { return rows.get(vendorId); }

        @Override public List<String> findSymbolsOfInterest() { return List.of("AAPL"); }
    }

    private static class Links implements NewsStockMapper {
        final List<String> linked = new ArrayList<>();

        @Override public int link(Long newsId, String symbol) {
            linked.add(newsId + ":" + symbol);
            return 1;
        }

        @Override public int linkByVendorId(String vendorId, String symbol) {
            linked.add(vendorId + ":" + symbol);
            return 1;
        }
    }

    /** 정해 둔 기사를 돌려주거나, 터지게 만들 수 있는 가짜 수집기. */
    private static class Fetch extends StockNewsFetchService {
        List<NewsItemResponse> items = List.of();
        boolean explode;

        Fetch() { super(null, null, null); }

        @Override public List<NewsItemResponse> news(String symbol) {
            if (explode) {
                throw new IllegalStateException("벤더 429");
            }
            return items;
        }
    }

    private static NewsItemResponse item(String headline, String url) {
        return new NewsItemResponse(headline, "요약", "Reuters", AT, url, null);
    }

    @Nested
    @DisplayName("수집")
    class 수집 {

        @Test
        @DisplayName("새 기사 수를 돌려주고 종목에 잇는다")
        void 저장() {
            News news = new News();
            Links links = new Links();
            Fetch fetch = new Fetch();
            fetch.items = List.of(item("실적 발표", "https://x.com/1"),
                                  item("신제품 공개", "https://x.com/2"));

            int saved = new NewsService(news, links, fetch).collect("AAPL");

            assertThat(saved).isEqualTo(2);
            assertThat(links.linked).hasSize(2);
        }

        /* 매시간 도는데 같은 기사가 매번 쌓이면 목록이 도배된다 */
        @Test
        @DisplayName("같은 기사를 다시 받아도 새로 세지 않는다")
        void 중복() {
            News news = new News();
            Fetch fetch = new Fetch();
            fetch.items = List.of(item("실적 발표", "https://x.com/1"));
            NewsService service = new NewsService(news, new Links(), fetch);

            assertThat(service.collect("AAPL")).isEqualTo(1);
            assertThat(service.collect("AAPL")).isEqualTo(0);
            assertThat(news.rows).hasSize(1);
        }

        /* URL 이 다르면 다른 기사다. 해시가 겹쳐 한 건으로 묶이면 안 된다 */
        @Test
        @DisplayName("URL 이 다르면 각각 저장된다")
        void 서로다른기사() {
            News news = new News();
            Fetch fetch = new Fetch();
            fetch.items = List.of(item("같은 제목", "https://x.com/1"),
                                  item("같은 제목", "https://x.com/2"));

            assertThat(new NewsService(news, new Links(), fetch).collect("AAPL")).isEqualTo(2);
        }

        @Test
        @DisplayName("제목이 500자를 넘어도 저장된다")
        void 긴제목() {
            News news = new News();
            Fetch fetch = new Fetch();
            fetch.items = List.of(item("가".repeat(900), "https://x.com/1"));

            new NewsService(news, new Links(), fetch).collect("AAPL");

            assertThat(news.headlines.get(0)).hasSize(500);
        }

        @Test
        @DisplayName("링크나 제목이 없는 기사는 건너뛴다")
        void 빈값() {
            News news = new News();
            Fetch fetch = new Fetch();
            fetch.items = new ArrayList<>(List.of(
                    new NewsItemResponse(null, "요약", "Reuters", AT, "https://x.com/1", null),
                    new NewsItemResponse("제목", "요약", "Reuters", AT, null, null)));

            assertThat(new NewsService(news, new Links(), fetch).collect("AAPL")).isZero();
        }

        /* 종목 하나가 실패했다고 배치 전체가 멈추면 나머지 수십 종목이 밀린다 */
        @Test
        @DisplayName("벤더가 죽어도 예외를 내지 않는다")
        void 벤더장애() {
            Fetch fetch = new Fetch();
            fetch.explode = true;

            assertThat(new NewsService(new News(), new Links(), fetch).collect("AAPL")).isZero();
        }
    }
}
