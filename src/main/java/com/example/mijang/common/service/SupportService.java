package com.example.mijang.common.service;

import com.example.mijang.common.dto.FaqResponse;
import com.example.mijang.common.dto.NoticeResponse;
import com.example.mijang.common.dto.NoticeForm;
import com.example.mijang.common.exception.BusinessException;
import com.example.mijang.common.exception.ErrorCode;
import com.example.mijang.common.mapper.SupportMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportMapper supportMapper;

    @Transactional(readOnly = true)
    public List<NoticeResponse> notices() {
        return supportMapper.findNotices();
    }

    @Transactional(readOnly = true)
    public NoticeResponse notice(Long id) {
        NoticeResponse notice = supportMapper.findNoticeById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
        }
        return notice;
    }

    @Transactional(readOnly = true)
    public List<FaqResponse> faqs() {
        return supportMapper.findFaqs();
    }

    @Transactional
    public Long createNotice(Long authorId, NoticeForm form) {
        SupportMapper.NoticeInsert insert = new SupportMapper.NoticeInsert(
                authorId, form.title().trim(), form.content().trim(), form.pinned());
        supportMapper.insertNotice(insert);
        return insert.getId();
    }

    /**
     * 공지 수정. {@code ADMIN-05}
     *
     * @throws BusinessException 없거나 이미 지워진 공지일 때(404)
     */
    @Transactional
    public void updateNotice(Long noticeId, NoticeForm form) {
        int changed = supportMapper.updateNotice(noticeId,
                form.title().trim(), form.content().trim(), form.pinned());
        if (changed != 1) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
    }

    /** 공지 삭제. 지우지 않고 표시만 한다 — 링크를 타고 들어온 사람이 있을 수 있다. */
    @Transactional
    public void deleteNotice(Long noticeId) {
        if (supportMapper.deleteNotice(noticeId) != 1) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
    }
}
