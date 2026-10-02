package com.example.mijang.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** portfolios 테이블에 접근하는 매퍼다. 사용자마다 기본 포트폴리오 하나만 쓴다. */
@Mapper
public interface PortfolioMapper {

    /** 기본 포트폴리오 id 를 조회한다. 없으면 null 이다. */
    Long findDefaultId(@Param("userId") Long userId);

    /** 기본 포트폴리오를 만든다. */
    int insertDefault(@Param("userId") Long userId);

    /** 방금 만든 id 를 조회한다. insertDefault 직후에만 의미가 있다. */
    Long findLastInsertedId();
}
