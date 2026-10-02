package com.example.mijang.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 등록·수정 입력 폼이다. */
public record NoticeForm(
        @NotBlank @Size(max = 150) String title,
        @NotBlank String content,
        boolean pinned) {
}
