package com.kdd.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkChatLimitUpdateRequest(
        @NotEmpty List<@NotNull Long> userIds,
        @NotNull @Min(0) Integer dailyChatLimit
) {
}
