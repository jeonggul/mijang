package com.example.mijang.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 게시글 작성. 개발명세서(API) COM-002 — 작성 시점 주가를 함께 저장한다. */
@Getter
@Setter
public class PostForm {

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotBlank
    private String content;
}
