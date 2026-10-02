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

/** 공지·FAQ 조회와 공지 관리 로직을 담당한다. */
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportMapper supportMapper;

    /** 공지 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<NoticeResponse> notices() {
        return supportMapper.findNotices();
    }

    /** 공지 상세를 조회한다. 없으면 404 를 던진다. */
    @Transactional(readOnly = true)
    public NoticeResponse notice(Long id) {
        NoticeResponse notice = supportMapper.findNoticeById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND);
        }
        return notice;
    }

    /** FAQ 목록을 조회한다. */
    @Transactional(readOnly = true)
    public List<FaqResponse> faqs() {
        return supportMapper.findFaqs();
    }

    /** 공지를 등록하고 생성된 id 를 반환한다. */
    @Transactional
    public Long createNotice(Long authorId, NoticeForm form) {
        SupportMapper.NoticeInsert insert = new SupportMapper.NoticeInsert(
                authorId, form.title().trim(), form.content().trim(), form.pinned());
        supportMapper.insertNotice(insert);
        return insert.getId();
    }

    /** 공지를 수정한다. 없거나 지워진 공지면 404 를 던진다. */
    @Transactional
    public void updateNotice(Long noticeId, NoticeForm form) {
        int changed = supportMapper.updateNotice(noticeId,
                form.title().trim(), form.content().trim(), form.pinned());
        if (changed != 1) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
    }

    /** 공지를 소프트 삭제한다. */
    @Transactional
    public void deleteNotice(Long noticeId) {
        if (supportMapper.deleteNotice(noticeId) != 1) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
    }
}
