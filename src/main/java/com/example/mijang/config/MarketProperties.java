/*
 * MarketProperties — 실시간 시세 설정값
 *
 * 이 파일이 하는 일
 *   동시에 몇 종목까지 구독할지, SSE 연결을 얼마나 열어 둘지,
 *   벤더 스트림 주소가 무엇인지를 담는 그릇이다.
 *   무료 요금제의 30종목 한도가 여기 숫자로 들어간다 — 요금제를 올리면
 *   코드가 아니라 설정만 바꾸면 된다.
 */
package com.example.mijang.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 실시간 시세 설정. application.properties 의 mijang.market.* 를 받는다. */
@ConfigurationProperties(prefix = "mijang.market")
public class MarketProperties {

    /** 벤더 동시 구독 한도. Alpaca 무료 Basic 이 30이다(2.1). */
    private int maxSubscriptions = 30;

    /** SSE 연결 수명. 끊겨도 브라우저가 다시 붙는다(2.3). */
    private Duration sseTimeout = Duration.ofMinutes(30);

    /** Alpaca 실시간 웹소켓 주소. IEX 피드다. */
    private String streamUrl = "wss://stream.data.alpaca.markets/v2/iex";

    /**
     * 장 시간 밖에 쓰는 스트림.
     *
     * <p>IEX 는 거래소 하나라 프리마켓·애프터마켓에 체결이 거의 없다(실측 10분에 0건).
     * delayed_sip 은 전 거래소를 합친 값이라 그 시간에도 계속 온다. 대신 15분 늦다.
     *
     * <p>둘을 <b>동시에 열 수는 없다.</b> 무료 요금제의 연결 한도 1개가 피드 전체에 걸린다.
     * 그래서 장 시간에 따라 갈아탄다.
     */
    private String delayedStreamUrl = "wss://stream.data.alpaca.markets/v2/delayed_sip";

    /**
     * 실시간 수신을 켤지.
     *
     * <p>끄면 벤더에 붙지 않고 종가만 보여준다. 테스트와 로컬에서 쓸데없이 연결을
     * 잡지 않게 하려는 것이다 — 동시 구독이 30종목뿐이라 개발용 연결이 운영분을 잡아먹는다.
     */
    private boolean streamEnabled = true;

    // 아래는 스프링이 값을 넣고 꺼내기 위한 접근자다.

    /** 구독 한도를 읽는다. */
    public int getMaxSubscriptions() { return maxSubscriptions; }
    /** mijang.market.max-subscriptions 주입. */
    public void setMaxSubscriptions(int maxSubscriptions) { this.maxSubscriptions = maxSubscriptions; }

    /** SSE 수명을 읽는다. */
    public Duration getSseTimeout() { return sseTimeout; }
    /** mijang.market.sse-timeout 주입. */
    public void setSseTimeout(Duration sseTimeout) { this.sseTimeout = sseTimeout; }

    /** 스트림 주소를 읽는다. */
    public String getStreamUrl() { return streamUrl; }
    /** mijang.market.stream-url 주입. */
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    /** 장 시간 밖에 쓰는 스트림 주소. */
    /* streamEnabled 도 접근자가 있어야 한다. 없으면 @ConfigurationProperties 가 값을
       넣지 못해 언제나 초기값(true)이다. @ConditionalOnProperty 는 Environment 를 직접
       읽어 지금은 맞게 도는데, 그 탓에 빠진 것이 드러나지 않았다 */
    public boolean isStreamEnabled() { return streamEnabled; }

    public void setStreamEnabled(boolean streamEnabled) { this.streamEnabled = streamEnabled; }

    public String getDelayedStreamUrl() { return delayedStreamUrl; }
    /** mijang.market.delayed-stream-url 주입. */
    public void setDelayedStreamUrl(String delayedStreamUrl) { this.delayedStreamUrl = delayedStreamUrl; }
}
