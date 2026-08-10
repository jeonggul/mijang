package com.example.mijang.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 신고 입력. 개발명세서(API) COM-005 — body {targetType, targetId, reason} */
@Getter
@Setter
public class ReportForm {

    /** POST / COMMENT */
    @NotBlank
    private String targetType;

    @NotNull
    private Long targetId;

    @NotBlank
    private String reason;
}
