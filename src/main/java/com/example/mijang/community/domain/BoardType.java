/*
 * BoardType — 게시판 구분
 *
 * 이 파일이 하는 일
 *   커뮤니티를 일반(자유·질문)과 종목별로 가르는 값이다.
 *   문자열로 다니면 오타가 런타임까지 살아남고, 컨트롤러가 잘못된 값을 400 으로
 *   걸러 주지도 못한다. 값이 셋뿐이라 enum 이 그대로 검증이 된다.
 */
package com.example.mijang.community.domain;

/**
 * 게시판. {@code posts.board} 와 같은 값이다.
 *
 * <p>일반 커뮤니티는 종목이 없고({@link #FREE}·{@link #QNA}), 종목별 게시판은
 * 반드시 종목이 있다({@link #STOCK}). 이 규칙은 DB 의 {@code ck_posts_board_symbol}
 * 이 다시 한번 지킨다.
 */
public enum BoardType {

    /** 자유 게시판 */
    FREE,

    /** 질문 게시판 */
    QNA,

    /** 종목별 게시판 */
    STOCK;

    /** 종목이 붙는 게시판인가. */
    public boolean needsSymbol() {
        return this == STOCK;
    }
}
