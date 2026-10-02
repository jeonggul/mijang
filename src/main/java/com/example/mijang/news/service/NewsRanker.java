package com.example.mijang.news.service;

import com.example.mijang.news.dto.NewsItemResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 벤더 뉴스를 걸러 내고 기업 뉴스가 앞에 오도록 순서를 다시 매긴다. */
@Component
public class NewsRanker {

    /** 시황을 나열할 뿐 종목 얘기가 아닌 매체다. */
    private static final Set<String> NOISE_SOURCES = Set.of(
            "chartmill", "zacks", "investing.com", "simply wall st.", "tipranks");

    /** 기업이 한 일을 다루는 매체. 같은 조건이면 앞에 세운다. */
    private static final Set<String> NEWSROOMS = Set.of(
            "cnbc", "reuters", "bloomberg", "associated press", "the wall street journal",
            "financial times", "the verge", "techcrunch", "axios", "bbc");

    /** 제목이 기업이 아니라 "주식" 을 말하는 신호다. */
    private static final Pattern STOCK_TALK = Pattern.compile(
            "\\b(stock|stocks|shares?|buy|sell|hold|analysts?|price target|rating|upgrade|downgrade|"
            + "valuation|bull|bear|bullish|bearish|rall(?:y|ies)|dip|portfolio|investors?|investment|"
            + "outperform|underperform|forecast|prediction|should you|top \\d+|"
            + "puts|calls|hedge|short book|price action|market cap)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 회사 이름에서 걷어낼 법인 표기다. */
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "\\b(inc|corp|corporation|company|co|ltd|limited|plc|holdings?|group|common|stock|shares?|"
            + "class|the|etf|etn|trust|fund|depositary|american|new|sponsored|series|[ab])\\b\\.?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "with", "its", "that", "this", "how", "what", "will", "has", "have",
            "from", "new", "into", "over", "out", "但");

    /** 제목이 이만큼 겹치면 같은 기사로 본다. */
    private static final double SAME_STORY = 0.6;

    /** 짧은 쪽으로 나눠도 되는 최소 낱말 수. 이보다 적으면 낱말 하나로 1.0 이 나온다. */
    private static final int MIN_WORDS_FOR_SHORTER_SIDE = 3;

    /** 앞자리에서 한 매체가 차지할 수 있는 최대 건수다. */
    private static final int PER_SOURCE_IN_HEAD = 2;

    /** 상한을 적용할 앞자리 범위. 화면이 한 번에 펼치는 건수와 같다. */
    private static final int HEAD = 6;

    /** 종목명·티커로 그 회사 뉴스만 남기고 기업 뉴스가 앞에 오게 순서를 매긴다. */
    public List<NewsItemResponse> rank(List<NewsItemResponse> items, String name, String symbol) {
        Set<String> nameTokens = tokensOf(name);
        String ticker = symbol == null ? "" : symbol.trim().toLowerCase(Locale.ROOT);

        List<NewsItemResponse> passed = new ArrayList<>();
        for (NewsItemResponse item : items) {
            if (noisy(item) || !aboutThisCompany(item, nameTokens, ticker)) {
                continue;
            }
            passed.add(item);
        }

        // 기업 얘기 → 언론사 → 최신 순. 주식 얘기는 뒤로 밀 뿐 버리지 않는다
        passed.sort((a, b) -> {
            int byTalk = Boolean.compare(stockTalk(a), stockTalk(b));
            if (byTalk != 0) return byTalk;
            int bySource = Boolean.compare(!newsroom(a), !newsroom(b));
            if (bySource != 0) return bySource;
            return b.publishedAt().compareTo(a.publishedAt());
        });

        return spread(passed);
    }

    /** 거의 같은 기사를 걷어내고, 앞자리(HEAD)에 한 매체가 몰리지 않게 상한을 한 칸씩 풀며 채운다. */
    private List<NewsItemResponse> spread(List<NewsItemResponse> items) {
        List<NewsItemResponse> unique = dedupe(items);
        if (unique.size() <= HEAD) {
            return unique;
        }

        List<NewsItemResponse> head = new ArrayList<>();
        Set<NewsItemResponse> taken = new HashSet<>();
        Map<String, Integer> perSource = new HashMap<>();

        // 상한 1 부터 시작해 앞자리가 찰 때까지 한 칸씩 푼다
        for (int cap = 1; cap <= HEAD && head.size() < HEAD; cap++) {
            for (NewsItemResponse item : unique) {
                if (head.size() >= HEAD) {
                    break;
                }
                if (taken.contains(item)) {
                    continue;
                }
                String source = sourceKey(item);
                if (perSource.getOrDefault(source, 0) >= cap) {
                    continue;
                }
                perSource.merge(source, 1, Integer::sum);
                taken.add(item);
                head.add(item);
            }
        }

        // 앞자리에 못 든 것은 원래 순서대로 뒤에 붙인다
        for (NewsItemResponse item : unique) {
            if (!taken.contains(item)) {
                head.add(item);
            }
        }
        return head;
    }

    /** 거의 같은 제목은 하나만 남긴다. */
    private List<NewsItemResponse> dedupe(List<NewsItemResponse> items) {
        List<NewsItemResponse> out = new ArrayList<>();
        List<Set<String>> seen = new ArrayList<>();
        for (NewsItemResponse item : items) {
            Set<String> words = significantWords(item.headline());
            if (seen.stream().anyMatch(prev -> overlap(words, prev) >= SAME_STORY)) {
                continue;
            }
            seen.add(words);
            out.add(item);
        }
        return out;
    }

    private String sourceKey(NewsItemResponse item) {
        return item.source() == null ? "" : item.source().toLowerCase(Locale.ROOT);
    }

    /** 두 제목의 겹침 비율을 짧은 쪽 기준으로 구한다. 낱말이 너무 적으면 합집합 기준으로 바꾼다. */
    private double overlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> both = new HashSet<>(a);
        both.retainAll(b);
        int shorter = Math.min(a.size(), b.size());
        if (shorter < MIN_WORDS_FOR_SHORTER_SIDE) {
            Set<String> either = new HashSet<>(a);
            either.addAll(b);
            return (double) both.size() / either.size();
        }
        return (double) both.size() / shorter;
    }

    private boolean noisy(NewsItemResponse item) {
        return item.source() != null && NOISE_SOURCES.contains(item.source().toLowerCase(Locale.ROOT));
    }

    private boolean newsroom(NewsItemResponse item) {
        return item.source() != null && NEWSROOMS.contains(item.source().toLowerCase(Locale.ROOT));
    }

    private boolean stockTalk(NewsItemResponse item) {
        return STOCK_TALK.matcher(item.headline()).find();
    }

    /** 이 회사 얘기인지 본다. 티커는 낱말 단위로 본다 — 부분 일치면 "AAL" 이 "small" 에 걸린다. */
    private boolean aboutThisCompany(NewsItemResponse item, Set<String> nameTokens, String ticker) {
        String text = (item.headline() + " " + (item.summary() == null ? "" : item.summary()))
                .toLowerCase(Locale.ROOT);
        if (!ticker.isEmpty()) {
            var m = WORD.matcher(text);
            while (m.find()) {
                if (m.group().equals(ticker)) {
                    return true;
                }
            }
        }
        return nameTokens.stream().anyMatch(text::contains);
    }

    /** 회사 이름에서 법인 표기를 걷어낸 낱말들을 만든다. */
    private Set<String> tokensOf(String name) {
        if (name == null || name.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        var m = WORD.matcher(LEGAL_SUFFIX.matcher(name).replaceAll(" ").toLowerCase(Locale.ROOT));
        while (m.find()) {
            if (m.group().length() > 1) {
                tokens.add(m.group());
            }
        }
        return tokens;
    }

    private Set<String> significantWords(String headline) {
        Set<String> words = new HashSet<>();
        var m = WORD.matcher(headline.toLowerCase(Locale.ROOT));
        while (m.find()) {
            if (m.group().length() > 2 && !STOP.contains(m.group())) {
                words.add(m.group());
            }
        }
        return words;
    }
}
