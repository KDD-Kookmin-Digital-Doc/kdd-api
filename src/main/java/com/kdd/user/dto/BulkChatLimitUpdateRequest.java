package com.kdd.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkChatLimitUpdateRequest(
        // 단일 요청으로 너무 큰 UPDATE를 트리거하지 못하도록 상한을 둔다.
        // admin 목록의 한 페이지가 최대 size=100이므로 여러 페이지를 모아 한 번에 처리하는 시나리오까지 커버.
        @NotEmpty @Size(max = 500) List<@NotNull Long> userIds,
        @NotNull @Min(0) Integer dailyChatLimit
) {
}
