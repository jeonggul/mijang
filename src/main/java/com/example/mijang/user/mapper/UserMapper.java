package com.example.mijang.user.mapper;

import com.example.mijang.user.domain.User;
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
