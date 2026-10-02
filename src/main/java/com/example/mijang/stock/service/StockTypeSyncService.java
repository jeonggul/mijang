/*
 * StockTypeSyncService — 종목 종류를 채우는 곳
 *
 * 이 파일이 하는 일
 *   Finnhub 종목 목록을 받아 stocks 의 security_type·isin 을 채우고,
 *   거기서 asset_class(STOCK/ETF)를 다시 정한다.
 *
 *   왜 필요한가 — Alpaca 는 종류를 안 알려준다. 그래서 지금까지 종목명에 "ETF" 라는
 *   글자가 있는지로 추측해 왔는데, "ProShares UltraPro QQQ"(TQQQ) 처럼 이름에 그 글자가
 *   없는 ETF 가 많다. 실측으로 <b>446건</b>이 STOCK 으로 잘못 들어가 있었다.
 *
 *   받은 것을 넣기만 하고 지우지 않는다. Finnhub 가 어느 날 한 종목을 빠뜨려도
 *   이미 정해진 종류가 사라지면 안 된다.
 */
package com.example.mijang.stock.service;

import com.example.mijang.stock.client.FinnhubStockClient;
import com.example.mijang.stock.mapper.StockMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTypeSyncService {

    private final FinnhubStockClient finnhubClient;
    private final StockMapper stockMapper;

    /**
     * 전 종목의 종류를 받아 채운다.
     *
     * @return 실제로 바뀐 종목 수
     */
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

    /**
     * 벤더의 종류를 우리 자산군 둘로 접는다.
     *
     * <p>화면의 필터가 ETF/개별주 둘로만 나뉘어 있어서다. 세부 종류는 security_type 에
     * 원문 그대로 남으므로, 나중에 "리츠만 보기" 같은 것이 필요해지면 거기서 꺼내 쓴다.
     *
     * <p>{@code ETP} 는 Exchange Traded Product 다. ETF 와 ETN 을 아우르는 말이라
     * 우리 기준으로는 둘 다 ETF 칸에 넣는다 — 사용자가 찾을 때 구분하지 않는다.
     */
    private String assetClassOf(String finnhubType) {
        return "ETP".equalsIgnoreCase(finnhubType) ? "ETF" : "STOCK";
    }
}
