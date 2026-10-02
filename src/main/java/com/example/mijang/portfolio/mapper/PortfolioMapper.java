/*
 * PortfolioMapper — 포트폴리오 테이블 접근
 *
 * 이 파일이 하는 일
 *   portfolios 를 읽고 쓰는 통로다.
 *   MVP 는 사용자마다 기본 포트폴리오 하나만 쓴다. 없으면 첫 거래를 기록할 때
 *   자동으로 하나 만든다 — 가입 절차에 단계를 더하지 않으려는 것이다.
 */
package com.example.mijang.portfolio.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * portfolios 접근. MVP 는 사용자마다 기본 하나만 쓴다(2.8).
 */
@Mapper
public interface PortfolioMapper {

    /** 기본 포트폴리오 id. 없으면 null. */
    Long findDefaultId(@Param("userId") Long userId);

    /**
     * 기본 포트폴리오를 만든다.
     *
     * <p>가입 시점이 아니라 첫 기록을 넣을 때 만든다. 한 번도 기록하지 않는 사용자에게
     * 빈 포트폴리오가 남지 않게 하려는 것이다.
     */
    int insertDefault(@Param("userId") Long userId);

    /** 방금 만든 id. insertDefault 직후에만 의미가 있다. */
    Long findLastInsertedId();
}
