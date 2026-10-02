/*
 * CommunityPolicy — 글을 받아 줄지 정하는 규칙
 *
 * 이 파일이 하는 일
 *   운영 설정이 켜 둔 두 가지를 판정한다 — 가입 직후 글쓰기 제한과 금칙어.
 *   판정만 하고 저장·예외는 서비스가 한다. 규칙을 서비스 안에 흩어 두면
 *   글쓰기와 댓글이 서로 다른 기준을 갖게 된다.
 *
 *   금칙어는 ML 이 아니라 사전이다([[2.1 기능 명세서]] COMM-08 — "금지어 사전 + 도배 감지"
 *   수준으로 축소). 리딩방·수익 보장 같은 표현만 잡는다.
 */
package com.example.mijang.community.policy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** 커뮤니티 작성 규칙. 화면 SR-013 운영 설정이 값을 정한다. */
public final class CommunityPolicy {

    /**
     * 금칙어 사전.
     *
     * <p>투자 권유·수익 보장 표현만 담는다. 욕설까지 넣으면 오탐이 늘고, 그 영역은
     * 신고·자동 숨김이 맡는다. 띄어쓰기를 지운 뒤 비교하므로 "리 딩 방" 도 걸린다.
     */
    private static final List<String> BANNED = List.of(
            "리딩방", "수익보장", "원금보장", "무조건오릅니다", "무조건상승",
            "급등주추천", "종목추천드립니다", "단타방", "picks추천");

    private CommunityPolicy() {
    }

    /**
     * 가입 직후 글쓰기 제한에 걸리는지.
     *
     * <p>{@code delayDays} 가 0 이면 제한이 없다. 가입 시각을 모르면(null) 막지 않는다 —
     * 알 수 없는 이유로 사용자를 막는 것보다 통과시키는 편이 낫다.
     */
    public static boolean tooEarlyToWrite(LocalDateTime joinedAt, int delayDays, LocalDateTime now) {
        if (delayDays <= 0 || joinedAt == null) {
            return false;
        }
        return Duration.between(joinedAt, now).toDays() < delayDays;
    }

    /**
     * 금칙어가 들어 있는지.
     *
     * <p>공백을 지우고 소문자로 맞춰 비교한다. 그렇게 하지 않으면 "리 딩 방" · "리딩 방"
     * 처럼 한 칸만 벌려도 그대로 통과한다.
     */
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
