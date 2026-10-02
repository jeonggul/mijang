/*
 * PopularQuotePollingScheduler — 인기 종목 미리 데우기
 *
 * 이 파일이 하는 일
 *   아직 아무것도 하지 않는다. 자리만 잡아 둔 곳이다.
 *
 *   원래 의도는 "아무도 안 보고 있어도 많이 찾는 종목의 시세는 미리 받아 두자" 였다.
 *   지금은 SubscriptionPoolManager 가 보는 사람이 생길 때 구독을 붙이고,
 *   DelayedQuotePoller 가 조용한 시간을 메운다. 그래서 없어도 화면은 멀쩡하다.
 *
 *   왜 @Scheduled 를 뗐는가
 *     몸통이 빈 채로 7초마다 돌며 경고를 찍고 있었다. 로그에서 진짜 경고가 묻힌다.
 *     할 일이 없는 배치는 돌 이유가 없다. 남은 것은 이 설명뿐이고,
 *     구현할 때 @Scheduled(fixedDelayString = "${mijang.batch.popular-poll-delay-ms:7000}")
 *     를 다시 붙이면 된다.
 */
package com.example.mijang.market.batch;

/** 자리만 잡아 둔 곳. 빈이 아니다 — 등록되면 도는 것처럼 보인다. */
public final class PopularQuotePollingScheduler {

    private PopularQuotePollingScheduler() {
    }
}
