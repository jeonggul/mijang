package com.example.mijang.common.type;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** 쓰기 직전에 scale 을 고정하는 DECIMAL 핸들러 뼈대다. 읽기에 scale 을 다시 맞추면 잘못 붙인 핸들러가 조용히 덮이므로 손대지 않는다. */
public abstract class ScaledDecimalTypeHandler extends BaseTypeHandler<BigDecimal> {

    private final int scale;
    private final RoundingMode rounding;

    protected ScaledDecimalTypeHandler(int scale, RoundingMode rounding) {
        this.scale = scale;
        this.rounding = rounding;
    }

    public int scale() {
        return scale;
    }

    public RoundingMode rounding() {
        return rounding;
    }

    /** 저장 직전 값을 고정 scale 로 정규화한다. */
    public BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(scale, rounding);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, BigDecimal parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setBigDecimal(i, normalize(parameter));
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
