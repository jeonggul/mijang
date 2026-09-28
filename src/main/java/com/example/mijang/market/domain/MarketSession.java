package com.example.mijang.market.domain;

/** 미국 장 시간을 네 구간으로 나눈다. 구간마다 등락률 기준가가 다르다. */
public enum MarketSession {

    /** 프리마켓. 보통 04:00~09:30 ET */
    PRE("프리마켓"),

    /** 정규장. 보통 09:30~16:00 ET. 조기폐장일은 13:00 에 끝난다 */
    REGULAR("정규장"),

    /** 애프터마켓. 보통 16:00~20:00 ET */
    AFTER("시간외"),

    /** 휴장·주말·장 시간 밖. 값이 멈춰 있어야 한다 */
    CLOSED("장 마감");

    private final String label;

    MarketSession(String label) {
        this.label = label;
    }

    /** 화면에 그대로 쓰는 이름을 반환한다. */
    public String label() {
        return label;
    }

    /** 값이 실시간으로 움직이는 구간인지 반환한다. */
    public boolean live() {
        return this != CLOSED;
    }
}
