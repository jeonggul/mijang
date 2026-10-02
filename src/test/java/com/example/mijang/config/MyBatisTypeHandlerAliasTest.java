package com.example.mijang.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.common.type.FxRateTypeHandler;
import com.example.mijang.common.type.KrwAmountTypeHandler;
import com.example.mijang.common.type.QuantityTypeHandler;
import com.example.mijang.common.type.RatioTypeHandler;
import com.example.mijang.common.type.ScaledDecimalTypeHandler;
import com.example.mijang.common.type.UsdAmountTypeHandler;
import java.math.BigDecimal;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TypeHandler 배선 확인. Spring 이 실제로 만든 SqlSessionFactory 를 들여다본다.
 *
 * <p>매퍼 XML 에서 {@code typeHandler="quantity"} 로 부를 수 있어야 하고,
 * 동시에 BigDecimal 전체의 기본 핸들러가 되어서는 안 된다.
 */
@SpringBootTest
class MyBatisTypeHandlerAliasTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("매퍼 XML 이 쓸 짧은 별칭이 전부 등록돼 있다")
    void aliasesAreRegistered() {
        var aliases = sqlSessionFactory.getConfiguration().getTypeAliasRegistry();

        assertThat(aliases.<Object>resolveAlias("quantity")).isEqualTo(QuantityTypeHandler.class);
        assertThat(aliases.<Object>resolveAlias("usdAmount")).isEqualTo(UsdAmountTypeHandler.class);
        assertThat(aliases.<Object>resolveAlias("krwAmount")).isEqualTo(KrwAmountTypeHandler.class);
        assertThat(aliases.<Object>resolveAlias("fxRate")).isEqualTo(FxRateTypeHandler.class);
        assertThat(aliases.<Object>resolveAlias("ratio")).isEqualTo(RatioTypeHandler.class);
    }

    @Test
    @DisplayName("BigDecimal 의 기본 핸들러를 가로채지 않는다 — scale 이 컬럼마다 다르기 때문")
    void doesNotHijackDefaultBigDecimalHandler() {
        var handlers = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();

        assertThat(handlers.getTypeHandler(BigDecimal.class))
                .as("전역 기본 핸들러가 되면 달러 단가가 센트로 깎이거나 원화에 없는 자릿수가 붙는다")
                .isNotInstanceOf(ScaledDecimalTypeHandler.class);
    }
}
