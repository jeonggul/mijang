package com.example.mijang.stock.service;

import com.example.mijang.stock.client.AlpacaStockClient;
import com.example.mijang.stock.mapper.StockMapper;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 하루 한 번 전 종목을 받아 stocks 마스터를 동기화한다. */
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

    /** 전 종목을 받아 upsert 하고, 이번 회차에 안 보인 종목은 비활성으로 내린다. */
    @Transactional
    public int syncAll() {
        if (!alpacaClient.configured()) {
            log.warn("[종목 동기화] Alpaca 키가 없어 건너뛴다");
            return 0;
        }

        // 기준 시각은 JVM 이 아니라 DB 시계다. synced_at 을 찍는 시계와 같아야 오판이 없다
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

    /** 종목명을 컬럼 길이에 맞게 자른다. 자르지 않으면 한 건 때문에 동기화 전체가 되돌아간다. */
    private String clipName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= NAME_MAX ? name : name.substring(0, NAME_MAX);
    }

    /** 자산 종류를 판별한다. Alpaca 가 ETF 여부를 주지 않아 종목명으로 추정하며 부정확할 수 있다. */
    private String assetClassOf(JsonNode asset) {
        String name = asset.path("name").asText("").toUpperCase(java.util.Locale.ROOT);
        // 단어 경계를 봐서 "ETFS" 같은 회사명에 걸리지 않게 한다
        return ETF_NAME.matcher(name).find() ? "ETF" : "STOCK";
    }
}
