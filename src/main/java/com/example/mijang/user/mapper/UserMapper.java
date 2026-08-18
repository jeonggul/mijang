/*
 * UserMapper — users 테이블 접근
 *
 * 이 파일이 하는 일
 *   회원 정보를 읽고 쓰는 통로다. auth 범위에서 쓰던 것에 세 가지가 늘었다 —
 *   닉네임 중복 확인(나 자신은 빼고), 프로필 갱신, 프로필 조회.
 *   "나 자신은 빼고" 가 중요하다. 안 빼면 내 닉네임을 그대로 두고
 *   다른 값만 고치려 할 때 내 것이 중복으로 잡힌다.
 */
package com.example.mijang.user.mapper;

import com.example.mijang.user.domain.User;
import com.example.mijang.user.dto.UserResponse;
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

    /** 닉네임 중복 확인. DB 에 UNIQUE 가 없어 애플리케이션에서 본다. */
    int countByNickname(@Param("nickname") String nickname);

    /** 로그인용 조회. 탈퇴 계정은 제외한다. */
    User findByEmail(@Param("email") String email);

    User findById(@Param("id") Long id);

    /**
     * 비밀번호 저장. 재설정과 변경이 같은 문장을 쓴다.
     *
     * <p>{@code expectedHash} 는 방금 확인한 값이다. 그 사이에 다른 요청이 먼저 바꿔 놓았다면
     * 조건이 어긋나 0 행이 바뀐다. 확인과 저장 사이가 벌어져 같은 링크가 두 번 먹히는 것을
     * 여기서 막는다 — 잠금 없이 조건부 갱신 하나로 끝난다.
     *
     * @return 바뀐 행 수. 0 이면 그 사이에 비밀번호가 이미 바뀐 것이다
     */
    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("expectedHash") String expectedHash);

    /** 탈퇴 처리. 지우지 않고 상태와 시각만 바꾼다. */
    int withdraw(@Param("id") Long id);


    /**
     * 닉네임 중복 확인 — <b>자기 자신은 뺀다</b>(2.2).
     *
     * <p>이걸 빼먹으면 닉네임을 그대로 두고 다른 항목만 바꿀 때 "이미 사용 중"이 뜬다.
     */
    int countByNicknameExcluding(@Param("nickname") String nickname,
                                 @Param("excludeUserId") Long excludeUserId);

    /**
     * 프로필 수정. null 인 항목은 건드리지 않는다.
     *
     * <p>XML 의 {@code <set>} 이 보낸 값만 골라 넣는다.
     *
     * @return 바뀐 행 수
     */
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("profileImageUrl") String profileImageUrl,
                      @Param("baseCurrency") String baseCurrency,
                      @Param("theme") String theme);

    /** 프로필 응답용 조회. 비밀번호 해시를 담지 않는 DTO 로 바로 받는다. */
    UserResponse findProfile(@Param("id") Long id);

    /** 저장 후 생성된 id 를 user.id 가 아니라 별도 홀더로 받는다. */
    int insert(UserInsert param);

    /**
     * insert 전용 파라미터.
     *
     * <p>record 가 아니라 클래스인 이유 — MyBatis 의 {@code useGeneratedKeys} 는
     * 생성된 키를 파라미터 객체의 setter 로 되돌려 준다. record 는 setter 가 없어
     * id 를 받을 수 없다.
     */
    class UserInsert {
        private Long id;
        private final String email;
        private final String passwordHash;
        private final String nickname;

        /** 저장할 값만 받는다. id 는 DB 가 채워 setId 로 돌아온다. */
        public UserInsert(String email, String passwordHash, String nickname) {
            this.email = email;
            this.passwordHash = passwordHash;
            this.nickname = nickname;
        }

        /** insert 후 DB 가 채워 준 식별자. 호출 전에는 null 이다. */
        public Long getId() { return id; }
        /** MyBatis 가 생성된 키를 여기로 돌려준다. 애플리케이션 코드가 부를 일은 없다. */
        public void setId(Long id) { this.id = id; }

        // 아래는 MyBatis 가 INSERT 문의 #{...} 를 채울 때 읽는 접근자다.

        /** #{email} 자리에 들어간다. */
        public String getEmail() { return email; }
        /** #{passwordHash} 자리에 들어간다. 이미 BCrypt 로 해시된 값이다. */
        public String getPasswordHash() { return passwordHash; }
        /** #{nickname} 자리에 들어간다. */
        public String getNickname() { return nickname; }
    }
}
