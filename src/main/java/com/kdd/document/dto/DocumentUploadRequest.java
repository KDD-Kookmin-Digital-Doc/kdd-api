package com.kdd.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DocumentUploadRequest {
    private String title;

    @NotNull(message = "categoryId는 필수입니다.")
    private Long categoryId;
}
