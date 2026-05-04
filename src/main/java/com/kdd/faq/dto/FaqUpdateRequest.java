package com.kdd.faq.dto;

import com.kdd.faq.entity.FaqTopic;
import jakarta.validation.constraints.Size;

// 변경할 필드만 전송. 모두 optional이지만 제공된 값에 길이 제약은 적용한다.
public record FaqUpdateRequest(
        @Size(max = 2000, message = "질문은 2000자 이하여야 합니다.")
        String question,

        @Size(max = 10000, message = "답변은 10000자 이하여야 합니다.")
        String answer,

        FaqTopic topic
) {
}
