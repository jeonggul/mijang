package com.example.mijang.portfolio.mapper;

import com.example.mijang.portfolio.dto.HoldingResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * holdings(보유 현황) 접근. 매매 기록에서 재계산되는 파생 테이블이다.
 *
 * <p>개발명세서(MVC) · 포트폴리오 · mapper
 */
@Mapper
public interface HoldingMapper {

    /** 보유 현황 조회. PORT-001 */
    List<HoldingResponse> findByUser(@Param("userId") Long userId);
}
