package com.example.mijang.market.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 실시간 시세 입구가 공통으로 쓰는 티커 모양 검사를 제공한다. 검사값은 벤더 제어 프레임에 그대로 실린다. */
public final class Tickers {

    /** 미국 티커 모양. 대문자로 시작하고 점·붙임표 포함 열 자까지. */
    private static final Pattern TICKER = Pattern.compile("[A-Z][A-Z0-9.\\-]{0,9}");

    private Tickers() {
    }

    /** 문자열이 티커 모양인지 검사한다. 대문자로 다듬은 값을 넣어야 한다. */
    public static boolean valid(String symbol) {
        return symbol != null && TICKER.matcher(symbol).matches();
    }

    /** 쉼표로 구분된 목록에서 티커만 골라낸다. 모양이 아닌 것은 조용히 버린다. */
    public static Set<String> parseCsv(String csv) {
        return parseCsv(csv, Integer.MAX_VALUE);
    }

    /** 쉼표 목록에서 티커만 골라내되 앞에서 max 개까지만 받는다. 구독 자리는 서버 전체 30칸을 나눠 쓰므로 초과분은 조용히 버린다. */
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

    /** 목록에서 티커만 골라낸다. 소문자도 대문자로 다듬어 받는다. */
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
