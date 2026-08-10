package com.example.mijang.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 댓글 작성. 개발명세서(API) COM-004 */
@Getter
@Setter
public class CommentForm {

    @NotBlank
    private String content;

    /** 대댓글이면 부모 댓글 id */
    private Long parentId;
}
