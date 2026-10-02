package com.example.mijang.admin.mapper;

import com.example.mijang.admin.domain.AdminUserAccount;
import com.example.mijang.admin.dto.AdminUserResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 관리자 사용자 조회와 상태 변경 SQL의 통로. */
@Mapper
public interface AdminUserMapper {

    /** 사용자 목록을 조회한다. */
    List<AdminUserResponse> findUsers(@Param("adminId") Long adminId,
                                      @Param("status") String status,
                                      @Param("q") String q,
                                      @Param("limit") int limit);

    /** 같은 조건의 사용자 수를 센다. */
    int countUsers(@Param("status") String status, @Param("q") String q);

    /** 사용자 원본 값 한 건을 조회한다. */
    AdminUserAccount findAccount(@Param("id") Long id);

    /** 활성 관리자 행을 잠근다 — 상태 변경끼리 직렬화해 두 관리자가 서로를 동시에 정지하는 것을 막는다. */
    List<Long> lockActiveAdminIds();

    /** 권한을 바꾼다 — 지금 권한이 기대와 같을 때만 바뀌고, 아니면 0 을 돌려준다. */
    int updateRole(@Param("id") Long id,
                   @Param("role") String role,
                   @Param("expectedRole") String expectedRole);

    /** 상태를 바꾼다 — 지금 상태가 기대와 같을 때만 바뀐 행 수가 1 이다. */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("expectedStatus") String expectedStatus);
}
