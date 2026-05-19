package com.kdd.chat.dto;

import com.kdd.chat.entity.SourceType;

/**
 * 채팅 세션 생성 요청. 본문 자체가 optional이며 필드도 모두 optional이다.
 * <p>
 * FE 호환성: 일반 채팅 진입은 body 없이 호출되는 기존 흐름을 그대로 유지하고,
 * 추천 질문(GET /chat/recommended-questions) 클릭 진입에만 {@code sourceType="recommended"}를
 * 명시해 보내도록 한다. FAQ 클릭 진입은 별도 흐름이 만들어지면 동일하게 {@code "faq"}를 전달한다.
 *
 * @param sourceType {@link SourceType#getValue()} 문자열. null/blank/미인식 값이면 서비스에서 NORMAL로 폴백.
 */
public record ChatSessionCreateRequest(String sourceType) {
}
