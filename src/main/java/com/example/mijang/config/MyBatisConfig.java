package com.example.mijang.config;

import com.example.mijang.common.type.FxRateTypeHandler;
import com.example.mijang.common.type.KrwAmountTypeHandler;
import com.example.mijang.common.type.QuantityTypeHandler;
import com.example.mijang.common.type.RatioTypeHandler;
import com.example.mijang.common.type.UsdAmountTypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 추가 설정.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.config
 * <p>{@code mapper-locations} · {@code map-underscore-to-camel-case} 는 application.properties 에 있다.
 * 여기서는 {@code common.type} 의 TypeHandler 를 매퍼 XML 에서 짧은 이름으로 부를 수 있게 별칭만 건다.
 *
 * <p><b>기본 핸들러로 등록하지 않는 이유.</b> {@code mybatis.type-handlers-package} 를 쓰거나
 * {@code @MappedTypes(BigDecimal.class)} 를 붙이면 BigDecimal 전체의 기본 핸들러가 되어 버린다.
 * 그런데 스키마의 DECIMAL 은 scale 이 하나가 아니다 — 수량 6 · 달러 4 · 원화 2 · 환율 4.
 * 전부에 같은 scale 을 강제하면 달러 단가가 센트로 깎이거나 원화 손익에 없는 자릿수가 붙는다.
 * 그래서 컬럼마다 매퍼에서 명시적으로 고르게 한다.
 *
 * <pre>{@code
 * <result column="quantity"  property="quantity"  typeHandler="quantity"/>
 * <result column="price"     property="price"     typeHandler="usdAmount"/>
 * <result column="fx_rate"   property="fxRate"    typeHandler="fxRate"/>
 * }</pre>
 *
 * <p>매퍼에서 지정하지 않으면 MyBatis 기본 BigDecimal 처리로 넘어가고, 반올림은 MySQL 이 한다.
 * 틀린 값이 되지는 않지만 반올림 지점이 코드에 남지 않는다.
 */
@Configuration
public class MyBatisConfig {

    @Bean
    public ConfigurationCustomizer mijangTypeHandlerAliases() {
        return configuration -> {
            var aliases = configuration.getTypeAliasRegistry();
            aliases.registerAlias("quantity", QuantityTypeHandler.class);
            aliases.registerAlias("usdAmount", UsdAmountTypeHandler.class);
            aliases.registerAlias("krwAmount", KrwAmountTypeHandler.class);
            aliases.registerAlias("fxRate", FxRateTypeHandler.class);
            aliases.registerAlias("ratio", RatioTypeHandler.class);
        };
    }
}
