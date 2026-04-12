package com.kdd.document.controller;

import com.kdd.document.dto.CategoryTreeResponse;
import com.kdd.document.dto.DocumentByCategoryResponse;
import com.kdd.document.service.DocumentService;
import com.kdd.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Documents", description = "사용자 문서 조회 API")
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "문서 카테고리 트리 조회", description = "사전에 정의된 문서 카테고리 트리 구조를 조회한다.")
    @GetMapping("/categories")
    public ResponseEntity<Map<String, List<CategoryTreeResponse>>> getCategories() {
        return ResponseEntity.ok(Map.of("categories", documentService.getCategoryTree()));
    }

    @Operation(summary = "카테고리 기반 문서 조회", description = "선택한 하위 카테고리에 속한 문서 목록을 조회한다. 상위 카테고리로 요청 시 에러를 반환한다.")
    @GetMapping("/by-category")
    public ResponseEntity<PageResponse<DocumentByCategoryResponse>> getDocumentsByCategory(
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(documentService.getDocumentsByCategory(categoryId, page, pageSize));
    }
}
