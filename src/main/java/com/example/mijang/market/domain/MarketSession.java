/*
 * MarketSession — 지금 미국 장이 어느 구간인가
 *
 * 이 파일이 하는 일
 *   장 시간을 네 구간으로 나눈다. 등락률의 기준가가 구간마다 다르기 때문이다.
 *
 *   정규장에는 "어제 종가 대비" 를 보고, 장이 끝난 뒤에는 "오늘 정규장 종가 대비" 를 본다.
 *   증권사 앱이 장 마감 후 "시간외 +0.3%" 를 따로 보여주는 것과 같은 이유다 —
 *   마감 뒤의 움직임을 하루치 등락에 섞으면 그날 장이 어땠는지가 흐려진다.
 */
package com.example.mijang.market.domain;

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

    /** 화면에 그대로 쓰는 이름 */
    public String label() {
        return label;
    }

    /** 값이 살아 움직이는 구간인가. 휴장일에는 실시간을 붙이지 않는다 */
    public boolean live() {
        return this != CLOSED;
    }
}
