package com.example.mijang.admin.mapper;

import com.example.mijang.admin.domain.AdminUserAccount;
import com.example.mijang.admin.dto.AdminUserResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자 사용자 조회와 상태 변경 SQL의 통로. */
@Mapper
public interface AdminUserMapper {

    List<AdminUserResponse> findUsers(@Param("adminId") Long adminId,
                                      @Param("status") String status,
                                      @Param("q") String q,
                                      @Param("limit") int limit);

    int countUsers(@Param("status") String status, @Param("q") String q);

    AdminUserAccount findAccount(@Param("id") Long id);

    /** 상태 변경끼리 직렬화해 두 관리자가 서로를 동시에 정지하는 것을 막는다. */
    List<Long> lockActiveAdminIds();

    /**
     * 상태와 토큰 세대를 함께 바꾼다.
     *
     * @return 예상한 기존 상태가 맞아 변경된 행 수
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("expectedStatus") String expectedStatus);
}
