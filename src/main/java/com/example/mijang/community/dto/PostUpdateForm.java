package com.example.mijang.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 글 수정 요청. 제목·본문뿐이다.
 *
 * <p>{@link PostForm} 을 다시 쓰지 않는다 — 그쪽에는 게시판·매매 카드처럼
 * 수정에서 받으면 안 되는 필드가 있다. 폼을 나누면 "안 받는다" 가 타입으로 굳는다.
 */
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
