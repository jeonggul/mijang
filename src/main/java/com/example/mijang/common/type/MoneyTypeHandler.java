package com.example.mijang.common.type;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 금액용 DECIMAL 핸들러. 부동소수점 오차를 막기 위해 금액은 전부 DECIMAL 로 다룬다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.typehandler
 * <p>TODO: 원화(소수점 0)와 달러(소수점 2)의 scale 을 어떻게 나눌지 정해야 한다.
 */
public class MoneyTypeHandler extends BaseTypeHandler<BigDecimal> {

    public static final int KRW_SCALE = 0;
    public static final int USD_SCALE = 2;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, BigDecimal parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setBigDecimal(i, parameter.setScale(USD_SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public BigDecimal getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getBigDecimal(columnName);
    }

    @Override
    public BigDecimal getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getBigDecimal(columnIndex);
    }

    @Override
    public BigDecimal getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getBigDecimal(columnIndex);
    }
}
