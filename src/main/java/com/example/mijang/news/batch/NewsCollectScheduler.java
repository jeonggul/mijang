package com.example.mijang.news.batch;

import com.example.mijang.admin.domain.AdminSettingKey;
import com.example.mijang.admin.service.AdminSettingService;
import com.example.mijang.admin.service.BatchLogWriter;
import com.example.mijang.news.service.NewsService;
import com.example.mijang.user.service.NotificationProducerService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 보유·관심 종목 뉴스를 수집하는 배치다. 주기는 운영 설정(news.refresh.minutes)이 정한다. */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class NewsCollectScheduler {

    private final NewsService newsService;
    private final NotificationProducerService notificationProducer;
    private final AdminSettingService settingService;
    private final BatchLogWriter batchLog;

    /** 마지막으로 실제 수집한 시각. 한 번도 안 돌았으면 null 이다. */
    private volatile Instant lastRun;

    /** 10분마다 깨어나 설정 주기가 지났으면 수집한다. 깨어나는 주기는 최소 설정 주기(30분)보다 촘촘해야 한다. */
    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    public void run() {
        int everyMinutes = settingService.number(AdminSettingKey.NEWS_REFRESH_MINUTES);
        if (lastRun != null
                && Duration.between(lastRun, Instant.now()).toMinutes() < everyMinutes) {
            return;
        }
        lastRun = Instant.now();

        batchLog.run("뉴스 수집", () -> {
            List<String> symbols = newsService.symbolsOfInterest();
            if (symbols.isEmpty()) {
                return 0;
            }
            int saved = 0;
            for (String symbol : symbols) {
                // collect 는 종목 하나가 실패해도 예외를 내지 않는다
                int newCount = newsService.collect(symbol);
                saved += newCount;
                if (newCount > 0) {
                    notificationProducer.produceNews(symbol, newCount);
                }
            }
            log.info("[배치] 뉴스 수집 — 종목 {}개 · 새 기사 {}건", symbols.size(), saved);
            return saved;
        });
    }
}
