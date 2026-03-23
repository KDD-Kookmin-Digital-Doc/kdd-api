package com.kdd.domain.document.controller;

import com.kdd.domain.document.dto.DocumentResponse;
import com.kdd.domain.document.dto.DocumentStatusResponse;
import com.kdd.domain.document.service.DocumentService;
import com.kdd.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Admin - Document", description = "관리자 문서 관리 API")
@RestController
@RequestMapping("/admin/documents")
@RequiredArgsConstructor
public class AdminDocumentController {

    private final DocumentService documentService;

    @Operation(summary = "문서 업로드")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(documentService.upload(file, title, category)));
    }

    @Operation(summary = "관리자 문서 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocuments() {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getDocuments()));
    }

    @Operation(summary = "문서 처리 상태 조회")
    @GetMapping("/{documentId}/status")
    public ResponseEntity<ApiResponse<DocumentStatusResponse>> getStatus(@PathVariable Long documentId) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getDocumentStatus(documentId)));
    }

    @Operation(summary = "문서 재처리")
    @PostMapping("/{documentId}/reprocess")
    public ResponseEntity<ApiResponse<DocumentStatusResponse>> reprocess(@PathVariable Long documentId) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.reprocess(documentId)));
    }

    @Operation(summary = "문서 삭제")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long documentId) {
        documentService.delete(documentId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
