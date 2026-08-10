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
 * scale 을 고정해 쓰기(write)하는 DECIMAL 핸들러의 공통 뼈대.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.typehandler
 *
 * <p><b>왜 필요한가.</b> 자바에서 나눗셈으로 만든 값은 scale 이 제멋대로다
 * (예: 이동평균 단가 = 총액 / 수량). 이 값을 그대로 넣으면 반올림을 MySQL 이 조용히 대신한다.
 * 손익 계산의 반올림이 어디서 일어났는지 코드에 남지 않는 것이 문제라서, 넣기 직전에
 * 자바 쪽에서 명시적으로 맞춘다.
 *
 * <p><b>읽기는 손대지 않는다.</b> 컬럼이 이미 그 scale 로 정의돼 있어 JDBC 가 정확한 scale 로
 * 돌려준다. 여기서 다시 맞추면 잘못된 핸들러를 붙였을 때 그 사실이 조용히 덮인다.
 *
 * <p><b>이것은 안전망이지 해결책이 아니다.</b> 계산 도중의 중간값은 여전히 메모리에서
 * 원래 scale 로 남는다. 반올림 시점을 확정하는 책임은 P2 손익 계산 서비스에 있다.
 */
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

    /** 저장 직전 scale 정규화. 테스트에서 직접 검증할 수 있게 public 으로 둔다. */
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
