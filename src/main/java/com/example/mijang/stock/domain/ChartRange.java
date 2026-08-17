/*
 * ChartRange — 차트 기간과 시간대의 대응표
 *
 * 이 파일이 하는 일
 *   화면이 고른 기간("1D"·"1Y"…)을 세 가지로 바꿔 준다 —
 *   벤더에 보낼 시간대(1Min·1Day…), 얼마나 거슬러 올라갈지, 그리고
 *   그 값을 DB 에 쌓아 둘지 그때그때 받을지.
 *
 *   기간마다 이 셋이 함께 정해지는데 코드 여기저기에 흩어지면 반드시 어긋난다.
 *   1년치를 1분봉으로 받는 것 같은 일이 그렇게 생긴다.
 */
package com.example.mijang.stock.domain;

import java.time.Duration;
import java.util.Locale;

public enum ChartRange {

    /**
     * 최근 다섯 시간. 실시간 체결이 이 위에 얹힌다.
     *
     * <p>하루 치를 다 그리면 봉이 800개가 넘어 지금 무슨 일이 벌어지는지가 묻힌다.
     * 하루 전체는 {@code ONE_DAY} 가 맡는다.
     */
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

    /** 벤더에 보낼 시간대 문자열. Alpaca 가 받는 표기 그대로다 */
    public String timeframe() {
        return timeframe;
    }

    /** 지금으로부터 얼마나 거슬러 올라갈지 */
    public Duration lookback() {
        return lookback;
    }

    /**
     * 받은 값을 {@code daily_prices} 에 쌓아 둘지.
     *
     * <p>일봉만 쌓는다. 분봉은 하루에 종목당 400건 가까이 나와 전 종목을 담을 수 없고,
     * 손익 계산에 쓰지도 않는다. 주봉·월봉은 일봉에서 만들 수 있는 값이라 따로 담지 않는다.
     */
    public boolean stored() {
        return stored;
    }

    /** 장중에 값이 계속 바뀌는 구간인가. 캐시를 얼마나 짧게 잡을지 정하는 데 쓴다 */
    public boolean intraday() {
        return timeframe.endsWith("Min");
    }

    /**
     * 화면이 보낸 문자열을 기간으로 바꾼다.
     *
     * <p>모르는 값이 오면 막지 않고 3개월로 본다. 차트는 잘못된 입력에 오류를 띄우기보다
     * 무언가를 보여 주는 편이 낫다.
     */
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

    /** 화면이 보낸 표기로 되돌린다. 응답에 실어 어떤 기간의 답인지 알려 준다 */
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
