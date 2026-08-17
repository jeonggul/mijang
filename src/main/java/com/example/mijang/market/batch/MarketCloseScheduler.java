/*
 * MarketCloseScheduler — 장이 닫히면 뒷정리하는 곳
 *
 * 이 파일이 하는 일
 *   시간외 거래까지 끝나면 벤더 연결을 닫고, 캐시에 남은 값의 "실시간" 표시를 내린다.
 *   체결이 없는 시간에 연결을 붙잡고 있을 이유가 없고,
 *   멈춘 값에 실시간 딱지가 붙어 있으면 사용자가 지금 거래되는 값으로 읽는다.
 */
package com.example.mijang.market.batch;

import com.example.mijang.market.cache.QuoteCacheService;
import com.example.mijang.market.client.AlpacaWebSocketClient;
import com.example.mijang.market.pool.SubscriptionPoolManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 장 마감 뒷정리. 화면 SR-004 · 개발명세서 '실시간·배치 상세' 시트
 *
 * <p>시간외 거래가 끝나는 20시(ET)에 돈다. 16시가 아니다 — 16시에 끊으면 시간외
 * 체결을 놓친다(2.11의 세션별 피드 전환과 같은 기준이다).
 *
 * <p>하는 일은 둘이다.
 * <ul>
 *   <li><b>연결을 닫는다.</b> 체결이 없는 시간에 자리를 붙잡고 있을 이유가 없다.
 *       무료 요금제는 동시 연결이 하나뿐이라 더 그렇다.</li>
 *   <li><b>실시간 표시를 내린다.</b> 값은 그대로 두고 플래그만 내린다. 이걸 하지 않으면
 *       주말 내내 금요일 값에 "실시간" 배지가 붙어 있어, 사용자는 지금 거래되고 있는
 *       값으로 읽는다.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "mijang.batch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MarketCloseScheduler {

    private final SubscriptionPoolManager pool;
    private final QuoteCacheService cache;

    /** 벤더 수신부. 설정으로 꺼 둘 수 있어 없을 수도 있다 */
    private final ObjectProvider<AlpacaWebSocketClient> stream;

    /** 시간외까지 끝나는 20시(ET). 서머타임은 zone 이 알아서 맞춰 준다 */
    @Scheduled(cron = "0 5 20 * * MON-FRI", zone = "America/New_York")
    public void run() {
        int watching = pool.current().size();

        /* 구독 목록을 먼저 비운다. 끊는 것만으로는 소용이 없다 —
           switchFeedIfNeeded 가 1분마다 "socket 이 비었는데 보는 사람이 있네" 를 보고
           다시 붙인다. 브라우저 하나만 열려 있어도 밤새 연결을 붙잡게 된다.
           보는 사람이 다시 생기면 그때 SSE 가 풀을 채우고 연결도 따라 열린다 */
        pool.clear();
        stream.ifAvailable(AlpacaWebSocketClient::disconnect);
        cache.markAllClosed();
        log.info("[배치] 장 마감 정리 — 구독 비움, 연결 닫음, 실시간 표시 내림 (직전 {}종목)",
                watching);
    }
}
