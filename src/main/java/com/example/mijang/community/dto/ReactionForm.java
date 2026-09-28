package com.example.mijang.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 좋아요·스크랩 토글 요청 값을 담는다. */
@Getter
@Setter
public class ReactionForm {

    @NotBlank
    @Pattern(regexp = "(?i)LIKE|SCRAP", message = "허용되지 않는 값입니다")
    private String type;
}
