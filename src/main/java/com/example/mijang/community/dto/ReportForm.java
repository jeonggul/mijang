package com.example.mijang.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 신고 입력. 개발명세서(API) COM-005 — body {targetType, targetId, reason} */
@Getter
@Setter
public class ReportForm {

    /** POST / COMMENT */
    @NotBlank
    @Pattern(regexp = "(?i)POST|COMMENT", message = "허용되지 않는 값입니다")
    private String targetType;

    @NotNull
    private Long targetId;

    /** 표의 ENUM 그대로. 밖에서 새 값이 오면 저장에서 터지기 전에 여기서 막는다. */
    @NotBlank
    @Pattern(regexp = "SPAM|ABUSE|MISINFO|ETC", message = "허용되지 않는 값입니다")
    private String reason;

    /** 자유 서술. 없어도 된다. */
    @Size(max = 500)
    private String detail;
}
