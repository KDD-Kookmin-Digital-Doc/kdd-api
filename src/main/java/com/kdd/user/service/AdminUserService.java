package com.kdd.user.service;

import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.response.PageResponse;
import com.kdd.user.dto.AdminUserListItemResponse;
import com.kdd.user.dto.BulkChatLimitUpdateResponse;
import com.kdd.user.entity.Role;
import com.kdd.user.entity.User;
import com.kdd.user.entity.UserChatUsage;
import com.kdd.user.entity.UserType;
import com.kdd.user.repository.UserChatUsageRepository;
import com.kdd.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    // 한도/사용량 기준 시간대는 ChatRateLimitService와 동일하게 KST. 두 곳에서 별도 상수를 쓰는 것은
    // 의도된 중복: 각 도메인이 자기 책임 범위에서 명시적으로 선언하도록 두고, 향후 다른 시간대가 필요해질 때
    // 둘이 같이 움직이는 것을 코드 리뷰에서 확인하게 한다.
    private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final UserChatUsageRepository userChatUsageRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItemResponse> searchUsers(String userType, String role, String search,
                                                                int page, int size) {
        UserType userTypeFilter = blankToNullThen(userType, UserType::from);
        Role roleFilter = blankToNullThen(role, Role::from);
        String searchFilter = (search == null || search.isBlank()) ? null : search.trim();

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<User> usersPage = userRepository.searchForAdmin(userTypeFilter, roleFilter, searchFilter, pageRequest);

        // 페이지의 user들에 대해 오늘 사용량을 한 번의 쿼리로 모아온다 (N+1 회피)
        List<Long> userIds = usersPage.getContent().stream().map(User::getId).toList();
        LocalDate today = LocalDate.now(RESET_ZONE);
        Map<Long, Integer> usageByUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserChatUsage u : userChatUsageRepository.findAllByUserIdsAndDate(userIds, today)) {
                usageByUserId.put(u.getUserId(), u.getChatCount());
            }
        }

        return PageResponse.from(usersPage,
                user -> AdminUserListItemResponse.from(user, usageByUserId.getOrDefault(user.getId(), 0)));
    }

    @Transactional
    public AdminUserListItemResponse updateChatLimit(Long userId, int dailyChatLimit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updateDailyChatLimit(dailyChatLimit);

        LocalDate today = LocalDate.now(RESET_ZONE);
        int todayUsed = userChatUsageRepository.findByUserIdAndUsageDate(userId, today)
                .map(UserChatUsage::getChatCount)
                .orElse(0);
        return AdminUserListItemResponse.from(user, todayUsed);
    }

    @Transactional
    public BulkChatLimitUpdateResponse bulkUpdateChatLimit(List<Long> userIds, int dailyChatLimit) {
        if (userIds.isEmpty()) {
            return new BulkChatLimitUpdateResponse(0, dailyChatLimit);
        }
        if (dailyChatLimit < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        // 엔티티 로드 없이 한 번의 SQL UPDATE로 처리. 존재하지 않는 ID는 자동으로 누락되며,
        // 반환된 row 수가 실제로 변경된 사용자 수다.
        int updated = userRepository.updateDailyChatLimitByIds(userIds, dailyChatLimit);
        return new BulkChatLimitUpdateResponse(updated, dailyChatLimit);
    }

    private <T> T blankToNullThen(String raw, java.util.function.Function<String, T> parser) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return parser.apply(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
