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
}
