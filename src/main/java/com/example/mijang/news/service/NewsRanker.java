/*
 * NewsRanker — 뉴스에서 쓸 만한 것을 골라 앞에 세우는 곳
 *
 * 이 파일이 하는 일
 *   벤더가 준 뉴스를 걸러 내고 순서를 다시 매긴다.
 *
 *   그냥 받아서 뿌리면 <b>기업이 뭘 했는지는 안 보이고 주식 얘기만 나온다.</b>
 *   AAPL 로 216건을 받아 보니 이런 것들이 섞여 있었다.
 *
 *     "What's going on in today's session: S&P500 most active stocks"   ← 종목과 무관
 *     "Broadcom: XPU Growth Trajectory Underestimated"                  ← 다른 회사 얘기
 *     "Apple Stock: 64% of Benzinga Viewers Have Owned It"              ← 주식 얘기
 *     "Apple opens new manufacturing facility in Texas"                 ← 이게 기업 뉴스다
 *
 *   네 단계로 거른다.
 *     ① 시황 나열만 하는 매체를 뺀다
 *     ② 그 종목 얘기가 아닌 것을 뺀다 (제목·요약에 회사 이름이나 티커가 없는 것)
 *     ③ 제목이 "주식" 을 말하는 것을 뒤로 민다 — 없애지는 않는다. 보고 싶은 사람도 있다
 *     ④ 거의 같은 제목과 한 매체 몰림을 줄인다
 *
 *   실측으로 216건 → 126건이 남고, 상위 여섯 건이 전부 기업 뉴스가 됐다.
 */
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

@Component
public class NewsRanker {

    /** 시황을 나열할 뿐 종목 얘기가 아닌 매체 */
    private static final Set<String> NOISE_SOURCES = Set.of(
            "chartmill", "zacks", "investing.com", "simply wall st.", "tipranks");

    /** 기업이 한 일을 다루는 매체. 같은 조건이면 앞에 세운다 */
    private static final Set<String> NEWSROOMS = Set.of(
            "cnbc", "reuters", "bloomberg", "associated press", "the wall street journal",
            "financial times", "the verge", "techcrunch", "axios", "bbc");

    /** 제목이 기업이 아니라 "주식" 을 말하는 신호 */
    private static final Pattern STOCK_TALK = Pattern.compile(
            "\\b(stock|stocks|shares?|buy|sell|hold|analysts?|price target|rating|upgrade|downgrade|"
            + "valuation|bull|bear|bullish|bearish|rall(?:y|ies)|dip|portfolio|investors?|investment|"
            + "outperform|underperform|forecast|prediction|should you|top \\d+|"
            + "puts|calls|hedge|short book|price action|market cap)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 회사 이름에서 걷어낼 말. 남는 것이 실제 상호다 */
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "\\b(inc|corp|corporation|company|co|ltd|limited|plc|holdings?|group|common|stock|shares?|"
            + "class|the|etf|etn|trust|fund|depositary|american|new|sponsored|series|[ab])\\b\\.?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "with", "its", "that", "this", "how", "what", "will", "has", "have",
            "from", "new", "into", "over", "out", "但");

    /** 제목이 이만큼 겹치면 같은 기사로 본다 */
    private static final double SAME_STORY = 0.6;

    /**
     * 짧은 쪽으로 나눠도 되는 최소 낱말 수.
     *
     * <p>이보다 적으면 겹치는 낱말 하나로 1.0 이 나와 버린다.
     */
    private static final int MIN_WORDS_FOR_SHORTER_SIDE = 3;

    /** 앞자리에서 한 매체가 차지할 수 있는 최대 건수 */
    private static final int PER_SOURCE_IN_HEAD = 2;

    /** 상한을 적용할 앞자리 범위. 화면이 한 번에 펼치는 건수와 같다 */
    private static final int HEAD = 6;

    /**
     * 거르고 순서를 매긴다.
     *
     * @param name   종목명. 제목이 이 회사 얘기인지 보는 데 쓴다
     * @param symbol 티커. 이름이 안 나와도 티커가 있으면 이 회사 얘기다
     */
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

        /* 기업 얘기 → 언론사 → 최신 순. 주식 얘기는 뒤로 밀 뿐 버리지 않는다 */
        passed.sort((a, b) -> {
            int byTalk = Boolean.compare(stockTalk(a), stockTalk(b));
            if (byTalk != 0) return byTalk;
            int bySource = Boolean.compare(!newsroom(a), !newsroom(b));
            if (bySource != 0) return bySource;
            return b.publishedAt().compareTo(a.publishedAt());
        });

        return spread(passed);
    }

    /**
     * 거의 같은 기사를 걷어내고, 앞자리에 한 매체가 몰리지 않게 한다.
     *
     * <p>매체 상한을 <b>앞 여섯 건에만</b> 건다. 목록 전체에 걸면 매체가 서넛뿐인 종목에서
     * 뉴스가 예닐곱 건으로 줄어 스크롤할 것이 남지 않는다.
     *
     * <p>상한 때문에 앞자리를 다 못 채우면 <b>상한을 한 칸씩 푼다.</b> 매체가 둘뿐인 종목도
     * 있는데, 그때 상한을 고집하면 자리가 빈 채로 남고 넘친 기사가 바로 뒤따라와
     * 결국 같은 매체가 앞을 차지한다 — 상한을 건 의미가 없어진다.
     */
    private List<NewsItemResponse> spread(List<NewsItemResponse> items) {
        List<NewsItemResponse> unique = dedupe(items);
        if (unique.size() <= HEAD) {
            return unique;
        }

        List<NewsItemResponse> head = new ArrayList<>();
        Set<NewsItemResponse> taken = new HashSet<>();
        Map<String, Integer> perSource = new HashMap<>();

        /* 상한 1 부터 시작해 앞자리가 찰 때까지 한 칸씩 푼다.
           매체가 여럿이면 1~2 에서 끝나고, 하나뿐이면 결국 그 매체로 채워진다 */
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

        /* 앞자리에 못 든 것은 원래 순서대로 뒤에 붙인다. 버리지 않는다 */
        for (NewsItemResponse item : unique) {
            if (!taken.contains(item)) {
                head.add(item);
            }
        }
        return head;
    }

    /** 거의 같은 제목은 하나만 남긴다 */
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

    /**
     * 두 제목이 얼마나 겹치는가.
     *
     * <p>합집합이 아니라 <b>짧은 쪽</b>으로 나눈다. "애플, 텍사스 공장 열어" 와
     * "애플이 텍사스에 새 제조 시설을 열었다: 알아야 할 것들" 은 길이가 크게 다른데
     * 같은 기사다. 합집합으로 재면 이런 짝을 놓친다.
     */
    /**
     * 두 제목이 얼마나 겹치는가.
     *
     * <p>짧은 쪽으로 나눈다 — 같은 사건을 길게 쓴 기사와 짧게 쓴 기사를 묶으려면 그래야 한다.
     *
     * <p><b>다만 낱말이 너무 적으면 그 나눗셈을 쓰지 않는다.</b> "Apple Q3" 는 걸러지고 나면
     * {@code {apple}} 하나만 남는데, 짧은 쪽으로 나누면 apple 이 든 모든 제목과 1.0 이 되어
     * 그 뒤 애플 뉴스가 통째로 중복으로 지워진다. 그럴 때는 합집합으로 나눠
     * 실제로 얼마나 닮았는지를 본다.
     */
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

    /**
     * 이 회사 얘기인가.
     *
     * <p>티커는 낱말 단위로 본다. 부분 일치로 보면 "AAL" 이 "small" 에 걸린다.
     */
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

    /** 회사 이름에서 법인 표기를 걷어낸 낱말들 */
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
