package com.example.mijang.config;

import com.example.mijang.common.type.FxRateTypeHandler;
import com.example.mijang.common.type.KrwAmountTypeHandler;
import com.example.mijang.common.type.QuantityTypeHandler;
import com.example.mijang.common.type.RatioTypeHandler;
import com.example.mijang.common.type.UsdAmountTypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TypeHandler 별칭을 등록한다. 기본 핸들러로 올리면 scale 이 다른 컬럼들이 한 scale 로 깎이므로 매퍼에서 명시적으로 고른다. */
@Configuration
public class MyBatisConfig {

    /** 매퍼 XML 에서 쓸 TypeHandler 별칭을 건다. */
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
