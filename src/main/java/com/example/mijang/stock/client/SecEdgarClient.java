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

/**
 * SEC EDGAR 원문 호출 담당.
 *
 * <p>개발명세서(MVC) · 종목 · client
 *
 * <p>이 클래스는 JSON 을 그대로 돌려주고 해석은 하지 않는다. 호출 규칙(속도 제한, CIK 자리수,
 * 없는 항목 처리)만 여기서 지킨다.
 */
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

    /**
     * 티커→CIK 매핑 원본. 전 종목이 한 번에 들어 있다.
     *
     * <p>형태: {@code {"0":{"cik_str":320193,"ticker":"AAPL","title":"Apple Inc."}, ...}}
     */
    public JsonNode companyTickers() {
        return get(wwwClient, "/files/company_tickers.json")
                .orElseThrow(() -> new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
    }

    /**
     * 기업 개요 + 최근 공시 목록.
     *
     * @param cik 10자리로 0을 채운 CIK (예: {@code 0000320193})
     */
    public JsonNode submissions(String cik) {
        return get(dataClient, "/submissions/CIK" + cik + ".json")
                .orElseThrow(() -> new BusinessException(ErrorCode.DISCLOSURE_NOT_FOUND));
    }

    /**
     * XBRL 재무 항목 하나의 전체 시계열.
     *
     * <p>같은 개념이라도 회사마다 쓰는 태그가 달라서, 없는 태그면 SEC 가 404 를 준다. 그건 오류가
     * 아니라 "이 회사는 그 태그를 안 쓴다"는 뜻이라 빈 값으로 돌려주고 호출부가 다음 후보로 넘어간다.
     */
    public Optional<JsonNode> companyConcept(String cik, String taxonomy, String tag) {
        return get(dataClient, "/api/xbrl/companyconcept/CIK" + cik + "/" + taxonomy + "/" + tag + ".json");
    }

    /**
     * 그 회사의 XBRL 항목 전체.
     *
     * <p>응답이 크다(애플 기준 약 3.8MB). 평소에는 {@link #companyConcept} 로 필요한 태그만 집어오고,
     * 이건 companyconcept 가 빈 값을 줄 때의 보정용으로만 쓴다. 코카콜라처럼 companyfacts 에는
     * 값이 있는데 companyconcept 는 빈 배열을 주는 조합이 실제로 있다.
     */
    public Optional<JsonNode> companyFacts(String cik) {
        return get(dataClient, "/api/xbrl/companyfacts/CIK" + cik + ".json");
    }

    private Optional<JsonNode> get(RestClient client, String path) {
        if (!config.configured()) {
            log.warn("SEC User-Agent 미설정 상태로 호출 시도: {}", path);
            throw new BusinessException(ErrorCode.EXTERNAL_API_NOT_CONFIGURED);
        }
        throttle();
        try {
            return Optional.ofNullable(client.get().uri(path).retrieve().body(JsonNode.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Forbidden e) {
            // SEC 의 403 은 대부분 User-Agent 문제이거나 초당 한도 초과 뒤의 차단이다.
            log.error("SEC 403 — User-Agent 형식 또는 호출 한도를 확인할 것. path={}", path);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        } catch (RestClientException e) {
            log.error("SEC 호출 실패 path={} : {}", path, e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    /**
     * SEC 공정접근 한도는 초당 10회다. 넘기면 IP 가 막히고 복구가 번거로우므로 호출 쪽에서 미리 막는다.
     * 배치가 여러 스레드로 돌아도 전체 합이 한도 안에 있도록 인스턴스 하나에서 간격을 잰다.
     */
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
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }
        }
    }
}
