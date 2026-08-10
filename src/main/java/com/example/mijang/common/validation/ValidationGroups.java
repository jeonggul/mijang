package com.example.mijang.common.validation;

/**
 * Bean Validation 공통 group. 등록/수정에서 검증 범위를 나눌 때 쓴다.
 *
 * <p>개발명세서(MVC) · 공통/설정 · Common.validation
 */
public final class ValidationGroups {

    private ValidationGroups() {
    }

    public interface Create {
    }

    public interface Update {
    }
}
