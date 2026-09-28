package com.example.mijang.fx.client;

import com.example.mijang.fx.domain.FxQuote;
import java.util.Optional;

/** 환율 벤더 호출을 추상화한다(미장-fx-구현 2.1). */
public interface FxRateClient {

    /** API 키가 설정되어 있는지 확인한다. */
    boolean configured();

    /** 현재 환율을 받아 온다. 실패 시 예외 대신 빈 값을 돌려준다. */
    Optional<FxQuote> latest();
}
