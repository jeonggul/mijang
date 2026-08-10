package com.example.mijang.common.type;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * 수량용 DECIMAL(18,6) 핸들러. 소수점 매수에 대응한다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.typehandler
 * <p>TODO: 쓰려면 MyBatisConfig 또는 mybatis.type-handlers-package 로 등록해야 한다.
 */
@MappedTypes(BigDecimal.class)
public class DecimalTypeHandler extends BaseTypeHandler<BigDecimal> {

    /** 기획서 8장: 수량은 DECIMAL(18,6). */
    public static final int SCALE = 6;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, BigDecimal parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setBigDecimal(i, parameter.setScale(SCALE, java.math.RoundingMode.DOWN));
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
