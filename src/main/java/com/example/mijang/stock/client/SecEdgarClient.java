package com.example.mijang.stock.client;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.config.ExternalApiProperties;
import tools.jackson.databind.JsonNode;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** SEC EDGAR 를 호출해 JSON 을 그대로 돌려주는 클라이언트다. 속도 제한 등 호출 규칙만 여기서 지킨다. */
@Slf4j
@Component
public class SecEdgarClient {

    private final RestClient dataClient;
    private final RestClient wwwClient;
    private final ExternalApiProperties.Sec config;

    /** 초당 한도를 지키기 위한 최소 호출 간격 게이트. */
    private final Object rateLock = new Object();
    private final long minIntervalNanos;
    private long nextAllowedNanos = 0L;

    public SecEdgarClient(@Qualifier("secDataClient") RestClient dataClient,
                          @Qualifier("secWwwClient") RestClient wwwClient,
                          ExternalApiProperties props) {
        this.dataClient = dataClient;
        this.wwwClient = wwwClient;
        this.config = props.sec();
        int perSecond = Math.max(1, config.requestsPerSecond());
        this.minIntervalNanos = 1_000_000_000L / perSecond;
    }

    /** 전 종목의 티커→CIK 매핑 원본을 한 번에 받는다. */
    public JsonNode companyTickers() {
        return get(wwwClient, "/files/company_tickers.json")
                .orElseThrow(() -> new BusinessException(ErrorCode.VENDOR_UNAVAILABLE));
    }

    /** 10자리 CIK 로 기업 개요와 최근 공시 목록을 받는다. */
    public JsonNode submissions(String cik) {
        return get(dataClient, "/submissions/CIK" + cik + ".json")
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_DISCLOSURE_NOT_FOUND));
    }

    /** XBRL 재무 항목 하나의 전체 시계열을 받는다. 회사가 그 태그를 안 쓰면(404) 빈 값을 돌려준다. */
    public Optional<JsonNode> companyConcept(String cik, String taxonomy, String tag) {
        return get(dataClient, "/api/xbrl/companyconcept/CIK" + cik + "/" + taxonomy + "/" + tag + ".json");
    }

    /** 회사의 XBRL 항목 전체를 받는다. 응답이 커서 companyConcept 가 빈 값일 때의 보정용으로만 쓴다. */
    public Optional<JsonNode> companyFacts(String cik) {
        return get(dataClient, "/api/xbrl/companyfacts/CIK" + cik + ".json");
    }

    private Optional<JsonNode> get(RestClient client, String path) {
        if (!config.configured()) {
            log.warn("SEC User-Agent 미설정 상태로 호출 시도: {}", path);
            throw new BusinessException(ErrorCode.VENDOR_NOT_CONFIGURED);
        }
        throttle();
        try {
            return Optional.ofNullable(client.get().uri(path).retrieve().body(JsonNode.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Forbidden e) {
            // SEC 의 403 은 대부분 User-Agent 문제이거나 초당 한도 초과 뒤의 차단이다.
            log.error("SEC 403 — User-Agent 형식 또는 호출 한도를 확인할 것. path={}", path);
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("SEC 호출 실패 path={} : {}", path, e.getMessage());
            throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
        }
    }

    /** SEC 초당 한도(10회)를 넘기면 IP 가 막히므로 인스턴스 하나에서 호출 간격을 재어 미리 막는다. */
    private void throttle() {
        long waitNanos;
        synchronized (rateLock) {
            long now = System.nanoTime();
            long start = Math.max(now, nextAllowedNanos);
            waitNanos = start - now;
            nextAllowedNanos = start + minIntervalNanos;
        }
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.VENDOR_UNAVAILABLE);
            }
        }
    }
}
