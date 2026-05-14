package com.kdd.statistics.service;

import com.kdd.statistics.dto.StatisticsResponse;
import com.kdd.statistics.repository.StatisticsRepository;
import com.kdd.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics() {
        long totalQuestions = statisticsRepository.countUserMessages();
        long totalDocuments = statisticsRepository.countActiveDocuments();

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

        Map<String, Long> byType = statisticsRepository.countUserMessagesByUserType();
        StatisticsResponse.ByUserType byUserType = new StatisticsResponse.ByUserType(
                byType.getOrDefault(UserType.STUDENT.getValue(), 0L),
                byType.getOrDefault(UserType.STAFF.getValue(), 0L)
        );

        return new StatisticsResponse(
                new StatisticsResponse.Overview(totalQuestions, totalDocuments),
                categories,
                new StatisticsResponse.Users(byUserType)
        );
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
