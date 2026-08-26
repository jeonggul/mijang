package com.example.mijang.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 반응 토글 요청. 표의 ENUM 그대로 두 종류뿐이다. */
@Getter
@Setter
public class ReactionForm {

    @NotBlank
    @Pattern(regexp = "(?i)LIKE|SCRAP", message = "허용되지 않는 값입니다")
    private String type;
}
