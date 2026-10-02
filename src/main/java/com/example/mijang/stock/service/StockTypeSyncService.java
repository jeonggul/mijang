package com.example.mijang.stock.service;

import com.example.mijang.stock.client.FinnhubStockClient;
import com.example.mijang.stock.mapper.StockMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Finnhub 목록으로 stocks 의 security_type·isin·asset_class 를 채운다. 넣기만 하고 지우지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockTypeSyncService {

    private final FinnhubStockClient finnhubClient;
    private final StockMapper stockMapper;

    /** 전 종목의 종류를 받아 채우고 실제로 바뀐 종목 수를 돌려준다. */
    @Transactional
    public int syncAll() {
        if (!finnhubClient.configured()) {
            log.warn("[종목 종류] Finnhub 키가 없어 건너뛴다");
            return 0;
        }
        JsonNode symbols = finnhubClient.usSymbols();
        if (symbols == null || !symbols.isArray()) {
            log.warn("[종목 종류] 목록을 받지 못했다 — 기존 값은 그대로 둔다");
            return 0;
        }

        int updated = 0;
        for (JsonNode item : symbols) {
            String symbol = item.path("symbol").asString("").trim().toUpperCase(Locale.ROOT);
            String type = item.path("type").asString("").trim();
            if (symbol.isEmpty() || type.isEmpty()) {
                continue;
            }
            String isin = item.path("isin").asString("").trim();
            updated += stockMapper.updateSecurityType(symbol, type,
                    isin.isEmpty() ? null : isin, assetClassOf(type));
        }
        log.info("[종목 종류] {}건 중 {}건 반영", symbols.size(), updated);
        return updated;
    }

    /** 벤더 종류를 STOCK/ETF 둘로 접는다. ETP 는 ETF 로 본다. */
    private String assetClassOf(String finnhubType) {
        return "ETP".equalsIgnoreCase(finnhubType) ? "ETF" : "STOCK";
    }
}
