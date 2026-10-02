package com.example.mijang.stock.service;

import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.market.domain.MarketDay;
import com.example.mijang.market.service.MarketCalendarService;
import com.example.mijang.stock.client.AlpacaStockClient;
import com.example.mijang.stock.domain.ChartRange;
import com.example.mijang.stock.dto.ChartPoint;
import com.example.mijang.stock.dto.ChartResponse;
import com.example.mijang.stock.mapper.DailyPriceMapper;
import com.example.mijang.stock.mapper.StockMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 기간별 차트 점 목록을 구해 주는 서비스다. 일봉 구간은 DB 우선, 분봉·주봉·월봉은 벤더에서 바로 받아 짧게 캐시한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    /** 장중 값의 캐시 수명. */
    private static final Duration INTRADAY_TTL = Duration.ofSeconds(30);

    /** 확정된 과거 봉의 캐시 수명. */
    private static final Duration HISTORY_TTL = Duration.ofMinutes(30);

    /** DB 에 이만큼 앞의 값이 없으면 과거분이 비어 있다고 본다. */
    private static final int BACKFILL_GAP_DAYS = 7;

    /** SIP 요청 끝 시각을 당길 폭. 이보다 짧으면 무료 요금제에서 403 이 나 장 시간 밖 데이터를 잃는다. */
    private static final Duration SIP_DELAY = Duration.ofMinutes(16);

    /** 미국 동부 표준시. 장 시간의 기준이다. */
    private static final ZoneId ET = ZoneId.of("America/New_York");

    /** 프리마켓이 열리는 시각. 하루 치 차트의 시작점이다. */
    private static final LocalTime PRE_MARKET_OPEN = LocalTime.of(4, 0);

    private final AlpacaStockClient alpacaClient;
    private final DailyPriceMapper dailyPriceMapper;
    private final StockMapper stockMapper;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** 종목별 벤더가 가진 가장 오래된 거래일. 이게 없으면 신규 상장 종목에서 백필이 끝없이 반복된다. */
    private final Map<String, LocalDate> historyFloor = new ConcurrentHashMap<>();
    private final com.example.mijang.common.time.TradingClock tradingClock;
    private final MarketCalendarService marketCalendarService;

    /** 벤더에 요청할 정확한 시작·끝. 휴장 중에는 직전 거래 세션을 가리킨다. */
    record ChartWindow(Instant from, Instant to, boolean currentSession) {
    }

    private record Cached(Instant until, List<ChartPoint> points) {
        boolean alive() {
            return Instant.now().isBefore(until);
        }
    }

    /** 종목·기간으로 차트 한 장을 돌려준다. 없는 종목이면 여기서 예외로 막는다. */
    public ChartResponse chart(String rawSymbol, String rawRange) {
        String symbol = normalize(rawSymbol);
        if (stockMapper.findBySymbol(symbol) == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "symbol");
        }
        ChartRange range = ChartRange.of(rawRange);

        String key = symbol + "|" + range.code();
        Cached hit = cache.get(key);
        if (hit != null && hit.alive()) {
            return new ChartResponse(symbol, range.code(), range.timeframe(), range.intraday(), hit.points());
        }

        List<ChartPoint> points = range.stored() ? fromStore(symbol, range) : fromVendor(symbol, range);
        cache.put(key, new Cached(Instant.now().plus(range.intraday() ? INTRADAY_TTL : HISTORY_TTL), points));
        return new ChartResponse(symbol, range.code(), range.timeframe(), range.intraday(), points);
    }

    /** 일봉 구간 — DB 를 먼저 보고, 과거분이 비어 있으면 한 번 채운 뒤 다시 읽는다. */
    private List<ChartPoint> fromStore(String symbol, ChartRange range) {
        // 미국 거래일 기준이다. LocalDate.now()(KST)를 쓰면 장 마감 뒤 아침까지 하루가 앞서 나간다
        LocalDate to = tradingClock.today();
        LocalDate from = to.minusDays(range.lookback().toDays());

        List<ChartPoint> stored = toPoints(dailyPriceMapper.findByRange(symbol, from, to));
        if (!needsBackfill(symbol, stored, from)) {
            return stored;
        }

        log.info("[차트] {} {} 과거분 없음 — 벤더에서 채운다", symbol, range.code());
        LocalDate oldest = backfill(symbol, from, to);
        if (oldest == null) {
            // 벤더도 이 구간에 줄 것이 없다. 요청한 시작을 바닥으로 적어 다시 묻지 않는다
            historyFloor.merge(symbol, from, (a, b) -> a.isBefore(b) ? a : b);
            return stored;
        }
        // 물어본 것보다 늦게 시작할 때만 바닥으로 적는다 — 꽉 채워 온 경우까지 적으면 더 긴 기간 요청이 막힌다
        if (oldest.isAfter(from)) {
            historyFloor.merge(symbol, oldest, (a, b) -> a.isBefore(b) ? a : b);
        }
        return toPoints(dailyPriceMapper.findByRange(symbol, from, to));
    }

    /** 과거분 백필이 필요한지 판단한다. */
    private boolean needsBackfill(String symbol, List<ChartPoint> stored, LocalDate from) {
        // 벤더에 없다고 확인된 구간(상장 전 기간)이면 묻지 않는다
        LocalDate floor = historyFloor.get(symbol);
        LocalDate wanted = (floor != null && floor.isAfter(from)) ? floor : from;

        if (stored.isEmpty()) {
            // 아직 안 물어봤거나 물어본 것보다 더 앞을 원할 때만 벤더를 부른다
            return floor == null || from.isBefore(floor);
        }
        return LocalDate.parse(stored.get(0).at()).isAfter(wanted.plusDays(BACKFILL_GAP_DAYS));
    }

    /** 벤더에서 일봉을 받아 DB 에 넣고 가장 오래된 거래일을 돌려준다. 하나도 없으면 null 이다. */
    private LocalDate backfill(String symbol, LocalDate from, LocalDate to) {
        JsonNode bars = barsOf(alpacaClient.bars(List.of(symbol), "1Day", from.toString(), to.toString()), symbol);
        if (bars == null) {
            return null;
        }
        LocalDate oldest = null;
        for (JsonNode bar : bars) {
            LocalDate date = LocalDate.parse(bar.path("t").asText().substring(0, 10));
            dailyPriceMapper.upsert(symbol, date,
                    decimal(bar, "o"), decimal(bar, "h"), decimal(bar, "l"), decimal(bar, "c"),
                    bar.path("v").asLong(0));
            if (oldest == null || date.isBefore(oldest)) {
                oldest = date;
            }
        }
        return oldest;
    }

    /** 분봉·주봉·월봉을 벤더에서 바로 받아 점으로 바꾼다. 분봉은 SIP+IEX 두 피드를 이어 붙인다. */
    private List<ChartPoint> fromVendor(String symbol, ChartRange range) {
        Instant requestTime = Instant.now();
        ChartWindow window = windowOf(range, requestTime);
        Instant from = window.from();
        Instant to = window.to();

        if (!range.intraday()) {
            return toPoints(barsOf(alpacaClient.bars(
                    List.of(symbol), range.timeframe(), from.toString(), to.toString()), symbol));
        }

        // SIP 제한은 실제 현재 시각 기준이다 — 이미 16분이 지난 구간은 끝까지 SIP 로 받을 수 있다
        Instant sipEnd = requestTime.minus(SIP_DELAY);
        if (sipEnd.isAfter(to)) {
            sipEnd = to;
        }
        List<ChartPoint> merged = new ArrayList<>();

        // 앞부분 — SIP. 전 거래소를 합친 값이라 장 시간 밖이 들어 있다
        if (sipEnd.isAfter(from)) {
            merged.addAll(toPoints(barsOf(alpacaClient.bars(
                    List.of(symbol), range.timeframe(), from.toString(), sipEnd.toString(), "sip"), symbol)));
        }

        // 끝부분 — IEX. SIP 가 못 주는 최근 16분을 메운다
        List<ChartPoint> tail = sipEnd.isBefore(to)
                ? toPoints(barsOf(alpacaClient.bars(
                        List.of(symbol), range.timeframe(), sipEnd.toString(), to.toString(), "iex"), symbol))
                : List.of();

        // 경계에서 같은 시각의 봉이 양쪽에 있으면 SIP 쪽을 남긴다
        String lastAt = merged.isEmpty() ? "" : merged.get(merged.size() - 1).at();
        for (ChartPoint p : tail) {
            if (p.at().compareTo(lastAt) > 0) {
                merged.add(p);
            }
        }

        // 결과가 비면 최근 7일에서 마지막 실제 거래 세션을 다시 찾아 빈 화면을 피한다
        if (merged.isEmpty() && (range == ChartRange.LIVE || range == ChartRange.ONE_DAY)) {
            // 현재 세션인데 지연 데이터가 아직 없으면 직전 거래일로 대체하지 않고 대기 상태로 둔다
            if (window.currentSession()) {
                return merged;
            }
            Instant fallbackFrom = requestTime.minus(Duration.ofDays(7));
            Instant fallbackTo = requestTime.minus(SIP_DELAY);
            List<ChartPoint> fallback = toPoints(barsOf(alpacaClient.bars(List.of(symbol), range.timeframe(),
                    fallbackFrom.toString(), fallbackTo.toString(), "sip"), symbol));
            return lastSession(fallback, range);
        }
        return merged;
    }

    /** 벤더에 요청할 조회 구간을 정한다. 하루 치는 24시간 전이 아니라 그날 세션 시작 시각부터다. */
    ChartWindow windowOf(ChartRange range, Instant now) {
        if (range != ChartRange.LIVE && range != ChartRange.ONE_DAY) {
            return new ChartWindow(now.minus(range.lookback()), now, false);
        }
        ZonedDateTime et = now.atZone(ET);
        LocalDate date = et.toLocalDate();

        // 오늘 세션이 시작했으면 오늘, 아니면 직전 거래일 — 단순히 하루를 빼면 주말·공휴일을 못 건너뛴다
        MarketDay day = marketCalendarService.tradingDay(date)
                .filter(current -> !et.toLocalTime().isBefore(current.sessionOpen()))
                .orElseGet(() -> marketCalendarService.previousTradingDayInfo(date).orElse(null));

        if (day != null) {
            Instant sessionStart = day.tradeDate().atTime(day.sessionOpen()).atZone(ET).toInstant();
            Instant sessionClose = day.tradeDate().atTime(day.sessionClose()).atZone(ET).toInstant();
            Instant end = now.isBefore(sessionClose) ? now : sessionClose;
            Instant start = range == ChartRange.ONE_DAY ? sessionStart : end.minus(range.lookback());
            if (start.isBefore(sessionStart)) {
                start = sessionStart;
            }
            boolean currentSession = day.tradeDate().equals(date) && end.equals(now);
            return new ChartWindow(start, end, currentSession);
        }

        // 달력을 아직 받지 못한 환경의 기본값 — 결과가 비면 fromVendor 가 마지막 거래 세션을 다시 찾는다
        ZonedDateTime open = et.toLocalTime().isBefore(PRE_MARKET_OPEN)
                ? et.minusDays(1).with(PRE_MARKET_OPEN)
                : et.with(PRE_MARKET_OPEN);

        if (range == ChartRange.ONE_DAY) {
            return new ChartWindow(open.toInstant(), now, false);
        }

        // 실시간은 최근 다섯 시간이되 프리마켓 시작보다 앞으로 가지 않는다 — 어제 값이 섞인다
        Instant rolling = now.minus(range.lookback());
        Instant sessionStart = open.toInstant();
        return new ChartWindow(rolling.isBefore(sessionStart) ? sessionStart : rolling, now, false);
    }

    /** 넓게 받은 분봉 중 마지막으로 거래가 있었던 미국 날짜만 남긴다. */
    private List<ChartPoint> lastSession(List<ChartPoint> points, ChartRange range) {
        if (points.isEmpty()) {
            return points;
        }
        Instant lastAt = Instant.parse(points.get(points.size() - 1).at());
        LocalDate lastDate = lastAt.atZone(ET).toLocalDate();
        Instant liveFrom = lastAt.minus(range.lookback());
        return points.stream()
                .filter(point -> {
                    Instant at = Instant.parse(point.at());
                    return at.atZone(ET).toLocalDate().equals(lastDate)
                            && (range != ChartRange.LIVE || !at.isBefore(liveFrom));
                })
                .toList();
    }

    /** 응답에서 이 종목의 봉 배열을 꺼낸다. 값이 없으면 null 이다. */
    private JsonNode barsOf(JsonNode response, String symbol) {
        JsonNode bars = response == null ? null : response.get("bars");
        if (bars == null || !bars.isObject()) {
            return null;
        }
        JsonNode mine = bars.get(symbol);
        return (mine == null || !mine.isArray() || mine.isEmpty()) ? null : mine;
    }

    /** 벤더 응답의 봉 배열을 점으로 바꾼다. 배열이 없으면 빈 목록이다. */
    private List<ChartPoint> toPoints(JsonNode bars) {
        if (bars == null) {
            return List.of();
        }
        List<ChartPoint> points = new ArrayList<>();
        for (JsonNode bar : bars) {
            points.add(new ChartPoint(bar.path("t").asText(),
                    decimal(bar, "o"), decimal(bar, "h"), decimal(bar, "l"), decimal(bar, "c"),
                    bar.path("v").asLong(0)));
        }
        return points;
    }

    /** DB 에서 읽은 일봉을 화면이 쓰는 점으로 바꾼다. */
    private List<ChartPoint> toPoints(List<com.example.mijang.stock.dto.CandleResponse> candles) {
        List<ChartPoint> points = new ArrayList<>(candles.size());
        for (var c : candles) {
            points.add(new ChartPoint(c.tradeDate().toString(),
                    c.open(), c.high(), c.low(), c.close(), c.volume()));
        }
        return points;
    }

    /** 봉의 값 하나를 읽는다. 없으면 0 이 아니라 null 이다. */
    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.decimalValue();
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    /** 그 거래일의 정규장 종가를 돌려준다. 없으면 벤더에서 받아 저장하고, 벤더도 못 주면 null 이다. */
    @Transactional
    public BigDecimal closeOn(String rawSymbol, LocalDate date) {
        if (date == null) {
            return null;
        }
        String symbol = normalize(rawSymbol);
        BigDecimal stored = dailyPriceMapper.findCloseOn(symbol, date);
        if (stored != null) {
            return stored;
        }
        if (backfill(symbol, date, date) == null) {
            return null;
        }
        return dailyPriceMapper.findCloseOn(symbol, date);
    }

    /** 수명이 다한 캐시만 걷어낸다. 통째로 비우면 직후 요청이 전부 DB 로 몰린다. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000)
    public void sweepExpired() {
        int before = cache.size();
        cache.values().removeIf(entry -> !entry.alive());
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("[차트] 만료된 캐시 {}장 걷어냄 (남은 {}장)", removed, cache.size());
        }
    }

    /** 캐시를 전부 비운다. 자정 넘김에 대비해 스케줄러가 부른다. */
    public void evictAll() {
        cache.clear();
    }

    /** 벤더 최고(最古) 날짜 기록을 하루 한 번 비워 다시 확인하게 한다. */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 5 * * *",
            zone = "Asia/Seoul")
    public void forgetHistoryFloor() {
        historyFloor.clear();
    }

    /** 지금 캐시에 있는 장 수를 돌려준다. */
    public int cachedCount() {
        return cache.size();
    }
}
