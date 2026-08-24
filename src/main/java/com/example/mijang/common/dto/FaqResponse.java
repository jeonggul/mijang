package com.example.mijang.common.dto;

/** 설정 화면 FAQ 한 건. */
public record FaqResponse(Long id, String question, String answer, int sortOrder) {
}
