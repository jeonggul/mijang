package com.example.mijang.market.client;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Alpaca 에서 미국 시장 거래일 달력을 받아 온다. 휴장일은 행이 아예 오지 않는다. */
@Slf4j
@Component
public class AlpacaCalendarClient {

    private final RestClient tradingClient;

    public AlpacaCalendarClient(@Qualifier("alpacaTradingClient") RestClient tradingClient) {
        this.tradingClient = tradingClient;
    }

    /** 기간의 거래일을 받는다. 실패하면 null 이며 빈 배열과 구분해야 한다. */
    public JsonNode calendar(LocalDate from, LocalDate to) {
        try {
            return tradingClient.get()
                    .uri(uri -> uri.path("/v2/calendar")
                            .queryParam("start", from.toString())
                            .queryParam("end", to.toString())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Alpaca 거래일 달력 조회 실패 — {} ~ {}", from, to, e);
            return null;
        }
    }
}
