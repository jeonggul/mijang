package com.example.mijang.market.batch;

import com.example.mijang.market.cache.QuoteCacheService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 인기 종목(약 300개) REST 폴링 — 계층 2. 화면 SR-004 · 확장(부록 C).
 * 기획서 7장의 계층 구조는 Alpaca 확정으로 전제가 바뀌어 부록 E 에 재설계 과제로 남아 있다.
 *
 * <p>개발명세서 '실시간·배치 상세' 시트
 * <p>구현 전이라 mijang.batch.enabled=false 로 꺼 둔다. 켜면 아래 주기대로 실제로 돌기 시작한다.
 */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PopularQuotePollingScheduler {

    private final QuoteCacheService quoteCacheService;

    @Scheduled(fixedDelayString = "${mijang.batch.popular-poll-delay-ms:7000}")
    public void run() {
        log.warn("[배치] PopularQuotePollingScheduler 아직 구현 전이다");
        // quoteCacheService.quotes(List.of());
    }
}
