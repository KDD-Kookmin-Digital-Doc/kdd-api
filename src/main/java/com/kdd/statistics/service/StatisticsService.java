package com.kdd.statistics.service;

import com.kdd.statistics.dto.StatisticsResponse;
import com.kdd.statistics.repository.StatisticsRepository;
import com.kdd.user.entity.StudentDepartment;
import com.kdd.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    /**
     * 통계는 7개의 독립 쿼리를 합산해 응답을 만든다. READ_COMMITTED에서는 쿼리 사이에 다른 트랜잭션이
     * commit 한 변경이 보이므로 {@code totalUsers != byUserType.sum()} 같은 비정합이 발생할 수 있다.
     * REPEATABLE_READ로 트랜잭션 시작 시점의 스냅샷을 고정해 한 응답 내 합계 일관성을 보장한다.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StatisticsResponse getStatistics() {
        // ---- categories (문서 카테고리별 채팅 참조 분포) ----
        List<StatisticsRepository.CategoryCount> rawCategories =
                statisticsRepository.aggregateQuestionsByCategory();
        long totalCategoryRefs = rawCategories.stream()
                .mapToLong(StatisticsRepository.CategoryCount::questionCount)
                .sum();
        List<StatisticsResponse.CategoryStat> categories = rawCategories.stream()
                .map(c -> new StatisticsResponse.CategoryStat(
                        c.category(),
                        c.questionCount(),
                        toPercentage(c.questionCount(), totalCategoryRefs)
                ))
                .toList();

        // ---- users (인원 수 기준 분포: 메시지 수와 무관) ----
        long totalUsers = statisticsRepository.countTotalUsers();

        Map<String, Long> usersByType = statisticsRepository.countUsersByUserType();
        StatisticsResponse.ByUserType byUserType = new StatisticsResponse.ByUserType(
                usersByType.getOrDefault(UserType.STUDENT.getValue(), 0L),
                usersByType.getOrDefault(UserType.STAFF.getValue(), 0L)
        );

        Map<String, Long> studentsByDept = statisticsRepository.countStudentsByDepartment();
        StatisticsResponse.ByDepartment byDepartment = new StatisticsResponse.ByDepartment(
                studentsByDept.getOrDefault(StudentDepartment.SOFTWARE.getValue(), 0L),
                studentsByDept.getOrDefault(StudentDepartment.AI.getValue(), 0L)
        );

        Map<String, Long> studentsByGrade = statisticsRepository.countStudentsByGrade();
        StatisticsResponse.ByGrade byGrade = new StatisticsResponse.ByGrade(
                studentsByGrade.getOrDefault("1", 0L),
                studentsByGrade.getOrDefault("2", 0L),
                studentsByGrade.getOrDefault("3", 0L),
                studentsByGrade.getOrDefault("4", 0L),
                studentsByGrade.getOrDefault("5_or_above", 0L)
        );

        StatisticsResponse.Users users = new StatisticsResponse.Users(
                totalUsers, byUserType, byDepartment, byGrade);

        // ---- overview (전체 사용량) ----
        long totalQuestions = statisticsRepository.countUserMessages();
        long totalDocuments = statisticsRepository.countActiveDocuments();
        long totalSessions = statisticsRepository.countTotalSessions();
        StatisticsResponse.Overview overview = new StatisticsResponse.Overview(
                totalQuestions, totalDocuments, totalSessions, totalUsers);

        return new StatisticsResponse(users, overview, categories);
    }

    /**
     * 소수점 1자리 비율 산정. 분모가 0이면 0.0 반환 (FE 그래프에서 0% 그대로 표시).
     */
    private double toPercentage(long part, long total) {
        if (total <= 0) return 0.0;
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
