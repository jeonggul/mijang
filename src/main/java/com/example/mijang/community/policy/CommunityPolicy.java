package com.example.mijang.community.policy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** 커뮤니티 작성 규칙(가입 직후 제한·금칙어)을 판정한다. 저장·예외 처리는 서비스가 한다. */
public final class CommunityPolicy {

    /** 금칙어 사전. 투자 권유·수익 보장 표현만 담는다. */
    private static final List<String> BANNED = List.of(
            "리딩방", "수익보장", "원금보장", "무조건오릅니다", "무조건상승",
            "급등주추천", "종목추천드립니다", "단타방", "picks추천");

    private CommunityPolicy() {
    }

    /** 가입 직후 글쓰기 제한에 걸리는지 판정한다. 가입 시각을 모르면 막지 않는다. */
    public static boolean tooEarlyToWrite(LocalDateTime joinedAt, int delayDays, LocalDateTime now) {
        if (delayDays <= 0 || joinedAt == null) {
            return false;
        }
        return Duration.between(joinedAt, now).toDays() < delayDays;
    }

    /** 금칙어가 들어 있는지 판정한다. 공백을 지우고 소문자로 맞춰 비교한다. */
    public static boolean containsBannedWord(String... texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String flat = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            for (String banned : BANNED) {
                if (flat.contains(banned.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}
