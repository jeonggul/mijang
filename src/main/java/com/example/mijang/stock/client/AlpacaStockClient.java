package com.example.mijang.stock.client;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.ExternalApiProperties;
import com.example.mijang.config.StockProperties;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Alpaca 에서 종목 마스터·봉·체결·기업행사를 조회해 응답을 그대로 돌려주는 클라이언트다. */
@Slf4j
@Component
public class AlpacaStockClient {

    private final RestClient dataClient;
    private final RestClient tradingClient;
    private final ExternalApiProperties.Alpaca config;
    private final StockProperties stockProps;

    public AlpacaStockClient(@Qualifier("alpacaDataClient") RestClient dataClient,
                             @Qualifier("alpacaTradingClient") RestClient tradingClient,
                             ExternalApiProperties props,
                             StockProperties stockProps) {
        this.dataClient = dataClient;
        this.tradingClient = tradingClient;
        this.config = props.alpaca();
        this.stockProps = stockProps;
    }

    /** API 키가 채워져 있는지 확인한다. */
    public boolean configured() {
        return config.configured();
    }

    /** 거래 가능한 활성 전 종목을 한 번에 받는다. */
    public JsonNode assets() {
        try {
            return tradingClient.get()
                    .uri(uri -> uri.path("/v2/assets")
                            .queryParam("status", "active")
                            .queryParam("asset_class", "us_equity")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Alpaca 종목 마스터 조회 실패", e);
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
    }

    /** 여러 종목의 일봉을 기간(양끝 포함)으로 한 번에 받는다. */
    public JsonNode dailyBars(List<String> symbols, LocalDate from, LocalDate to) {
        return bars(symbols, "1Day", from.toString(), to.toString());
    }

    /** 시간대(1Min~1Month)를 지정해 설정된 기본 피드로 봉을 받는다. */
    public JsonNode bars(List<String> symbols, String timeframe, String start, String end) {
        return bars(symbols, timeframe, start, end, stockProps.getBarFeed());
    }

    /** 여러 종목의 마지막 체결을 한 번에 받는다. 실패하면 null 을 돌려준다. */
    public JsonNode latestTrades(List<String> symbols, String feed) {
        String joined = String.join(",", symbols);
        try {
            return dataClient.get()
                    .uri(uri -> uri.path("/v2/stocks/trades/latest")
                            .queryParam("symbols", joined)
                            .queryParam("feed", feed)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Alpaca 최신 체결 조회 실패 — {}건", symbols.size(), e);
            return null;
        }
    }

    /** 정분할·역분할 이벤트를 페이지 토큰(첫 호출은 null)으로 이어 받는다. */
    public JsonNode splits(List<String> symbols, LocalDate start, LocalDate end,
                           String pageToken) {
        String joined = String.join(",", symbols);
        try {
            return dataClient.get()
                    .uri(uri -> {
                        var b = uri.path("/v1/corporate-actions")
                                .queryParam("types", "forward_split,reverse_split")
                                .queryParam("symbols", joined)
                                .queryParam("start", start.toString())
                                .queryParam("end", end.toString())
                                .queryParam("limit", 1000);
                        if (pageToken != null) {
                            b = b.queryParam("page_token", pageToken);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Alpaca 분할 이벤트 조회 실패 — {}건", symbols.size(), e);
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
    }

    /** 현금 배당 이벤트를 배당락일 기준 기간으로, 페이지 토큰으로 이어 받는다. */
    public JsonNode cashDividends(List<String> symbols, LocalDate start, LocalDate end,
                                  String pageToken) {
        String joined = String.join(",", symbols);
        try {
            return dataClient.get()
                    .uri(uri -> {
                        var b = uri.path("/v1/corporate-actions")
                                .queryParam("types", "cash_dividend")
                                .queryParam("symbols", joined)
                                .queryParam("start", start.toString())
                                .queryParam("end", end.toString())
                                .queryParam("limit", 1000);
                        if (pageToken != null) {
                            b = b.queryParam("page_token", pageToken);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Alpaca 배당 이벤트 조회 실패 — {}건", symbols.size(), e);
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
    }

    /** 피드를 지정해 봉을 받는다. 무료 요금제는 최근 SIP 데이터를 거부(403)하므로 피드를 반드시 명시한다. */
    public JsonNode bars(List<String> symbols, String timeframe, String start, String end, String feed) {
        String joined = String.join(",", symbols);
        try {
            return dataClient.get()
                    .uri(uri -> uri.path("/v2/stocks/bars")
                            .queryParam("symbols", joined)
                            .queryParam("timeframe", timeframe)
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("adjustment", "split")
                            .queryParam("feed", feed)
                            .queryParam("limit", 10000)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Alpaca 봉 조회 실패 — {}건 {}", symbols.size(), timeframe, e);
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
    }
}
