package com.example.mijang.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mijang.community.dto.CommentResponse;
import com.example.mijang.community.domain.PostRow;
import com.example.mijang.community.mapper.CommentMapper;
import com.example.mijang.community.mapper.PostMapper;
import com.example.mijang.user.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 작성자의 게시글·댓글은 이름이 "탈퇴한 사용자" 로 보이고,
 * 활성 작성자는 실명으로 보이는지 잠근다. @Transactional 로 롤백된다.
 */
@SpringBootTest
@Transactional
class WithdrawnAuthorAnonymizationTest {

    private static final String ANON = "탈퇴한 사용자";

    @Autowired UserMapper userMapper;
    @Autowired PostMapper postMapper;
    @Autowired CommentMapper commentMapper;

    @Test
    @DisplayName("탈퇴한 작성자의 게시글은 이름이 '탈퇴한 사용자' 로, 활성 작성자는 실명으로 나온다")
    void postAuthorAnonymizedWhenWithdrawn() {
        Long activeId = newUser("active-author@mijang.app", "살아있는작성자");
        Long goneId = newUser("gone-author@mijang.app", "떠난작성자");
        long activePost = newGeneralPost(activeId, "활성 글");
        long gonePost = newGeneralPost(goneId, "탈퇴 글");

        userMapper.withdraw(goneId, "$2a$10$x");

        PostRow active = postMapper.findAnyById(activePost);
        PostRow gone = postMapper.findAnyById(gonePost);
        assertThat(active.authorName()).isEqualTo("살아있는작성자");
        assertThat(gone.authorName()).isEqualTo(ANON);
    }

    @Test
    @DisplayName("탈퇴한 작성자의 댓글은 이름이 '탈퇴한 사용자' 로 나온다")
    void commentAuthorAnonymizedWhenWithdrawn() {
        Long postOwner = newUser("post-owner@mijang.app", "글쓴이");
        Long commenter = newUser("commenter@mijang.app", "댓글쓴이");
        long postId = newGeneralPost(postOwner, "댓글 달릴 글");
        newComment(postId, commenter, "댓글 내용");

        userMapper.withdraw(commenter, "$2a$10$x");

        List<CommentResponse> comments = commentMapper.findByPost(postId);
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).authorName()).isEqualTo(ANON);
    }

    // --- helpers: 실제 스키마에 맞춰 최소 필드만 채운다 ---

    private Long newUser(String email, String nickname) {
        var insert = new UserMapper.UserInsert(email, "$2a$10$x", nickname);
        userMapper.insert(insert);
        return insert.getId();
    }

    /** 종목 없는 일반 게시판 글 하나. board=FREE(종목 불필요), 나머지는 서버 기본값이다. */
    private long newGeneralPost(Long userId, String title) {
        postMapper.insert(userId, "FREE", null, title, "본문",
                null, null, false, null,
                null, null, null, null, null,
                null, null);
        return postMapper.findLastInsertedId();
    }

    private void newComment(long postId, Long userId, String content) {
        commentMapper.insert(postId, userId, null, content);
        commentMapper.findLastInsertedId();
    }
}
