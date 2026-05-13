package com.kdd.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChatLimitUpdateRequest(
        @NotNull @Min(0) Integer dailyChatLimit
) {
}
