package com.kdd.setting.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DefaultChatLimitUpdateRequest(
        @NotNull @Min(0) Integer defaultChatLimit
) {
}
