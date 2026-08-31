/*
 * AlpacaWebSocketClient — 벤더에서 체결가를 받아 오는 곳
 *
 * 이 파일이 하는 일
 *   Alpaca 실시간 스트림에 웹소켓으로 붙어, 체결이 일어날 때마다 값을 받는다.
 *   받은 값은 캐시에 넣고(QuoteCacheService) 그 종목을 보고 있는 연결에 뿌린다(SseEmitterRegistry).
 *
 *   브라우저 쪽은 SSE 인데 여기만 웹소켓인 이유는 단순하다 — Alpaca 가 그것만 제공한다(2.3).
 *
 *   붙는 순서가 정해져 있다.
 *     연결 → connected 수신 → auth 전송 → authenticated 수신 → subscribe 전송
 *   중간을 건너뛰면 조용히 아무 값도 오지 않는다. 그래서 단계를 상태로 들고 있는다.
 *
 *   끊기면 다시 붙는다. 간격을 늘려 가며 붙는 이유는, 벤더가 잠깐 죽었을 때
 *   모든 인스턴스가 1초마다 두드리면 살아나는 것을 방해하기 때문이다.
 */
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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mijang.market.stream-enabled", havingValue = "true")
public class AlpacaWebSocketClient {

    /** 재연결 간격의 상한. 이보다 더 벌리면 살아난 것을 한참 못 알아챈다 */
    private static final long MAX_BACKOFF_SECONDS = 300;

    /** 재시도 간격이 상한에 닿는 단계. 2^8 = 256초로 MAX_BACKOFF_SECONDS 에 걸린다 */
    private static final int MAX_RETRY_STEP = 8;

    /** 미국 동부. 장 시간은 전부 이 기준이다 */
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

    /**
     * 벤더에 붙을 때 쓰는 클라이언트.
     *
     * <p><b>한 번만 만든다.</b> {@code newHttpClient()} 는 셀렉터 스레드와 실행기를 딸려
     * 만드는데 닫을 방법이 없다. 연결 시도마다 새로 만들면 재연결과 피드 전환이 반복되는 날
     * 스레드가 계속 쌓인다.
     */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final AtomicInteger retry = new AtomicInteger();
    private final AtomicBoolean closing = new AtomicBoolean();

    /**
     * 지금 붙는 중인가.
     *
     * <p>SSE 접속·구독 변경·폴러가 각자 {@link #ensureConnected()} 를 부른다.
     * 이 빗장이 없으면 셋이 동시에 "아직 안 붙었네" 를 보고 각각 연결을 연다.
     * 무료 요금제는 <b>동시 연결이 하나</b>라 두 번째부터 406 으로 거부당하고,
     * 그 과정에서 먼저 붙은 것까지 끊긴다.
     */
    private final AtomicBoolean connecting = new AtomicBoolean();
    private volatile WebSocket socket;
    private volatile boolean authenticated;

    /** 지금 붙어 있는 스트림이 지연 피드인가. 받은 체결을 어떻게 표시할지 정한다 */
    private volatile boolean onDelayedFeed;

    /**
     * 몇 번째 연결인가.
     *
     * <p>연결을 버릴 때마다 하나 올린다. 콜백은 <b>자기가 태어난 번호</b>를 들고 있어,
     * 번호가 어긋나면 이미 버려진 연결이 뒤늦게 부른 것이라 무시한다.
     *
     * <p>이게 없으면 피드를 갈아탈 때 사고가 난다. {@code disconnect()} 는 닫기 프레임을
     * <b>보내기만</b> 하고 곧바로 새 연결을 여는데, 옛 소켓의 {@code onClose} 가 그 뒤에
     * 도착해 {@code socket = null} 로 <b>방금 붙은 멀쩡한 연결을 지워 버린다.</b>
     * 그러면 살아 있는 소켓이 붙잡힌 채로 또 하나를 열게 되고, 동시 연결이 하나뿐인
     * 무료 요금제에서는 406 을 받으며 먼저 것까지 끊긴다.
     */
    private final java.util.concurrent.atomic.AtomicLong generation =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * 붙는다.
     *
     * <p>스프링이 뜰 때 자동으로 부르지 않는다. 아무도 화면을 안 보고 있는데 연결을
     * 잡고 있을 이유가 없다 — 첫 구독이 생길 때 {@link #ensureConnected()} 가 부른다.
     */
    private void connect() {
        if (closing.get()) {
            return;
        }
        try {
            boolean delayed = !regularSession();
            String url = delayed ? props.getDelayedStreamUrl() : props.getStreamUrl();
            onDelayedFeed = delayed;

            // 이 시도의 번호. 다 붙기 전에 누가 버렸으면 그 소켓은 쓰지 않고 닫는다
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

    /**
     * 지금이 미국 정규장 시간인가.
     *
     * <p>정규장에는 IEX 로 진짜 실시간을 받고, 그 밖에는 15분 지연 SIP 로 받는다.
     * 둘을 동시에 열 수 없어 하나를 골라야 한다.
     *
     * <p>휴장일은 거래 달력이 안다. 예전에는 요일과 시각만 보고 판단해서, 평일 휴장일에
     * 정규장으로 읽고 IEX 에 붙어 있었다 — 그날은 체결이 없어 화면의 값이 하루 종일
     * 멈춘다. 달력이 비어 있을 때만 요일·시각으로 물러난다.
     */
    private boolean regularSession() {
        ZonedDateTime now = ZonedDateTime.now(ET);
        try {
            return calendar.sessionAt(now) == MarketSession.REGULAR;
        } catch (RuntimeException e) {
            /* 달력을 못 읽어도 피드는 붙어 있어야 한다. 아래 어림짐작으로 물러난다 */
            log.warn("[실시간] 거래 달력을 읽지 못해 요일·시각으로 판단한다", e);
        }
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }

    /**
     * 장이 열리고 닫힐 때 피드를 갈아탄다.
     *
     * <p>1분마다 지금 써야 할 피드와 붙어 있는 피드를 견준다. 다르면 끊고 다시 붙는다.
     * 개장 직후 15분 늦은 값을 보고 있거나, 마감 직후 값이 멈추는 것을 막는다.
     */
    @Scheduled(fixedDelay = 60_000)
    public void switchFeedIfNeeded() {
        if (closing.get()) {
            return;
        }

        /* 끊겨 있으면 다시 붙는다.
           예전에는 여기서 그냥 돌아갔는데, 그러면 한 번 튕긴 뒤로 백오프가 끝날 때까지
           아무도 다시 붙이지 않는다. 그동안 화면은 지연 시세만 받으며 "15분 지연" 을
           계속 띄운다 — 정규장인데도 그렇다. 보는 사람이 있으면 1분마다 다시 시도한다 */
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

    /**
     * 지금 상태.
     *
     * <p>화면이 "15분 지연" 을 띄우는데 왜 그런지 알 길이 없어 넣었다.
     * 붙어 있는지, 어느 피드인지, 몇 종목을 보고 있는지를 한 번에 본다.
     */
    public String describeState() {
        if (socket == null) {
            return "연결 없음 (구독 " + pool.current().size() + "종목 · 지연 시세로만 갱신)";
        }
        return (authenticated ? "연결됨" : "인증 중")
                + " · " + (onDelayedFeed ? "지연 피드(SIP 15분)" : "실시간 피드(IEX)")
                + " · 구독 " + pool.current().size() + "종목";
    }

    /**
     * 아직 안 붙어 있으면 붙는다.
     *
     * <p>구독 목록이 바뀔 때마다 불린다. 이미 붙어 있으면 아무 일도 하지 않는다.
     */
    public synchronized void ensureConnected() {
        if (socket != null || closing.get()) {
            return;
        }
        /* 먼저 빗장을 잡은 하나만 연결한다. 나머지는 그냥 돌아간다.
           빗장을 걸고 여는 일과 discardConnection 이 상태를 지우는 일을 같은 자물쇠로
           묶는다. 따로 두면 그 사이에 끼어들 수 있다 — 버리는 쪽이 번호를 올리고
           socket 을 비운 직후, connecting 을 내리기 전에 다른 스레드가 "붙어 있지도 않고
           붙는 중도 아니네" 를 보고 연결을 연다. 그리고 버리던 쪽이 뒤늦게 빗장을 내려
           세 번째 스레드까지 들어온다. 동시 연결이 하나뿐이라 그 순간 406 이다 */
        if (connecting.compareAndSet(false, true)) {
            connect();
        }
    }

    /**
     * 지금 풀에 있는 종목으로 구독을 맞춘다.
     *
     * <p>Alpaca 는 "이것만 봐라"가 아니라 더하기·빼기로 동작한다. 그래서 전부 뺀 뒤
     * 다시 넣는다. 종목이 30개뿐이라 이 편이 차이를 계산하는 것보다 단순하고 틀릴 일이 없다.
     */
    public synchronized void resubscribe() {
        ensureConnected();
        WebSocket ws = socket;
        if (ws == null || !authenticated) {
            return;                 // 인증이 끝나면 그때 한 번 더 불린다
        }
        Set<String> symbols = pool.current();
        String unsubscribe = mapper.writeValueAsString(
                Map.of("action", "unsubscribe", "trades", List.of("*")));

        /* 두 프레임을 잇달아 보내면 안 된다. JDK 의 WebSocket 은 앞의 쓰기가 끝나기 전에
           또 보내면 예외를 던지지 않고 "Send pending" 으로 실패한 Future 를 돌려준다.
           그것을 버리고 있었으므로, 빼기만 나가고 넣기는 사라져도 아무도 몰랐다 —
           로그에는 "N종목 구독" 이 찍히고 값은 하나도 안 온다.
           앞의 것이 끝난 뒤에 다음 것을 보내고, 실패하면 남긴다 */
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

    /**
     * 구독 프레임을 만든다.
     *
     * <p>문자열로 이어 붙이지 않는다. 티커에 따옴표나 대괄호가 섞여 오면 프레임 구조가
     * 통째로 바뀐다 — 지금은 티커를 대문자로 바꾸고 모양까지 검사해 그런 값이 들어오지
     * 않지만, 그 검사가 하나만 느슨해져도 바깥에서 제어 프레임을 짜 넣을 수 있게 된다.
     * 이미 들고 있는 직렬화기에 맡기면 그 걱정이 없다.
     */
    private String subscribeFrame(Set<String> symbols) {
        return mapper.writeValueAsString(
                Map.of("action", "subscribe", "trades", List.copyOf(symbols)));
    }

    /**
     * 지금 연결을 버린다.
     *
     * <p>세대 번호를 올려 <b>이 연결의 콜백을 전부 무효로 만든다.</b> 닫기는 프레임을
     * 보내는 것뿐이라 {@code onClose} 는 한참 뒤에 오는데, 그때 이미 새 연결이 붙어
     * 있으면 그 콜백이 새것을 지워 버린다.
     *
     * <p>지연 피드 표시도 여기서 내린다. 켜 두기만 하고 안 내리면, 연결이 끊긴 뒤에도
     * {@link #delayedFeed()} 가 계속 참이라 {@code DelayedQuotePoller} 가 "스트림이
     * 채워 주겠지" 하고 빠져나간다 — 아무도 채우지 않아 값이 통째로 멈춘다.
     */
    private synchronized void discardConnection() {
        generation.incrementAndGet();
        socket = null;
        authenticated = false;
        onDelayedFeed = false;
        connecting.set(false);
    }

    /** 간격을 1·2·4…초로 늘려 가며 다시 붙는다. 최대 {@value #MAX_BACKOFF_SECONDS}초 */
    private void scheduleReconnect() {
        if (closing.get()) {
            return;
        }
        discardConnection();
        long wait = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(MAX_RETRY_STEP, retry.getAndIncrement()));
        log.info("[실시간] {}초 뒤 다시 붙는다", wait);
        scheduler.schedule(this::ensureConnected, wait, TimeUnit.SECONDS);
    }

    /** 장 마감 배치가 부른다. 체결이 없는 시간에 연결을 열어 둘 이유가 없다(2.5) */
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

    /** 지금 붙어서 인증까지 끝났는가. 운영 화면과 검증에서 쓴다 */
    public boolean live() {
        return socket != null && authenticated;
    }

    /** 지금 지연 피드에 붙어 있는가 */
    public boolean delayedFeed() {
        return onDelayedFeed;
    }

    /**
     * 벤더가 보내 오는 메시지를 처리한다.
     *
     * <p>{@code onText} 는 한 프레임이 조각으로 나뉘어 올 수 있다. {@code last} 가 true 일 때만
     * 모아 둔 것을 해석한다 — 조각 하나를 그대로 JSON 으로 읽으면 깨진다.
     */
    private class Listener implements WebSocket.Listener {

        /** 이 리스너가 태어난 연결 번호. 지금 번호와 다르면 버려진 연결이다 */
        private final long gen = generation.get();

        /**
         * 조각으로 나뉘어 오는 메시지를 모으는 자리. 한 프레임이 여러 번에 걸쳐 올 수 있다.
         *
         * <p><b>연결마다 따로 둔다.</b> 하나를 공유하면, 조각이 오는 도중에 끊겼을 때
         * 남은 앞부분이 다음 연결의 첫 프레임 — 즉 {@code connected} 인사 — 앞에 붙는다.
         * 그러면 JSON 해석이 터지고, 그 예외는 {@code onText} 가 삼키며, 인증 프레임이
         * 나가지 않아 벤더가 10초 뒤 조용히 끊는다.
         */
        private final StringBuilder buffer = new StringBuilder();

        /** 이미 버려진 연결의 뒤늦은 콜백인가. */
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
            /* 버린 연결에서 오는 말은 듣지 않는다.
               이 검사를 onClose 에만 걸어 두면 소용이 없다 — 버린 연결이 뒤늦게
               "connected" 인사를 보내면 onSuccess 가 socket 을 그 죽어 가는 소켓으로
               되돌려 놓고 인증 프레임까지 거기로 보낸다. 그 뒤로는 onClose 가 stale 이라
               재연결도 안 하고, switchFeedIfNeeded 는 socket != null 이라 가만히 있어서
               영영 붙지 못한다. onTrade 도 마찬가지로 내려놓은 피드의 값을 캐시에 넣는다 */
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
                // 갈아타면서 우리가 버린 연결이다. 이미 새것이 붙어 있으니 건드리지 않는다
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

    /** 벤더는 메시지를 늘 배열로 보낸다. 한 건이어도 배열이다 */
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

    /**
     * 벤더가 보낸 오류.
     *
     * <p>406 은 "연결이 이미 있다" 는 뜻이다. 무료 요금제는 동시 연결이 하나뿐이라,
     * 앞 인스턴스가 아직 정리되지 않았을 때 난다. 바로 다시 붙으면 같은 일이 반복되므로
     * 재시도 간격을 크게 벌린다.
     */
    private void onError(JsonNode message) {
        int code = message.path("code").asInt();
        log.warn("[실시간] 벤더 오류 {} — {}", code, message.path("msg").asText());
        if (code == 406) {
            /* 다른 인스턴스가 그 한 자리를 쓰고 있다. 계속 두드려도 뺏어 오지 못하고
               서로 끊기만 한다. 한참 뒤에 다시 본다.
               그동안에도 DelayedQuotePoller 가 값을 채우므로 화면은 멈추지 않는다 */
            retry.set(MAX_RETRY_STEP);
            log.warn("[실시간] 다른 인스턴스가 연결을 쓰고 있다. 지연 시세로만 갱신된다");
        }
    }

    private void onSuccess(WebSocket ws, String msg) {
        if ("connected".equals(msg)) {
            /* 필드가 아니라 넘겨받은 ws 로 보낸다.
               buildAsync 가 끝나기 전에 첫 메시지가 도착할 수 있고, 그때 필드는 아직 비어 있다.
               필드로 보내면 인증 프레임이 아무 데도 안 나가고 10초 뒤 벤더가 연결을 끊는다 */
            socket = ws;
            ws.sendText("{\"action\":\"auth\",\"key\":\"" + apiProps.alpaca().apiKey()
                    + "\",\"secret\":\"" + apiProps.alpaca().apiSecret() + "\"}", true);
        } else if ("authenticated".equals(msg)) {
            authenticated = true;
            log.info("[실시간] 인증 성공");
            resubscribe();
        }
    }

    /**
     * 체결 한 건.
     *
     * <p>{@code S} 티커, {@code p} 체결가, {@code t} 체결 시각이다.
     * 캐시에 넣고, 그 종목을 보고 있는 연결에만 보낸다(2.2).
     */
    private void onTrade(JsonNode trade) {
        String symbol = trade.path("S").asText("");
        JsonNode price = trade.get("p");
        if (symbol.isEmpty() || price == null || price.isNull()) {
            return;
        }
        Instant at = parseAt(trade.path("t").asText(""));
        BigDecimal value = new BigDecimal(price.asString());
        /* 지연 피드에서 온 값은 지연으로 표시한다. 실시간인 척 보내면 지금 값으로 오해한다 */
        if (onDelayedFeed) {
            cache.putDelayed(symbol, value, at);
        } else {
            cache.putLive(symbol, value, at);
        }
        cache.get(symbol).ifPresent(registry::broadcast);
    }

    /** 시각이 깨져 오면 지금으로 본다. 값 하나 때문에 체결을 버릴 이유는 없다 */
    private Instant parseAt(String raw) {
        try {
            return raw.isEmpty() ? Instant.now() : Instant.parse(raw);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    /** 구독 풀이 비었을 때 연결을 접는 데 쓴다 */
    public List<String> currentSymbols() {
        return List.copyOf(pool.current());
    }
}
