package com.example.mijang.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * posts(게시글) 접근.
 *
 * <p>개발명세서(MVC) · 커뮤니티 · mapper — 확장(부록 C)
 */
@Mapper
public interface PostMapper {

    /** 종목별 게시글 수. COM-001 */
    long countBySymbol(@Param("symbol") String symbol);
}
