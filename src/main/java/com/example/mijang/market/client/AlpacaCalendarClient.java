/*
 * AlpacaCalendarClient — 거래일 달력을 받아 오는 곳
 *
 * 이 파일이 하는 일
 *   Alpaca 에서 미국 시장의 개장·마감 시각을 날짜별로 받는다.
 *
 *   휴장일은 <b>행이 아예 오지 않는다.</b> 조기폐장일은 close 가 13:00 으로 온다.
 *   우리가 직접 공휴일 목록을 관리하지 않아도 되는 이유다 — 거래소 일정이 바뀌면
 *   벤더 응답이 먼저 바뀐다.
 *
 *   시세와 같은 벤더지만 창구가 다르다. 이쪽은 거래(trading) 주소를 쓴다.
 */
package com.example.mijang.market.client;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class AlpacaCalendarClient {

    private final RestClient tradingClient;

    public AlpacaCalendarClient(@Qualifier("alpacaTradingClient") RestClient tradingClient) {
        this.tradingClient = tradingClient;
    }

    /**
     * 기간의 거래일을 받는다.
     *
     * @return 실패하면 null. 빈 배열과 구분해야 한다 — 빈 배열로 착각하면
     *         "그 기간에 장이 하루도 안 열린다" 로 읽어 화면이 통째로 멈춘다
     */
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
