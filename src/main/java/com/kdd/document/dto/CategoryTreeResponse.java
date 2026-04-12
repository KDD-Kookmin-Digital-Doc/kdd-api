package com.kdd.document.dto;

import com.kdd.document.entity.DocumentCategory;

import java.util.List;

public record CategoryTreeResponse(
        Long categoryId,
        String name,
        List<CategoryTreeResponse> children
) {
    public static CategoryTreeResponse from(DocumentCategory category, List<CategoryTreeResponse> children) {
        return new CategoryTreeResponse(
                category.getId(),
                category.getName(),
                children
        );
    }
}
