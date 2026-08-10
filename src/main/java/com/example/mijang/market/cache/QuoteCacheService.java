package com.example.mijang.market.cache;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 현재가 캐시. 중복 호출을 없애고 화면 조회를 캐시에서 받는다.
 *
 * <p>개발명세서(MVC) · 실시간 시세 · cache
 * <p>TODO: Redis 로 옮긴다. spring-boot-starter-data-redis 의존성이 아직 없다.
 */
@Service
public class QuoteCacheService {

    public Map<String, BigDecimal> quotes(List<String> symbols) {
        throw new UnsupportedOperationException("TODO MARKET-003: 캐시에서 현재가 스냅샷 조회");
    }
}
