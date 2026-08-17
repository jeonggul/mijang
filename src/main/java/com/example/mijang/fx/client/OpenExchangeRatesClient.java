/*
 * OpenExchangeRatesClient — Open Exchange Rates 에서 환율을 받아 오는 곳
 *
 * 이 파일이 하는 일
 *   무료 플랜으로 원달러 환율 하나를 받는다.
 *
 *   무료 플랜의 제약 셋을 그대로 안고 간다.
 *     · 월 1,000회 — 그래서 부르는 것은 1시간마다 도는 배치 하나뿐이다.
 *       사용자가 몰려도 벤더 쪽 호출량은 월 720회로 고정이다.
 *     · USD 기준 고정 — 우리가 필요한 것이 USD→KRW 라 마침 맞는다.
 *     · symbols 필터 미지원 — 전체를 받아 KRW 만 꺼낸다. 172통화에 3.7KB 라 아낄 것이 없다.
 *
 *   App ID 는 헤더가 아니라 질의 문자열로 보낸다. 벤더가 그 방식만 받는다.
 */
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

    /**
     * 최신 환율.
     *
     * <p><b>{@code symbols} 파라미터를 쓰지 않는다.</b> 붙이면 실제로는 걸러져 오는데
     * 무료 플랜의 기능 목록에는 {@code symbols: false} 로 되어 있다. 공식적으로 꺼진 기능이라
     * 언제 막혀도 이상하지 않다(2.5).
     *
     * <p>실패해도 예외를 던지지 않는다. 벤더가 잠깐 죽었다고 배치가 멈추면, 다음 정시까지
     * 아무도 다시 시도하지 않는다.
     */
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

    /**
     * 응답에서 원달러 환율을 꺼낸다.
     *
     * <p>{@code timestamp} 는 벤더가 값을 만든 시각이다. 우리가 받은 시각과 다르고,
     * 정시에 맞춰 온다(2.4). 대체 여부 판정이 이 값을 보므로 그대로 들고 간다.
     */
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
