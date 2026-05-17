package com.kdd.statistics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 관리자 통계 통합 응답 — 노션 "통계 조회" API 명세 응답 스키마를 그대로 따른다.
 * <ul>
 *     <li>{@link Users} — 사용자 인원·유형·학과·학년 분포 (메시지 수가 아닌 인원 수 기준)</li>
 *     <li>{@link Overview} — 전체 서비스 사용 통계 (총 질문·문서·세션·사용자 수)</li>
 *     <li>{@link CategoryStat} — 문서 카테고리별 채팅 참조 분포</li>
 * </ul>
 *
 * @param users      사용자 분포 통계
 * @param overview   전체 서비스 사용 통계
 * @param categories 카테고리별 질문 통계 (questionCount 내림차순)
 */
public record StatisticsResponse(
        Users users,
        Overview overview,
        List<CategoryStat> categories
) {

    /**
     * 사용자 분포 통계.
     * <p>
     * 모든 카운트는 <b>사용자 인원 수</b> 기준이며 메시지 수가 아니다. byUserType과의 일관성을 위해 byUserType도
     * 인원 수로 변경됨 (기존 메시지 수 의미는 overview.totalQuestions로 대체).
     */
    public record Users(
            long totalUsers,
            ByUserType byUserType,
            ByDepartment byDepartment,
            ByGrade byGrade
    ) {}

    public record ByUserType(long student, long staff) {}

    /** 학생 학부 분포 — StudentDepartment enum의 값(software/ai)을 키로 사용. */
    public record ByDepartment(long software, long ai) {}

    /**
     * 학생 학년 분포 — 5학년 이상은 5_or_above 키로 누적.
     * Java record 컴포넌트는 숫자로 시작할 수 없어 grade1~5OrAbove로 선언하고, 명세서 JSON 키와 일치시키기 위해
     * {@link JsonProperty}로 직렬화 이름만 "1"~"5_or_above"로 매핑한다.
     */
    public record ByGrade(
            @JsonProperty("1") long grade1,
            @JsonProperty("2") long grade2,
            @JsonProperty("3") long grade3,
            @JsonProperty("4") long grade4,
            @JsonProperty("5_or_above") long grade5OrAbove
    ) {}

    /**
     * 전체 서비스 사용 통계.
     *
     * @param totalQuestions 누적 사용자 질문 수 (chat_messages WHERE role='user')
     * @param totalDocuments 활성 문서 수 (deleted_at IS NULL)
     * @param totalSessions  누적 채팅 세션 수
     * @param totalUsers     전체 사용자 수 (활성/비활성 모두 포함 — 비활성 정의는 추후 확정)
     */
    public record Overview(
            long totalQuestions,
            long totalDocuments,
            long totalSessions,
            long totalUsers
    ) {}

    /**
     * @param category      document_categories.name
     * @param questionCount 해당 카테고리 문서가 채팅 답변에 참조된 횟수 (chat_message_sources 행 수)
     * @param percentage    전체 참조 중 비율 (소수점 1자리)
     */
    public record CategoryStat(String category, long questionCount, double percentage) {}
}
