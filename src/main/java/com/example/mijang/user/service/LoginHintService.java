/*
 * LoginHintService — 로그인 화면 말풍선에 실제로 내보낼 계정을 고르는 곳
 *
 * 이 파일이 하는 일
 *   설정에 적힌 체험 계정 중 "내보내도 되는 것" 만 걸러 준다.
 *   로그인 화면은 비로그인 공개 화면이고, 말풍선 값은 hidden 이어도
 *   페이지 소스에 그대로 실린다. 즉 여기 실린 계정은 공개된 계정이다.
 *
 *   그래서 관리자 계정은 무슨 일이 있어도 통과시키지 않는다.
 *   설정 파일은 사람이 손으로 고치는 곳이라 언젠가 다시 관리자 계정이
 *   적힌다 — 그때 막을 곳이 코드 쪽에 하나 있어야 한다.
 */
package com.example.mijang.user.service;

import com.example.mijang.config.DemoAccountProperties;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.mapper.UserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 화면 계정 안내에 내보낼 목록.
 *
 * <p>설정({@link DemoAccountProperties})은 "무엇을 띄우고 싶은가" 를 적는 곳이고,
 * 여기는 <b>"띄워도 되는가"</b> 를 판단하는 곳이다. 둘을 나눈 이유는 2026-09-03 점검에서
 * 체험 계정으로 걸어 둔 값이 ADMIN 이었고, 그 화면이 비로그인 공개 화면이라
 * 누구나 운영 콘솔을 열 수 있었기 때문이다.
 *
 * <p>설정만 고쳐서는 같은 일이 또 난다 — 설정 파일은 사람이 손으로 고치는 곳이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHintService {

    /** 안내에 실을 수 없는 권한. 이 계정의 자격은 곧 운영 콘솔 전체다. */
    private static final String ADMIN = "ADMIN";

    private final DemoAccountProperties props;
    private final UserMapper userMapper;

    /**
     * 화면에 내보낼 계정.
     *
     * <p>거르는 기준은 둘이다.
     *
     * <ul>
     *   <li><b>관리자</b> — 안내에 실리는 순간 공개 자격이 된다. 무조건 뺀다</li>
     *   <li><b>없는 계정</b> — 눌러 채워도 로그인되지 않는다. 안내가 아니라 혼란이다</li>
     * </ul>
     *
     * <p>관리자를 걸러낼 때는 로그를 남긴다. 조용히 빼면 "말풍선이 안 뜬다" 로만 보이고
     * 왜 안 뜨는지 알 수 없다.
     */
    @Transactional(readOnly = true)
    public List<DemoAccountProperties.Account> visibleAccounts() {
        return props.usable().stream().filter(this::publishable).toList();
    }

    private boolean publishable(DemoAccountProperties.Account account) {
        User user = userMapper.findByEmail(account.getEmail());
        if (user == null) {
            log.warn("로그인 안내에 적힌 계정이 실재하지 않아 뺀다: {}", account.getEmail());
            return false;
        }
        if (ADMIN.equals(user.role())) {
            log.error("로그인 안내에 관리자 계정이 적혀 있어 뺀다: {}."
                    + " 로그인 화면은 공개 화면이라 이 값은 누구나 읽을 수 있다.",
                    account.getEmail());
            return false;
        }
        return true;
    }
}
