package com.example.mijang.community.domain;

/** 게시판 구분을 나타낸다. posts.board 와 같은 값이다. */
public enum BoardType {

    /** 자유 게시판 */
    FREE,

    /** 질문 게시판 */
    QNA,

    /** 종목별 게시판 */
    STOCK;

    /** 종목이 붙는 게시판인지 반환한다. */
    public boolean needsSymbol() {
        return this == STOCK;
    }
}
