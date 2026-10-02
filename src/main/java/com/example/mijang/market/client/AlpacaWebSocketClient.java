package com.example.mijang.market.client;

import com.example.mijang.config.ExternalApiProperties;
import com.example.mijang.config.MarketProperties;
import com.example.mijang.market.cache.QuoteCacheService;
import com.example.mijang.market.domain.MarketSession;
import com.example.mijang.market.service.MarketCalendarService;
import com.example.mijang.market.pool.SubscriptionPoolManager;
import com.example.mijang.market.stream.SseEmitterRegistry;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Alpaca 웹소켓 스트림에 붙어 체결가를 받아 캐시에 넣고 화면에 뿌린다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.market.stream-enabled", havingValue = "true")
public class AlpacaWebSocketClient {

    /** 재연결 간격의 상한. */
    private static final long MAX_BACKOFF_SECONDS = 300;

    /** 재시도 간격이 상한에 닿는 단계. */
    private static final int MAX_RETRY_STEP = 8;

    /** 미국 동부. 장 시간의 기준이다. */
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime CLOSE = LocalTime.of(16, 0);

    private final MarketProperties props;
    private final ExternalApiProperties apiProps;
    private final QuoteCacheService cache;
    private final SseEmitterRegistry registry;
    private final SubscriptionPoolManager pool;
    private final MarketCalendarService calendar;
    private final ObjectMapper mapper = new ObjectMapper();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "alpaca-ws");
                t.setDaemon(true);          // 애플리케이션이 이 스레드 때문에 안 끝나면 안 된다
                return t;
            });

    /** 벤더 접속용 클라이언트. 매번 새로 만들면 스레드가 쌓이므로 한 번만 만든다. */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final AtomicInteger retry = new AtomicInteger();
    private final AtomicBoolean closing = new AtomicBoolean();

    /* 동시 연결이 하나뿐이라 중복 연결을 막는 빗장이다 */
    private final AtomicBoolean connecting = new AtomicBoolean();
    private volatile WebSocket socket;
    private volatile boolean authenticated;

    /* 지금 붙어 있는 스트림이 지연 피드인지 */
    private volatile boolean onDelayedFeed;

    /* 연결 세대 번호. 번호가 어긋난 콜백은 버려진 연결의 것이라 무시한다 */
    private final java.util.concurrent.atomic.AtomicLong generation =
            new java.util.concurrent.atomic.AtomicLong();

    /** 벤더에 연결한다. 첫 구독이 생길 때 ensureConnected 가 부른다. */
    private void connect() {
        if (closing.get()) {
            return;
        }
        try {
            boolean delayed = !regularSession();
            String url = delayed ? props.getDelayedStreamUrl() : props.getStreamUrl();
            onDelayedFeed = delayed;

            // 다 붙기 전에 세대가 바뀌면 그 소켓은 쓰지 않고 닫는다
            long attempt = generation.get();

            httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(url), new Listener())
                    .thenAccept(ws -> {
                        if (attempt != generation.get()) {
                            log.debug("[실시간] 늦게 열린 연결이라 그냥 닫는다 — {}", url);
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "superseded");
                            return;
                        }
                        socket = ws;
                        connecting.set(false);
                        log.info("[실시간] 벤더 연결 열림 — {}", url);
                    })
                    .exceptionally(e -> {
                        connecting.set(false);
                        log.warn("[실시간] 연결 실패", e);
                        scheduleReconnect();
                        return null;
                    });
        } catch (RuntimeException e) {
            connecting.set(false);
            log.warn("[실시간] 연결 시도 중 오류", e);
            scheduleReconnect();
        }
    }

    /** 지금이 미국 정규장 시간인지 판정한다. 달력을 못 읽을 때만 요일·시각으로 물러난다. */
    private boolean regularSession() {
        ZonedDateTime now = ZonedDateTime.now(ET);
        try {
            return calendar.sessionAt(now) == MarketSession.REGULAR;
        } catch (RuntimeException e) {
            /* 달력을 못 읽어도 피드는 붙어 있어야 한다 */
            log.warn("[실시간] 거래 달력을 읽지 못해 요일·시각으로 판단한다", e);
        }
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }

    /** 1분마다 써야 할 피드와 붙은 피드를 견줘, 다르면 끊고 갈아탄다. */
    @Scheduled(fixedDelay = 60_000)
    public void switchFeedIfNeeded() {
        if (closing.get()) {
            return;
        }

        /* 끊겨 있으면 보는 사람이 있는 한 1분마다 다시 붙는다 */
        if (socket == null) {
            if (!pool.current().isEmpty()) {
                ensureConnected();
            }
            return;
        }

        boolean shouldBeDelayed = !regularSession();
        if (shouldBeDelayed != onDelayedFeed) {
            log.info("[실시간] 피드 전환 — {} → {}", onDelayedFeed ? "지연" : "실시간",
                    shouldBeDelayed ? "지연" : "실시간");
            disconnect();
            retry.set(0);
            ensureConnected();
        }
    }

    /** 연결·피드·구독 상태를 한 줄로 설명한다. */
    public String describeState() {
        if (socket == null) {
            return "연결 없음 (구독 " + pool.current().size() + "종목 · 지연 시세로만 갱신)";
        }
        return (authenticated ? "연결됨" : "인증 중")
                + " · " + (onDelayedFeed ? "지연 피드(SIP 15분)" : "실시간 피드(IEX)")
                + " · 구독 " + pool.current().size() + "종목";
    }

    /** 아직 안 붙어 있으면 붙는다. 이미 붙어 있으면 아무 일도 하지 않는다. */
    public synchronized void ensureConnected() {
        if (socket != null || closing.get()) {
            return;
        }
        /* 먼저 빗장을 잡은 하나만 연결한다 — discardConnection 과 같은 자물쇠여야 한다 */
        if (connecting.compareAndSet(false, true)) {
            connect();
        }
    }

    /** 지금 풀에 있는 종목으로 구독을 맞춘다. 전부 뺀 뒤 다시 넣는다. */
    public synchronized void resubscribe() {
        ensureConnected();
        WebSocket ws = socket;
        if (ws == null || !authenticated) {
            return;                 // 인증이 끝나면 그때 한 번 더 불린다
        }
        Set<String> symbols = pool.current();
        String unsubscribe = mapper.writeValueAsString(
                Map.of("action", "unsubscribe", "trades", List.of("*")));

        /* 앞의 쓰기가 끝나기 전에 또 보내면 "Send pending" 으로 조용히 실패한다 — 순서대로 보낸다 */
        CompletableFuture<WebSocket> sent = ws.sendText(unsubscribe, true);
        if (!symbols.isEmpty()) {
            sent = sent.thenCompose(w -> w.sendText(subscribeFrame(symbols), true));
        }
        sent.whenComplete((w, error) -> {
            if (error != null) {
                log.warn("[실시간] 구독 프레임을 보내지 못했다. 다시 붙는다", error);
                scheduleReconnect();
            } else if (!symbols.isEmpty()) {
                log.info("[실시간] {}종목 구독 — {}", symbols.size(), symbols);
            }
        });
    }

    /** 구독 프레임을 만든다. 문자열 이어 붙이기 대신 직렬화기에 맡긴다. */
    private String subscribeFrame(Set<String> symbols) {
        return mapper.writeValueAsString(
                Map.of("action", "subscribe", "trades", List.copyOf(symbols)));
    }

    /** 지금 연결을 버린다. 세대 번호를 올려 이 연결의 늦은 콜백을 전부 무효로 만든다. */
    private synchronized void discardConnection() {
        generation.incrementAndGet();
        socket = null;
        authenticated = false;
        onDelayedFeed = false;
        connecting.set(false);
    }

    /** 간격을 배로 늘려 가며 다시 붙는다. */
    private void scheduleReconnect() {
        if (closing.get()) {
            return;
        }
        discardConnection();
        long wait = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(MAX_RETRY_STEP, retry.getAndIncrement()));
        log.info("[실시간] {}초 뒤 다시 붙는다", wait);
        scheduler.schedule(this::ensureConnected, wait, TimeUnit.SECONDS);
    }

    /** 벤더 연결을 닫는다. 장 마감 배치가 부른다. */
    public void disconnect() {
        WebSocket ws = socket;
        discardConnection();
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "market closed");
            log.info("[실시간] 벤더 연결 닫음");
        }
    }

    @PreDestroy
    void shutdown() {
        closing.set(true);
        disconnect();
        scheduler.shutdownNow();
    }

    /** 붙어서 인증까지 끝났는지 반환한다. */
    public boolean live() {
        return socket != null && authenticated;
    }

    /** 지연 피드에 붙어 있는지 반환한다. */
    public boolean delayedFeed() {
        return onDelayedFeed;
    }

    /** 벤더가 보내 오는 메시지를 처리한다. last 가 true 일 때만 모아 둔 것을 해석한다. */
    private class Listener implements WebSocket.Listener {

        /* 이 리스너가 태어난 연결 번호 */
        private final long gen = generation.get();

        /* 조각으로 오는 메시지를 모으는 버퍼. 연결마다 따로 둬야 잔여 조각이 새 연결을 오염시키지 않는다 */
        private final StringBuilder buffer = new StringBuilder();

        /** 이미 버려진 연결의 뒤늦은 콜백인지 판정한다. */
        private boolean stale() {
            return gen != generation.get();
        }

        @Override
        public void onOpen(WebSocket ws) {
            retry.set(0);
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            /* 버린 연결의 메시지를 처리하면 죽은 소켓이 socket 을 되차지한다 — 반드시 걸러야 한다 */
            if (stale()) {
                ws.request(1);
                return null;
            }
            buffer.append(data);
            if (last) {
                String frame = buffer.toString();
                buffer.setLength(0);
                try {
                    handle(ws, mapper.readTree(frame));
                } catch (RuntimeException e) {
                    log.warn("[실시간] 메시지 해석 실패", e);
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
            if (stale()) {
                // 우리가 버린 연결이라 건드리지 않는다
                log.debug("[실시간] 버린 연결이 닫혔다 — {} {}", status, reason);
                return null;
            }
            log.info("[실시간] 연결 끊김 — {} {}", status, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (stale()) {
                log.debug("[실시간] 버린 연결의 오류", error);
                return;
            }
            log.warn("[실시간] 연결 오류", error);
            scheduleReconnect();
        }
    }

    /** 배열로 오는 메시지를 종류별로 나눠 처리한다. */
    private void handle(WebSocket ws, JsonNode frame) {
        if (frame == null || !frame.isArray()) {
            return;
        }
        for (JsonNode message : frame) {
            switch (message.path("T").asText("")) {
                case "success" -> onSuccess(ws, message.path("msg").asText(""));
                case "subscription" -> log.debug("[실시간] 구독 확인 {}", message);
                case "error" -> onError(message);
                case "t" -> onTrade(message);
                default -> { /* 호가·바 등 구독하지 않은 종류는 무시한다 */ }
            }
        }
    }

    /** 벤더 오류를 처리한다. 406(연결 중복)이면 재시도 간격을 크게 벌린다. */
    private void onError(JsonNode message) {
        int code = message.path("code").asInt();
        log.warn("[실시간] 벤더 오류 {} — {}", code, message.path("msg").asText());
        if (code == 406) {
            /* 다른 인스턴스가 연결을 쓰고 있다. 한참 뒤에 다시 본다 */
            retry.set(MAX_RETRY_STEP);
            log.warn("[실시간] 다른 인스턴스가 연결을 쓰고 있다. 지연 시세로만 갱신된다");
        }
    }

    private void onSuccess(WebSocket ws, String msg) {
        if ("connected".equals(msg)) {
            /* buildAsync 가 끝나기 전에 첫 메시지가 올 수 있어 필드가 아니라 넘겨받은 ws 로 보낸다 */
            socket = ws;
            ws.sendText("{\"action\":\"auth\",\"key\":\"" + apiProps.alpaca().apiKey()
                    + "\",\"secret\":\"" + apiProps.alpaca().apiSecret() + "\"}", true);
        } else if ("authenticated".equals(msg)) {
            authenticated = true;
            log.info("[실시간] 인증 성공");
            resubscribe();
        }
    }

    /** 체결 한 건을 캐시에 넣고 그 종목을 보는 연결에 보낸다. */
    private void onTrade(JsonNode trade) {
        String symbol = trade.path("S").asText("");
        JsonNode price = trade.get("p");
        if (symbol.isEmpty() || price == null || price.isNull()) {
            return;
        }
        Instant at = parseAt(trade.path("t").asText(""));
        BigDecimal value = new BigDecimal(price.asString());
        /* 지연 피드에서 온 값은 지연으로 표시한다 */
        if (onDelayedFeed) {
            cache.putDelayed(symbol, value, at);
        } else {
            cache.putLive(symbol, value, at);
        }
        cache.get(symbol).ifPresent(registry::broadcast);
    }

    /** 체결 시각을 파싱한다. 깨져 오면 지금으로 본다. */
    private Instant parseAt(String raw) {
        try {
            return raw.isEmpty() ? Instant.now() : Instant.parse(raw);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    /** 지금 구독 중인 종목 목록을 반환한다. */
    public List<String> currentSymbols() {
        return List.copyOf(pool.current());
    }
}
