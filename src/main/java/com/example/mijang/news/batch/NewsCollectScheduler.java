/*
 * NewsCollectScheduler — 종목별 뉴스 수집 배치
 *
 * 이 파일이 하는 일
 *   보유·관심 종목의 기사를 받아 표에 쌓고, 새 기사가 있으면 알림을 만든다.
 *
 *   주기를 @Scheduled 로 못 박지 않는다. 운영 설정(news.refresh.minutes)이 30·60·180분
 *   중 하나를 정하는데, 크론에 박아 두면 관리자가 바꿔도 배치는 그대로 돈다.
 *   그래서 10분마다 깨어나 "지난 실행 이후 설정한 만큼 지났는가" 를 스스로 본다.
 *
 *   수집원은 Finnhub 이며 제목·요약·원문 링크만 저장하고 본문은 전재하지 않는다.
 *
 * 개발명세서 '실시간·배치 상세' 시트 · 화면 SR-010 · 확장(부록 C)
 */
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

/** 뉴스 수집. 주기는 운영 설정이 정한다. */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class NewsCollectScheduler {

    private final NewsService newsService;
    private final NotificationProducerService notificationProducer;
    private final AdminSettingService settingService;
    private final BatchLogWriter batchLog;

    /** 마지막으로 실제 수집한 시각. 서버가 뜬 뒤 한 번도 안 돌았으면 null 이다. */
    private volatile Instant lastRun;

    /**
     * 10분마다 깨어나 설정한 주기가 지났는지 본다.
     *
     * <p>깨어나는 주기(10분)는 설정할 수 있는 가장 짧은 주기(30분)보다 촘촘해야 한다.
     * 그래야 30분 설정이 실제로 30분 간격을 지킨다.
     */
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
                /* collect 는 종목 하나가 실패해도 예외를 내지 않는다 —
                   한 종목 때문에 나머지가 통째로 밀리면 안 된다 */
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
