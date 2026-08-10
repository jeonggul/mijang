package com.example.mijang.news.batch;

import com.example.mijang.news.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종목별 뉴스 수집. 화면 SR-010 · 확장(부록 C).
 * 수집원은 Finnhub 이며 제목·요약·원문 링크만 저장하고 본문은 전재하지 않는다.
 *
 * <p>개발명세서 '실시간·배치 상세' 시트
 * <p>구현 전이라 mijang.batch.enabled=false 로 꺼 둔다. 켜면 아래 주기대로 실제로 돌기 시작한다.
 */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class NewsCollectScheduler {

    private final NewsService newsService;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void run() {
        log.warn("[배치] NewsCollectScheduler 아직 구현 전이다");
        // newsService.collect(symbol);
    }
}
