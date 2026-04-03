package com.kdd.document.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DocumentUploadRequest {
    private String title;
    private Long categoryId;
}
