package com.kdd.document.controller;

import com.kdd.document.dto.*;
import com.kdd.document.service.DocumentService;
import com.kdd.global.response.MessageResponse;
import com.kdd.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin - Document", description = "관리자 문서 관리 API")
@RestController
@RequestMapping("/admin/documents")
@RequiredArgsConstructor
public class AdminDocumentController {

    private final DocumentService documentService;

    @Operation(summary = "문서 업로드", description = "관리자가 PDF 문서를 업로드한다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDetailResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("data") DocumentUploadRequest data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.upload(file, data));
    }

    @Operation(summary = "관리자 문서 목록 조회", description = "관리자가 문서 목록을 조회한다.")
    @GetMapping
    public ResponseEntity<PageResponse<DocumentListResponse>> getDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(documentService.getDocuments(page, size));
    }

    @Operation(summary = "문서 카테고리 수정", description = "관리자가 문서의 카테고리를 변경한다.")
    @PatchMapping("/{documentId}/category")
    public ResponseEntity<DocumentDetailResponse> updateCategory(
            @PathVariable Long documentId,
            @Valid @RequestBody DocumentCategoryUpdateRequest request) {
        return ResponseEntity.ok(documentService.updateCategory(documentId, request.getCategoryId()));
    }

    @Operation(summary = "문서 처리 상태 조회", description = "업로드 후 파싱/임베딩 처리 상태를 확인한다.")
    @GetMapping("/{documentId}/status")
    public ResponseEntity<DocumentStatusResponse> getStatus(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentService.getDocumentStatus(documentId));
    }

    @Operation(summary = "문서 재처리", description = "문서 파싱 실패 시 재처리를 트리거한다.")
    @PostMapping("/{documentId}/reprocess")
    public ResponseEntity<DocumentReprocessResponse> reprocess(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentService.reprocess(documentId));
    }

    @Operation(summary = "문서 삭제", description = "관리자가 문서를 삭제한다.")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long documentId) {
        documentService.delete(documentId);
        return ResponseEntity.ok(new MessageResponse("문서가 삭제되었습니다."));
    }
}
