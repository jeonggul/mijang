package com.example.mijang.fx.client;

import com.example.mijang.config.FxProperties;
import com.example.mijang.fx.domain.FxQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Open Exchange Rates 무료 플랜으로 원달러 환율을 받아 온다. */
@Slf4j
@Component
public class OpenExchangeRatesClient implements FxRateClient {

    /** 받아올 통화. 무료 플랜이 USD 기준이라 이 키가 곧 1 USD 당 원화다. */
    private static final String TARGET = "KRW";

    private final RestClient fxClient;
    private final FxProperties props;

    public OpenExchangeRatesClient(@Qualifier("fxClient") RestClient fxClient, FxProperties props) {
        this.fxClient = fxClient;
        this.props = props;
    }

    @Override
    public boolean configured() {
        return props.getAppId() != null && !props.getAppId().isBlank();
    }

    /** 최신 환율을 받아 온다. 실패해도 예외 대신 빈 값을 돌려준다. */
    @Override
    public Optional<FxQuote> latest() {
        if (!configured()) {
            log.warn("[환율] App ID 가 없어 건너뛴다");
            return Optional.empty();
        }
        JsonNode body;
        try {
            body = fxClient.get()
                    .uri(uri -> uri.path("/latest.json")
                            .queryParam("app_id", props.getAppId())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("[환율] 벤더 호출 실패", e);
            return Optional.empty();
        }
        return parse(body);
    }

    /** 응답에서 원달러 환율과 벤더 생성 시각을 꺼낸다. */
    private Optional<FxQuote> parse(JsonNode body) {
        if (body == null) {
            return Optional.empty();
        }
        JsonNode rate = body.path("rates").get(TARGET);
        JsonNode timestamp = body.get("timestamp");
        if (rate == null || rate.isNull() || timestamp == null || timestamp.isNull()) {
            log.warn("[환율] 응답에 {} 또는 timestamp 가 없다", TARGET);
            return Optional.empty();
        }
        BigDecimal value = new BigDecimal(rate.asString());
        if (value.signum() <= 0) {
            log.warn("[환율] 값이 0 이하다 — {}", value);
            return Optional.empty();
        }
        return Optional.of(new FxQuote("USD", value, Instant.ofEpochSecond(timestamp.asLong())));
    }
}
