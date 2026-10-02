/*
 * Tickers — 티커 모양을 검사하는 곳
 *
 * 이 파일이 하는 일
 *   바깥에서 들어온 종목 코드 중 실제 티커 모양인 것만 골라 준다.
 *   이 값은 벤더에 보내는 제어 프레임에 그대로 실리기 때문에,
 *   따옴표나 대괄호가 섞인 문자열이 새어 들어가면 프레임 구조를 바깥에서 정하게 된다.
 *   들어오는 입구가 둘(SSE 스트림, 구독 갱신)이라 규칙을 한 곳에 모아 둔다.
 */
package com.example.mijang.market.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 티커 모양 검사. 실시간 시세 입구가 공통으로 쓴다.
 *
 * <p><b>규칙을 한 곳에 둔 이유</b> — 이 값은 벤더에 보내는 제어 프레임에 실린다.
 * 입구가 둘인데 한쪽에만 검사가 있으면, 검사 없는 쪽으로 아무 문자열이나 들어와
 * 30칸뿐인 공용 구독 자리를 차지하거나 프레임 구조를 흔든다.
 *
 * <p>미국 티커는 대문자 한 글자로 시작하고, 점·붙임표를 포함해 열 자를 넘지 않는다
 * (예: {@code AAPL} · {@code BRK.B} · {@code KIM.PRL} · {@code BBBY.WS}).
 */
public final class Tickers {

    /** 미국 티커의 모양. 대문자로 시작하고 점·붙임표를 포함해 열 자까지 */
    private static final Pattern TICKER = Pattern.compile("[A-Z][A-Z0-9.\\-]{0,9}");

    private Tickers() {
    }

    /** 이 문자열이 티커 모양인가. 이미 대문자로 다듬은 값을 넣어야 한다. */
    public static boolean valid(String symbol) {
        return symbol != null && TICKER.matcher(symbol).matches();
    }

    /**
     * 쉼표로 구분된 목록에서 티커만 골라낸다.
     *
     * <p>{@code GET /api/market/stream?symbols=...} 이 쓴다. 모양이 아닌 것은 조용히 버린다 —
     * 오류로 돌려주면 어느 티커가 걸렸는지 알려 주게 되고, 그럴 이유가 없다.
     */
    public static Set<String> parseCsv(String csv) {
        return parseCsv(csv, Integer.MAX_VALUE);
    }

    /**
     * 쉼표 목록에서 티커만 골라내되, 앞에서 {@code max} 개까지만 받는다.
     *
     * <p>구독 자리는 서버 전체에서 30칸을 나눠 쓴다. 한 요청이 그 이상을 실어 보내면
     * 화면 하나가 자리를 다 차지해 다른 사람은 전부 종가만 보게 된다. 스트림 입구는
     * 로그인 없이 부를 수 있어 더 그렇다.
     *
     * <p>넘치는 만큼을 오류로 돌려주지는 않는다 — 한 화면에 종목이 많은 것은 정상이고,
     * 넘친 종목은 원래도 종가로 나가기 때문이다(2.1).
     */
    public static Set<String> parseCsv(String csv, int max) {
        if (csv == null || csv.isBlank() || max <= 0) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(Tickers::valid)
                .distinct()
                .limit(max)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * 목록에서 티커만 골라낸다.
     *
     * <p>{@code POST /api/market/subscriptions} 가 쓴다. 소문자로 와도 받아 준다 —
     * 화면이 사용자가 친 그대로 보내는 경우가 있다.
     */
    public static Set<String> clean(Collection<String> symbols) {
        if (symbols == null) {
            return Set.of();
        }
        return symbols.stream()
                .filter(java.util.Objects::nonNull)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(Tickers::valid)
                .collect(Collectors.toUnmodifiableSet());
    }
}
