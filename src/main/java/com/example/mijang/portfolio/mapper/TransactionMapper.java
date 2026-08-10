package com.example.mijang.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * transactions(매매 기록) 접근. 이 테이블이 원장이다.
 *
 * <p>개발명세서(MVC) · 포트폴리오 · mapper
 */
@Mapper
public interface TransactionMapper {

    /** 사용자의 매매 기록 수. PORT-004 페이징에 쓴다. */
    long countByUser(@Param("userId") Long userId);
}
