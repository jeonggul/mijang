/*
 * FxRateClient — 환율을 어디서 받아 오는가
 *
 * 이 파일이 하는 일
 *   벤더 호출을 인터페이스 하나로 가린다.
 *
 *   왜 굳이 가리는가 — 환율 창구는 사라진다. 이 범위를 만들면서 이미 두 번 겪었다.
 *   한국수출입은행은 하루 한 번 고시라 "현재 환율" 을 못 띄웠고,
 *   두나무 비공식 API 는 널리 알려진 주소가 통째로 없어져 있었다.
 *   구현체 하나만 갈아 끼우면 되게 해 두면 다음번에도 하루면 끝난다.
 *   auth 범위가 MailTransport 로 쓴 방식과 같다.
 */
package com.example.mijang.fx.client;

import com.example.mijang.fx.domain.FxQuote;
import java.util.Optional;

/** 환율 벤더. 구현체를 갈아 끼울 수 있게 인터페이스로 둔다(미장-fx-구현 2.1). */
public interface FxRateClient {

    /** 키가 채워져 있는지. 배치가 돌기 전에 먼저 본다. */
    boolean configured();

    /**
     * 지금 환율.
     *
     * @return 받지 못했으면 비어 있음. <b>예외를 던지지 않는다</b> — 환율을 못 받는 것은
     *         오류가 아니라 흔한 일이고(미장-API명세서 1.6), 배치가 그것 때문에 멈추면 안 된다
     */
    Optional<FxQuote> latest();
}
