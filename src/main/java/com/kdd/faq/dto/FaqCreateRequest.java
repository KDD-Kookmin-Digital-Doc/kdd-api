package com.kdd.faq.dto;

import com.kdd.faq.entity.FaqTopic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaqCreateRequest(
        @NotBlank(message = "질문은 필수입니다.")
        @Size(max = 2000, message = "질문은 2000자 이하여야 합니다.")
        String question,

        @NotBlank(message = "답변은 필수입니다.")
        @Size(max = 10000, message = "답변은 10000자 이하여야 합니다.")
        String answer,

        @NotNull(message = "토픽은 필수입니다.")
        FaqTopic topic
) {
}
