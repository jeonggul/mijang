package com.example.mijang.user.mapper;

import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.UserResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** users 테이블 접근을 담당한다. */
@Mapper
public interface UserMapper {

    /** 이메일 중복 여부를 센다. */
    int countByEmail(@Param("email") String email);

    /** 닉네임 중복 여부를 센다 — DB에 UNIQUE가 없어 애플리케이션에서 본다. */
    int countByNickname(@Param("nickname") String nickname);

    /** 이메일로 회원을 조회한다 — 탈퇴 계정은 제외한다. */
    User findByEmail(@Param("email") String email);

    /** id로 회원을 조회한다. */
    User findById(@Param("id") Long id);

    /** 비밀번호를 조건부(expectedHash 일치)로 저장한다 — 0행이면 그 사이 이미 바뀐 것이다. */
    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("expectedHash") String expectedHash);

    /** ACTIVE·expectedHash 일치 조건으로 탈퇴 처리한다 — 이메일에 withdrawn 표식을 붙이고 password_version을 올리며, 0행이면 조건이 어긋난 것이다. */
    int withdraw(@Param("id") Long id, @Param("expectedHash") String expectedHash);

    /** 지금 저장된 password_version을 읽는다 — 탈퇴 뒤 registry에 기록할 세대는 스냅샷+1이 아니라 반드시 이 값이어야 한다. */
    int findPasswordVersion(@Param("id") Long id);

    /** FOR UPDATE로 행을 잠그고 최신 커밋 상태를 읽는다 — 스냅샷 읽기로는 동시 탈퇴를 못 봐 죽은 계정에 연동이 붙을 수 있다. */
    String lockUserStatusForUpdate(@Param("id") Long id);

    /** 테스트 전용 — 이 사용자를 ADMIN으로 올린다. */
    void promoteToAdminForTest(@Param("id") Long id);

    /** 테스트 전용 — 탈퇴 행의 이메일(표식 포함)을 그대로 읽는다. */
    String findWithdrawnEmailForTest(@Param("id") Long id);


    /** 자기 자신을 뺀 닉네임 중복 여부를 센다 — 안 빼면 닉네임을 그대로 둔 수정이 중복으로 잡힌다. */
    int countByNicknameExcluding(@Param("nickname") String nickname,
                                 @Param("excludeUserId") Long excludeUserId);

    /** 프로필을 수정한다 — null인 항목은 건드리지 않는다. */
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("profileImageUrl") String profileImageUrl,
                      @Param("baseCurrency") String baseCurrency,
                      @Param("theme") String theme);

    /** 프로필 응답용으로 조회한다 — 비밀번호 해시를 담지 않는 DTO로 바로 받는다. */
    UserResponse findProfile(@Param("id") Long id);

    /** 회원 한 행을 넣고 생성된 id를 파라미터 홀더로 돌려받는다. */
    int insert(UserInsert param);

    /** insert 전용 파라미터다 — useGeneratedKeys가 setter로 id를 돌려주므로 record가 아니라 클래스다. */
    class UserInsert {
        private Long id;
        private final String email;
        private final String passwordHash;
        private final String nickname;

        /** 저장할 값만 받는다 — id는 DB가 채워 setId로 돌아온다. */
        public UserInsert(String email, String passwordHash, String nickname) {
            this.email = email;
            this.passwordHash = passwordHash;
            this.nickname = nickname;
        }

        /** insert 후 DB가 채워 준 식별자를 돌려준다 — 호출 전에는 null이다. */
        public Long getId() { return id; }
        /** MyBatis가 생성된 키를 여기로 돌려준다. */
        public void setId(Long id) { this.id = id; }

        public String getEmail() { return email; }
        /** 이미 BCrypt로 해시된 값을 돌려준다. */
        public String getPasswordHash() { return passwordHash; }
        public String getNickname() { return nickname; }
    }
}
