package com.kdd.document.controller;

import com.kdd.document.dto.CategoryTreeResponse;
import com.kdd.document.dto.DocumentByCategoryResponse;
import com.kdd.document.dto.DocumentDetailPublicResponse;
import com.kdd.document.dto.DocumentSearchResponse;
import com.kdd.document.dto.PopularDocumentResponse;
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

    @Operation(summary = "문서 목록 조회", description = "카테고리 필터, 제목 키워드 검색, 정렬(latest/popular)을 지원하는 문서 목록 조회. 상위 카테고리 선택 시 하위 카테고리 문서도 함께 조회된다.")
    @GetMapping
    public ResponseEntity<PageResponse<DocumentSearchResponse>> searchDocuments(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(documentService.searchDocuments(categoryId, keyword, sort, page, pageSize));
    }

    @Operation(summary = "인기 문서 조회", description = "최근 7일 기준 조회수/참조수 기반 인기 문서 TOP 10을 조회한다.")
    @GetMapping("/popular")
    public ResponseEntity<Map<String, List<PopularDocumentResponse>>> getPopularDocuments() {
        return ResponseEntity.ok(Map.of("documents", documentService.getPopularDocuments()));
    }

    @Operation(summary = "문서 상세 조회", description = "특정 문서의 상세 정보를 조회한다.")
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentDetailPublicResponse> getDocumentDetail(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentService.getDocumentDetail(documentId));
    }
}
