/*
 * ChartService — 차트에 그릴 점을 구해 주는 곳
 *
 * 이 파일이 하는 일
 *   기간(실시간·1일·1주·1개월·3달·1년·5년·전체)을 받아 그릴 점 목록을 돌려준다.
 *
 *   어디서 가져올지는 기간이 정한다.
 *     · 일봉 구간(1개월·3달·1년) — DB 를 먼저 본다. 과거분이 비어 있으면 그때 한 번
 *       벤더에서 받아 DB 에 채우고 다시 읽는다. 사용자가 실제로 본 종목만 쌓이므로
 *       13,000 종목의 5년치를 미리 받아 둘 필요가 없다.
 *     · 분봉 구간(실시간·1일·1주) — 벤더에서 바로 받는다. 저장하지 않는다.
 *       하루에 종목당 400건 가까이 나오고 손익 계산에 쓰지도 않는다.
 *     · 주봉·월봉(5년·전체) — 벤더에서 바로 받는다. 일봉에서 만들 수도 있지만
 *       그러려면 5년치 일봉이 먼저 있어야 한다.
 *
 *   같은 종목을 여러 사람이 열어도 벤더를 다시 부르지 않도록 짧게 캐시한다.
 */
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    /** 장중에 계속 바뀌는 값. 짧게 잡되 0 은 아니다 — 여러 사람이 같은 종목을 연다 */
    private static final Duration INTRADAY_TTL = Duration.ofSeconds(30);

    /** 확정된 과거 봉. 하루에 한 번 바뀌면 충분하다 */
    private static final Duration HISTORY_TTL = Duration.ofMinutes(30);

    /** DB 에 이만큼 앞의 값이 없으면 과거분이 비어 있다고 본다 */
    private static final int BACKFILL_GAP_DAYS = 7;

    /**
     * SIP 를 요청할 때 끝 시각을 얼마나 앞으로 당길지.
     *
     * <p>무료 요금제는 "최근 SIP" 를 막는다. 실측으로 15분이 경계라 여유를 1분 더 둔다.
     * 이보다 짧게 잡으면 403 이 나고 장 시간 밖 데이터를 통째로 잃는다.
     */
    private static final Duration SIP_DELAY = Duration.ofMinutes(16);

    /** 미국 동부. 장 시간은 전부 이 기준으로 정해진다 */
    private static final ZoneId ET = ZoneId.of("America/New_York");

    /** 프리마켓이 열리는 시각. 하루 치 차트는 여기서 시작한다 */
    private static final LocalTime PRE_MARKET_OPEN = LocalTime.of(4, 0);

    private final AlpacaStockClient alpacaClient;
    private final DailyPriceMapper dailyPriceMapper;
    private final StockMapper stockMapper;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /**
     * 종목마다 벤더가 가진 가장 오래된 거래일.
     *
     * <p>이게 없으면 <b>상장한 지 얼마 안 된 종목에서 백필이 끝나지 않는다.</b>
     * 1년치를 요청했는데 벤더에 6개월치밖에 없으면 앞이 늘 비어 있고,
     * 그것을 "아직 안 채웠다"로 읽어 캐시가 만료될 때마다 다시 부르게 된다.
     * 한 번 받아 보고 여기에 적어 두면 그 앞은 다시 묻지 않는다.
     */
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

    /**
     * 차트 한 장. {@code PRICE-05}·{@code PRICE-07}
     *
     * <p>없는 종목이면 그린다 만다를 화면이 판단하게 두지 않고 여기서 막는다.
     * 상세와 차트가 서로 다른 답을 하면 사용자가 혼란스럽다.
     */
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

    /**
     * 일봉 구간. DB 를 먼저 보고, 과거분이 비어 있으면 한 번 채운 뒤 다시 읽는다.
     *
     * <p>수집 배치는 최근 5일치만 받는다. 그래서 처음 열어 보는 종목의 1년 차트는
     * 점이 몇 개뿐이다. 그때만 벤더를 부르고, 받은 값은 DB 에 남겨 다음부터는 쓰지 않는다.
     */
    private List<ChartPoint> fromStore(String symbol, ChartRange range) {
        /* 미국 거래일 기준이다. 서버가 KST 라 LocalDate.now() 를 쓰면
           장 마감 뒤 아침까지 하루가 앞서 나간다 */
        LocalDate to = tradingClock.today();
        LocalDate from = to.minusDays(range.lookback().toDays());

        List<ChartPoint> stored = toPoints(dailyPriceMapper.findByRange(symbol, from, to));
        if (!needsBackfill(symbol, stored, from)) {
            return stored;
        }

        log.info("[차트] {} {} 과거분 없음 — 벤더에서 채운다", symbol, range.code());
        LocalDate oldest = backfill(symbol, from, to);
        if (oldest == null) {
            /* 벤더도 이 구간에 줄 것이 없다. 요청한 시작을 바닥으로 적어 다시 묻지 않는다 */
            historyFloor.merge(symbol, from, (a, b) -> a.isBefore(b) ? a : b);
            return stored;
        }
        /* 바닥으로 적을 수 있는 것은 <b>물어본 것보다 늦게 시작할 때뿐</b>이다.
           1년을 물었는데 6개월치만 왔다면 그 앞은 벤더에 없는 것이 맞다.
           그런데 3달을 물어 3달이 온 경우는 아무것도 알게 된 것이 없다 — 그때도 적어 버리면
           나중에 1년을 눌렀을 때 "이미 바닥까지 봤다" 로 읽혀 3달치만 그려진다 */
        if (oldest.isAfter(from)) {
            historyFloor.merge(symbol, oldest, (a, b) -> a.isBefore(b) ? a : b);
        }
        return toPoints(dailyPriceMapper.findByRange(symbol, from, to));
    }

    /**
     * 채워야 하는가.
     *
     * <p>한 건도 없거나, 가장 오래된 값이 요청한 시작보다 한참 뒤면 과거분이 빈 것이다.
     * 상장한 지 얼마 안 된 종목은 원래 앞이 비어 있는데, 그 경우 벤더도 주지 않으므로
     * 한 번 헛되이 부르고 끝난다.
     */
    private boolean needsBackfill(String symbol, List<ChartPoint> stored, LocalDate from) {
        /* 벤더에 없다고 확인된 구간이면 묻지 않는다. 상장 전 기간이 여기 걸린다 */
        LocalDate floor = historyFloor.get(symbol);
        LocalDate wanted = (floor != null && floor.isAfter(from)) ? floor : from;

        if (stored.isEmpty()) {
            /* 한 건도 없다. 아직 안 물어봤거나(floor 없음), 물어본 것보다 더 앞을 원할 때만
               벤더를 부른다 — 같은 구간을 되묻지 않으면서 더 넓은 구간은 한 번 시도한다 */
            return floor == null || from.isBefore(floor);
        }
        return LocalDate.parse(stored.get(0).at()).isAfter(wanted.plusDays(BACKFILL_GAP_DAYS));
    }

    /**
     * 벤더에서 일봉을 받아 DB 에 넣는다.
     *
     * @return 받은 것 중 가장 오래된 거래일. 하나도 없으면 null
     */
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

    /**
     * 분봉·주봉·월봉. 저장하지 않고 받은 것을 그대로 점으로 바꾼다.
     *
     * <p>분봉만 두 피드를 이어 붙인다(2.16). SIP 로 16분 전까지 받아 프리마켓·애프터마켓을
     * 채우고, 남은 16분은 IEX 로 받는다. IEX 만 쓰면 장 시간 밖이 통째로 빈다.
     */
    private List<ChartPoint> fromVendor(String symbol, ChartRange range) {
        Instant requestTime = Instant.now();
        ChartWindow window = windowOf(range, requestTime);
        Instant from = window.from();
        Instant to = window.to();

        if (!range.intraday()) {
            return toPoints(barsOf(alpacaClient.bars(
                    List.of(symbol), range.timeframe(), from.toString(), to.toString()), symbol));
        }

        /* SIP 제한은 조회 구간의 끝이 아니라 실제 현재 시각 기준이다. 직전 거래일처럼
           이미 16분이 지난 구간은 끝까지 SIP 로 받을 수 있다. */
        Instant sipEnd = requestTime.minus(SIP_DELAY);
        if (sipEnd.isAfter(to)) {
            sipEnd = to;
        }
        List<ChartPoint> merged = new ArrayList<>();

        /* 앞부분 — SIP. 전 거래소를 합친 값이라 장 시간 밖이 들어 있다 */
        if (sipEnd.isAfter(from)) {
            merged.addAll(toPoints(barsOf(alpacaClient.bars(
                    List.of(symbol), range.timeframe(), from.toString(), sipEnd.toString(), "sip"), symbol)));
        }

        /* 끝부분 — IEX. SIP 가 못 주는 최근 16분을 메운다. 얇지만 없는 것보다 낫다 */
        List<ChartPoint> tail = sipEnd.isBefore(to)
                ? toPoints(barsOf(alpacaClient.bars(
                        List.of(symbol), range.timeframe(), sipEnd.toString(), to.toString(), "iex"), symbol))
                : List.of();

        /* 경계에서 같은 시각의 봉이 양쪽에 있을 수 있다. SIP 쪽을 남긴다 — 더 많이 본 값이다 */
        String lastAt = merged.isEmpty() ? "" : merged.get(merged.size() - 1).at();
        for (ChartPoint p : tail) {
            if (p.at().compareTo(lastAt) > 0) {
                merged.add(p);
            }
        }

        /* 달력 테이블이 아직 비어 있거나 범위 밖이면 정확한 직전 거래일을 구할 수 없다.
           그때만 넓게 한 번 받아 실제로 마지막 봉이 있던 하루를 골라 빈 화면을 피한다. */
        if (merged.isEmpty() && (range == ChartRange.LIVE || range == ChartRange.ONE_DAY)) {
            /* 장은 열렸지만 15분 지연 데이터가 아직 도착하지 않은 구간이다.
               직전 거래일을 현재 차트처럼 보여주지 말고 화면이 대기 상태를 표시하게 둔다. */
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

    /**
     * 어디서부터 받을지.
     *
     * <p>하루 치는 <b>24시간 전이 아니라 그날 프리마켓이 열린 시각</b>부터다.
     * 롤링 24시간으로 잡으면 어제 애프터마켓과 오늘 프리마켓이 한 화면에 섞여
     * 어느 날 값인지 알 수 없게 된다.
     *
     * <p>새벽(프리마켓 전)이면 아직 오늘 장이 시작되지 않았으므로 어제 것을 보여준다.
     * 주말·휴장일에는 그 구간에 봉이 없어 빈 차트가 되는데, 그때는 기간만큼 넓혀 잡는다.
     */
    ChartWindow windowOf(ChartRange range, Instant now) {
        if (range != ChartRange.LIVE && range != ChartRange.ONE_DAY) {
            return new ChartWindow(now.minus(range.lookback()), now, false);
        }
        ZonedDateTime et = now.atZone(ET);
        LocalDate date = et.toLocalDate();

        /* 오늘 세션이 이미 시작했으면 오늘, 아직 시작 전이거나 휴장이면 직전 거래일이다.
           단순히 하루를 빼면 월요일 새벽에 일요일을 조회하고 공휴일도 건너뛰지 못한다. */
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

        /* 달력을 아직 한 번도 받지 못한 로컬 환경의 안전한 기본값. 조회 결과가 비면
           fromVendor 가 최근 7일에서 마지막 실제 거래 세션을 다시 찾는다. */
        ZonedDateTime open = et.toLocalTime().isBefore(PRE_MARKET_OPEN)
                ? et.minusDays(1).with(PRE_MARKET_OPEN)
                : et.with(PRE_MARKET_OPEN);

        if (range == ChartRange.ONE_DAY) {
            return new ChartWindow(open.toInstant(), now, false);
        }

        /* 실시간은 최근 다섯 시간이다. 다만 그날 프리마켓이 열린 시각보다 앞으로는 가지 않는다 —
           새벽에 다섯 시간을 거슬러 올라가면 어제 애프터마켓이 섞여 어느 날 값인지 알 수 없다 */
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

    /**
     * 응답에서 이 종목의 봉 배열을 꺼낸다.
     *
     * <p>Alpaca 는 {@code bars} 아래에 티커를 키로 담아 준다. 종목이 하나여도 모양은 같다.
     * 값이 없는 날(신규 상장 전, 휴장)에는 키 자체가 없다.
     */
    private JsonNode barsOf(JsonNode response, String symbol) {
        JsonNode bars = response == null ? null : response.get("bars");
        if (bars == null || !bars.isObject()) {
            return null;
        }
        JsonNode mine = bars.get(symbol);
        return (mine == null || !mine.isArray() || mine.isEmpty()) ? null : mine;
    }

    /** 벤더 응답의 봉 배열을 점으로 바꾼다. 배열이 없으면 빈 목록이다 */
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

    /** DB 에서 읽은 일봉을 화면이 쓰는 점으로 바꾼다 */
    private List<ChartPoint> toPoints(List<com.example.mijang.stock.dto.CandleResponse> candles) {
        List<ChartPoint> points = new ArrayList<>(candles.size());
        for (var c : candles) {
            points.add(new ChartPoint(c.tradeDate().toString(),
                    c.open(), c.high(), c.low(), c.close(), c.volume()));
        }
        return points;
    }

    /** 값이 없으면 null 이다. 0 으로 바꾸면 "0달러에 거래됐다"가 된다 */
    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.decimalValue();
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 그 거래일의 정규장 종가. 없으면 벤더에서 받아 저장하고 돌려준다.
     *
     * <p>일봉 수집 배치는 장 마감 뒤에 돈다. 그래서 <b>장중과 마감 직후에는 그날 일봉이 없다.</b>
     * 시간외 등락률의 기준가가 바로 그 값이라 없으면 등락률을 낼 수 없다.
     *
     * @return 벤더도 못 주면 null. 아직 그날 거래가 없었다는 뜻이다
     */
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
        /* 하루치만 받는다. 차트 백필과 달리 기간이 아니라 한 날짜다 */
        if (backfill(symbol, date, date) == null) {
            return null;
        }
        return dailyPriceMapper.findCloseOn(symbol, date);
    }

    /**
     * 수명이 다한 것을 걷어낸다.
     *
     * <p>덮어쓰기만 하고 지우지 않으면 캐시는 <b>줄어들 일이 없다.</b> 종목 13,000개에
     * 기간이 8종이라, 사람들이 들여다볼수록 만료된 봉 목록이 그대로 쌓인 채 남는다.
     *
     * <p>통째로 비우지 않고 죽은 것만 고른다. 한꺼번에 비우면 그 직후 요청이 전부
     * DB 로 몰린다.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000)
    public void sweepExpired() {
        int before = cache.size();
        cache.values().removeIf(entry -> !entry.alive());
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("[차트] 만료된 캐시 {}장 걷어냄 (남은 {}장)", removed, cache.size());
        }
    }

    /** 오늘 날짜 기준으로 만든 키가 자정을 넘겨 남지 않도록 비운다. 스케줄러가 부른다 */
    public void evictAll() {
        cache.clear();
    }

    /**
     * 벤더가 가진 가장 오래된 날짜를 다시 확인하게 한다. 상장 이력이 바뀌는 일은 드물다.
     *
     * <p>하루 한 번 비운다. 신규 상장이나 과거 데이터 보강이 반영되는 주기다.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 5 * * *",
            zone = "Asia/Seoul")
    public void forgetHistoryFloor() {
        historyFloor.clear();
    }

    /** 지금 캐시에 몇 장이 있는지. 운영 화면과 검증에서 쓴다 */
    public int cachedCount() {
        return cache.size();
    }
}
