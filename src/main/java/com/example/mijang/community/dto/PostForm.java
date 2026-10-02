package com.example.mijang.community.dto;

import com.example.mijang.community.domain.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 게시글 작성 요청 값을 담는다. 작성 시점 주가·매매 스냅샷은 서버가 직접 구한다. */
@Getter
@Setter
public class PostForm {

    @NotBlank
    @Size(max = 150)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String content;

    /** 게시판. 일반 커뮤니티 경로에서만 의미가 있고 종목별 경로에서는 읽지 않는다. */
    private BoardType board;

    /** 본문에 붙일 매매 기록 id. 남의 기록이거나 게시판 종목과 다르면 거절한다. */
    private Long tradeTxId;

    /** 켜면 보유 중일 때 "주주" 배지가 붙는다. 수량은 나가지 않는다. */
    private boolean showHoldingBadge;
}
