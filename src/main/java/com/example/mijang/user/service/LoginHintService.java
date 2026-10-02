package com.example.mijang.user.service;

import com.example.mijang.config.DemoAccountProperties;
import com.example.mijang.user.domain.User;
import com.example.mijang.user.mapper.UserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 화면 계정 안내에 내보내도 되는 체험 계정만 거른다. 안내 값은 공개 화면 소스에 실리므로 관리자 계정은 내보내지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHintService {

    /** 안내에 실을 수 없는 권한. 이 계정의 자격은 곧 운영 콘솔 전체다. */
    private static final String ADMIN = "ADMIN";

    private final DemoAccountProperties props;
    private final UserMapper userMapper;

    /** 화면에 내보낼 계정 목록을 돌려준다. 관리자 계정과 실재하지 않는 계정은 뺀다. */
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
            if (props.isAllowAdmin()) {
                // 배포 전 로컬 확인용 옵트인. 켜져 있으면 기동 때마다 경고를 남긴다.
                log.warn("allow-admin 이 켜져 있어 관리자 계정을 로그인 안내에 내보낸다: {}."
                        + " 운영 설정에는 절대 켜지 않는다.", account.getEmail());
                return true;
            }
            log.error("로그인 안내에 관리자 계정이 적혀 있어 뺀다: {}."
                    + " 로그인 화면은 공개 화면이라 이 값은 누구나 읽을 수 있다.",
                    account.getEmail());
            return false;
        }
        return true;
    }
}
