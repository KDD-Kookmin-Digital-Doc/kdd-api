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
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @Operation(summary = "카테고리 기반 문서 조회",
            description = "선택한 카테고리에 속한 문서 목록을 조회한다. 상위 카테고리 요청 시 하위 카테고리 문서도 함께 조회된다. page는 0부터 시작.")
    @GetMapping("/by-category")
    public ResponseEntity<PageResponse<DocumentByCategoryResponse>> getDocumentsByCategory(
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(documentService.getDocumentsByCategory(categoryId, page, pageSize));
    }

    @Operation(summary = "문서 목록 조회",
            description = "카테고리 필터, 제목 키워드 검색, 정렬(latest/popular)을 지원하는 문서 목록 조회. " +
                    "상위 카테고리 선택 시 하위 카테고리 문서도 함께 조회된다. page는 0부터 시작.")
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

    @Operation(summary = "문서 상세 조회",
            description = "특정 문서의 상세 정보를 조회한다. 호출 시 documents.view_count 누적 증가 및 " +
                    "document_views 7일 윈도우 추적 row가 기록되어 인기 문서 점수 산정에 반영된다.")
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentDetailPublicResponse> getDocumentDetail(
            @AuthenticationPrincipal Long userId,
            Authentication authentication,
            @PathVariable Long documentId) {
        // 관리자 조회는 view_count/document_views 추적에서 제외 — 인기 점수 산정 정책과 일관.
        // (popularity_score 산정 시 u.role <> 'admin' 필터가 걸려 있어 view_count만 누적되면 drift.)
        boolean isAdmin = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(documentService.getDocumentDetail(documentId, userId, isAdmin));
    }

    @Operation(summary = "문서 원본 PDF 스트리밍", description = "업로드된 PDF 원본 파일을 브라우저 뷰어/다운로드용으로 내려준다.")
    @GetMapping("/{documentId}/file")
    public ResponseEntity<Resource> getDocumentFile(@PathVariable Long documentId) {
        DocumentService.DocumentFileDownload download = documentService.getDocumentFile(documentId);
        // 한글 파일명 안전 전달을 위해 RFC 5987 형식으로 인코딩
        String encoded = URLEncoder.encode(download.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .body(download.resource());
    }
}
