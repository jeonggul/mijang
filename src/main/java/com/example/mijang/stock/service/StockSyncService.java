package com.example.mijang.stock.service;

import com.example.mijang.stock.client.AlpacaStockClient;
import com.example.mijang.stock.mapper.StockMapper;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 마스터 동기화. 개발명세서(API) ADMIN-01 · 외부 데이터 출처 3.1
 *
 * <p>하루 한 번 전 종목을 받아 {@code stocks} 에 넣는다. 검색은 이 표만 본다(2.1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSyncService {

    /** 종목명에서 ETF 를 알아보는 패턴. 단어로 떨어질 때만 잡는다. */
    private static final java.util.regex.Pattern ETF_NAME =
            java.util.regex.Pattern.compile("\\bETF\\b");

    /** stocks.name 의 컬럼 길이. 이보다 긴 이름은 잘라 넣는다. */
    private static final int NAME_MAX = 200;

    private final AlpacaStockClient alpacaClient;
    private final StockMapper stockMapper;

    /**
     * 전 종목을 받아 upsert 하고, 이번에 안 보인 종목은 비활성으로 내린다.
     *
     * <p>시작 시각을 먼저 잡아 두는 것이 요점이다. upsert 가 {@code synced_at} 을 지금 시각으로
     * 갱신하므로, <b>시작 시각보다 오래된 행 = 이번 회차에 보이지 않은 종목</b>이 된다.
     * 별도의 목록 비교 없이 시각 하나로 판정이 끝난다.
     *
     * <p><b>그 시작 시각은 DB 에서 받아 온다.</b> {@code synced_at} 을 찍는 것이 DB 의
     * {@code CURRENT_TIMESTAMP(3)} 이라, 기준을 자바에서 만들면 서로 다른 시계를 견주게 된다.
     * DB 가 UTC 로, 서버가 KST 로 도는 흔한 배치에서는 방금 넣은 행이 9시간 뒤처져 보여
     * <b>13,000건 전부가 "벤더 목록에서 제외됨" 으로 내려간다.</b> 표준시가 같더라도 두 시계가
     * 1초만 어긋나면 같은 일이 난다.
     *
     * <p>전체를 한 트랜잭션으로 묶는다. 중간에 실패했는데 절반만 반영되면
     * 나머지 절반이 통째로 비활성으로 내려간다.
     *
     * @return 반영된 종목 수. 벤더 키가 없으면 0
     */
    @Transactional
    public int syncAll() {
        if (!alpacaClient.configured()) {
            log.warn("[종목 동기화] Alpaca 키가 없어 건너뛴다");
            return 0;
        }

        // JVM 이 아니라 DB 의 시계다. 이유는 위 주석
        LocalDateTime startedAt = stockMapper.now();
        JsonNode assets = alpacaClient.assets();
        if (assets == null || !assets.isArray()) {
            log.warn("[종목 동기화] 응답이 배열이 아니다. 건너뛴다");
            return 0;
        }

        int saved = 0;
        for (JsonNode asset : assets) {
            // tradable=false 는 상장은 돼 있으나 주문을 받지 않는 종목이다. 기록 대상이 아니다
            if (!asset.path("tradable").asBoolean(false)) {
                continue;
            }
            stockMapper.upsert(
                    asset.path("symbol").asText(),
                    clipName(asset.path("name").asText()),
                    asset.path("exchange").asText(),
                    assetClassOf(asset),
                    asset.path("fractionable").asBoolean(false));
            saved++;
        }

        int deactivated = stockMapper.deactivateNotSyncedSince(startedAt);
        log.info("[종목 동기화] 반영 {}건, 비활성 전환 {}건", saved, deactivated);
        return saved;
    }

    /**
     * 종목명을 컬럼 길이에 맞게 자른다.
     *
     * <p>{@code stocks.name} 은 VARCHAR(200) 인데 실제 응답에 그보다 긴 이름이 있다.
     * 우선주 예탁증서처럼 법적 명칭이 통째로 들어오는 경우다(실측 최대 229자).
     *
     * <p><b>자르지 않으면 그 한 줄 때문에 동기화 전체가 실패한다.</b> 한 트랜잭션이라
     * 13,000건이 통째로 되돌아간다. 종목 하나의 이름 끝부분보다 나머지 전부가 중요하다.
     *
     * <p>잘린 이름도 검색에는 문제가 없다. 전방 일치라 앞부분만 쓰기 때문이다(2.3).
     */
    private String clipName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= NAME_MAX ? name : name.substring(0, NAME_MAX);
    }

    /**
     * 자산 종류를 판별한다.
     *
     * <p><b>Alpaca 는 ETF 여부를 알려 주지 않는다.</b> {@code class} 는 주식·ETF 모두
     * {@code us_equity} 이고 {@code attributes} 에는 거래 속성만 들어 있다(실제 응답 확인).
     * 그래서 <b>종목명으로 판별</b>한다.
     *
     * <p>정확한 분류는 아니다. 이름에 ETF 를 적지 않는 상품은 놓치고, 회사명에 우연히
     * 그 단어가 들어가면 잘못 잡는다. 정확히 하려면 FMP 같은 보조 벤더가 필요하고
     * (외부 데이터 출처 3.1의 "보조"), 그것은 이 범위 밖이다.
     *
     * <p>그럼에도 이름 판별을 쓰는 이유 — 전부 STOCK 으로 두면 {@code SEARCH-04}
     * ETF 검색이 아무것도 돌려주지 못한다. 불완전해도 있는 편이 낫다.
     */
    private String assetClassOf(JsonNode asset) {
        String name = asset.path("name").asText("").toUpperCase(java.util.Locale.ROOT);
        // 단어 경계를 본다. "ETFS" 같은 회사명에 걸리지 않게 앞뒤를 확인한다
        return ETF_NAME.matcher(name).find() ? "ETF" : "STOCK";
    }
}
