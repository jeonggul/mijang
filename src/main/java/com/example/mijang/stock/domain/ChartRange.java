package com.example.mijang.stock.domain;

import java.time.Duration;
import java.util.Locale;

/** 차트 기간을 벤더 시간대·조회 범위·저장 여부로 바꿔 주는 대응표다. */
public enum ChartRange {

    /** 최근 다섯 시간. 실시간 체결이 이 위에 얹힌다. */
    LIVE("1Min", Duration.ofHours(5), false),
    ONE_DAY("1Min", Duration.ofDays(1), false),
    ONE_WEEK("5Min", Duration.ofDays(7), false),
    ONE_MONTH("1Day", Duration.ofDays(30), true),
    THREE_MONTH("1Day", Duration.ofDays(90), true),
    ONE_YEAR("1Day", Duration.ofDays(365), true),
    FIVE_YEAR("1Week", Duration.ofDays(365L * 5), false),
    ALL("1Month", Duration.ofDays(365L * 20), false);

    private final String timeframe;
    private final Duration lookback;
    private final boolean stored;

    ChartRange(String timeframe, Duration lookback, boolean stored) {
        this.timeframe = timeframe;
        this.lookback = lookback;
        this.stored = stored;
    }

    /** 벤더에 보낼 시간대 문자열을 돌려준다. Alpaca 표기 그대로다. */
    public String timeframe() {
        return timeframe;
    }

    /** 지금으로부터 얼마나 거슬러 올라갈지를 돌려준다. */
    public Duration lookback() {
        return lookback;
    }

    /** 받은 값을 daily_prices 에 쌓아 둘지 여부다. 일봉만 쌓는다. */
    public boolean stored() {
        return stored;
    }

    /** 장중에 값이 계속 바뀌는 분봉 구간인지 여부다. 캐시 수명 결정에 쓴다. */
    public boolean intraday() {
        return timeframe.endsWith("Min");
    }

    /** 화면이 보낸 문자열을 기간으로 바꾼다. 모르는 값은 3개월로 본다. */
    public static ChartRange of(String raw) {
        if (raw == null) {
            return THREE_MONTH;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "LIVE" -> LIVE;
            case "1D" -> ONE_DAY;
            case "1W" -> ONE_WEEK;
            case "1M" -> ONE_MONTH;
            case "1Y" -> ONE_YEAR;
            case "5Y" -> FIVE_YEAR;
            case "ALL" -> ALL;
            default -> THREE_MONTH;
        };
    }

    /** 화면 표기 문자열로 되돌린다. */
    public String code() {
        return switch (this) {
            case LIVE -> "LIVE";
            case ONE_DAY -> "1D";
            case ONE_WEEK -> "1W";
            case ONE_MONTH -> "1M";
            case THREE_MONTH -> "3M";
            case ONE_YEAR -> "1Y";
            case FIVE_YEAR -> "5Y";
            case ALL -> "ALL";
        };
    }
}
