/*
 * PostForm — 글쓰기 화면이 보내는 값
 *
 * 이 파일이 하는 일
 *   제목·본문 외에 세 가지를 더 받는다.
 *     board       일반 커뮤니티에서만 쓴다. 종목별은 경로가 게시판을 정하므로 무시한다
 *     tradeTxId   본문에 카드로 붙일 내 매매 한 건
 *     showHoldingBadge  "주주" 배지를 달지
 *   작성 시점 주가와 매매 스냅샷은 여기서 받지 않는다. 화면이 보낸 값을 그대로
 *   저장하면 원하는 숫자를 적어 넣을 수 있다 — 서버가 직접 구해서 박는다.
 */
package com.example.mijang.community.dto;

import com.example.mijang.community.domain.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 게시글 작성. 개발명세서(API) COM-002 — 작성 시점 주가를 함께 저장한다. */
@Getter
@Setter
public class PostForm {

    @NotBlank
    @Size(max = 150)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String content;

    /**
     * 게시판. 일반 커뮤니티({@code /api/posts})에서만 의미가 있고
     * {@code FREE} 또는 {@code QNA} 여야 한다. 종목별 경로에서는 읽지 않는다.
     */
    private BoardType board;

    /**
     * 본문에 붙일 매매 기록 id. 종목별 게시판에서만 쓴다.
     *
     * <p>남의 기록이거나 그 게시판 종목이 아니면 거절한다 — 게시판 종목과 카드 종목이
     * 다르면 읽는 사람이 다른 종목 수익률을 그 종목 것으로 읽는다.
     */
    private Long tradeTxId;

    /** 켜면 보유 중일 때 "주주" 배지가 붙는다. 수량은 나가지 않는다. */
    private boolean showHoldingBadge;
}
