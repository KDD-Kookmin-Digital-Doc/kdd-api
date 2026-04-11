package com.kdd.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSessionUpdateRequest(
        @NotBlank @Size(max = 100) String title
) {
}
