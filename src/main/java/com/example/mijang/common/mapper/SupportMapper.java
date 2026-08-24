package com.example.mijang.common.mapper;

import com.example.mijang.common.dto.FaqResponse;
import com.example.mijang.common.dto.NoticeResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SupportMapper {

    List<NoticeResponse> findNotices();

    NoticeResponse findNoticeById(@Param("id") Long id);

    List<FaqResponse> findFaqs();

    int insertNotice(NoticeInsert notice);

    class NoticeInsert {
        private Long id;
        private final Long authorId;
        private final String title;
        private final String content;
        private final boolean pinned;

        public NoticeInsert(Long authorId, String title, String content, boolean pinned) {
            this.authorId = authorId;
            this.title = title;
            this.content = content;
            this.pinned = pinned;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getAuthorId() { return authorId; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public boolean isPinned() { return pinned; }
    }
}
