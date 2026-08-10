package com.example.mijang.community.service;

import com.example.mijang.community.dto.ReportForm;
import com.example.mijang.community.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게시글·댓글 신고. 개발명세서(API) COM-005 — 확장(부록 C)
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

    public Long create(Long userId, ReportForm form) {
        throw new UnsupportedOperationException(
                "TODO COM-005: 중복 신고면 REPORT_DUPLICATED(409), 아니면 reports 저장");
    }
}
