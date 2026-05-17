package com.kdd.statistics.dto;

import java.util.List;

/**
 * 관리자 통계 통합 응답 — 노션 "통계 조회" API 명세를 정확히 따른다.
 * <ul>
 *     <li>{@link Overview} — 전체 서비스 사용 통계 (총 질문 수, 업로드 문서 수)</li>
 *     <li>{@link CategoryStat} — 카테고리별 질문 통계 (문서 카테고리 기준)</li>
 *     <li>{@link Users#byUserType} — 사용자 유형별 질문 수</li>
 * </ul>
 *
 * @param overview   전체 서비스 사용 통계
 * @param categories 카테고리별 질문 통계 (questionCount 내림차순)
 * @param users      사용자 유형별 통계
 */
public record StatisticsResponse(
        Overview overview,
        List<CategoryStat> categories,
        Users users
) {
    public record Overview(long totalQuestions, long totalDocuments) {}

    /**
     * @param category      document_categories.name
     * @param questionCount 해당 카테고리 문서가 채팅 답변에 참조된 횟수 (chat_message_sources 행 수)
     * @param percentage    전체 참조 중 비율 (소수점 1자리)
     */
    public record CategoryStat(String category, long questionCount, double percentage) {}

    public record Users(ByUserType byUserType) {}

    /**
     * 사용자 유형별 <b>질문(user 메시지) 수</b> — 사용자 인원 수가 아니다.
     * 한 학생이 1000개 보내면 student=1000으로 집계된다 (메시지 단위).
     */
    public record ByUserType(long student, long staff) {}
}
