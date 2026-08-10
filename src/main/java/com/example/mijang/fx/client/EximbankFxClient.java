package com.example.mijang.fx.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 한국수출입은행 환율 API 클라이언트.
 *
 * <p>개발명세서(MVC) · 환율 · client
 * <p>한도가 일 1,000회이고 영업일 11시에 1회 고시된다. 하루에 여러 번 부를 이유가 없다.
 */
@Component
public class EximbankFxClient {

    public BigDecimal fetchUsdKrw(LocalDate baseDate) {
        throw new UnsupportedOperationException("TODO: 수출입은행 API 호출 후 매매기준율 파싱");
    }
}
