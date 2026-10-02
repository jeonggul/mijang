package com.example.mijang.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 글 수정 요청 값을 담는다. 제목·본문만 받는다. */
@Getter
@Setter
public class PostUpdateForm {

    @NotBlank
    @Size(max = 150)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String content;
}
