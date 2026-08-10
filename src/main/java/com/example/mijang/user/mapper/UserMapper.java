package com.example.mijang.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * users 테이블 접근. 문장은 resources/mapper/UserMapper.xml 에 둔다.
 *
 * <p>개발명세서(MVC) · 인증/회원 · mapper
 */
@Mapper
public interface UserMapper {

    /** 이메일 중복 확인. AUTH-001 의 409 판정에 쓴다. */
    int countByEmail(@Param("email") String email);
}
