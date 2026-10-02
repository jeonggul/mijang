package com.example.mijang.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 실시간 시세({@code mijang.market.*}) 설정 바인딩이다. */
@ConfigurationProperties(prefix = "mijang.market")
public class MarketProperties {

    /** 벤더 동시 구독 한도. Alpaca 무료 Basic 이 30이다(2.1). */
    private int maxSubscriptions = 30;

    /** SSE 연결 수명. 끊겨도 브라우저가 다시 붙는다(2.3). */
    private Duration sseTimeout = Duration.ofMinutes(30);

    /** Alpaca 실시간 웹소켓 주소. IEX 피드다. */
    private String streamUrl = "wss://stream.data.alpaca.markets/v2/iex";

    /* 장외 시간용 스트림이다. 무료 요금제는 연결 1개뿐이라 IEX 와 동시에 열 수 없다. */
    private String delayedStreamUrl = "wss://stream.data.alpaca.markets/v2/delayed_sip";

    /** 실시간 수신 스위치다. 끄면 벤더에 붙지 않고 종가만 보여준다. */
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

    /* streamEnabled 접근자를 지우면 @ConfigurationProperties 가 값을 넣지 못한다. */
    public boolean isStreamEnabled() { return streamEnabled; }

    public void setStreamEnabled(boolean streamEnabled) { this.streamEnabled = streamEnabled; }

    public String getDelayedStreamUrl() { return delayedStreamUrl; }
    /** mijang.market.delayed-stream-url 주입. */
    public void setDelayedStreamUrl(String delayedStreamUrl) { this.delayedStreamUrl = delayedStreamUrl; }
}
