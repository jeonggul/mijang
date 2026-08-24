package com.example.mijang.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeForm(
        @NotBlank @Size(max = 150) String title,
        @NotBlank String content,
        boolean pinned) {
}
