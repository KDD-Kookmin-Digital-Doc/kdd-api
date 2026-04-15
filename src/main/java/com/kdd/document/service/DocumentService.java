package com.kdd.document.service;

import com.kdd.ai.client.AiServerClient;
import com.kdd.ai.dto.AiEmbedRequest;
import com.kdd.ai.dto.AiEmbedResponse;
import com.kdd.ai.exception.AiServerException;
import com.kdd.document.dto.*;
import com.kdd.document.entity.*;
import com.kdd.document.repository.DocumentCategoryRepository;
import com.kdd.document.repository.DocumentRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final DocumentPersistenceService persistenceService;
    private final AiServerClient aiServerClient;

    private static final int POPULAR_DAYS = 7;
    private static final int POPULAR_LIMIT = 10;

    private static final Sort SORT_LATEST = Sort.by("updatedAt", "id").descending();

    /**
     * 문서 업로드 플로우.
     * <ol>
     *     <li>트랜잭션 없이 파일 검증·PDF 텍스트 추출</li>
     *     <li>독립 트랜잭션으로 Document/Chunk 저장 (status=PROCESSING)</li>
     *     <li>트랜잭션 밖에서 AI embed 호출 (최대 120초)</li>
     *     <li>독립 트랜잭션으로 최종 상태(COMPLETED/FAILED) 업데이트</li>
     * </ol>
     * AI 호출이 트랜잭션 경계 밖에서 일어나므로 DB 락을 길게 잡지 않는다.
     */
    public DocumentDetailResponse upload(MultipartFile file, DocumentUploadRequest request) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        validatePdf(file);

        String originalFilename = file.getOriginalFilename();
        String title = resolveTitle(request.getTitle(), originalFilename);

        // PDF 텍스트 추출 (트랜잭션 없음)
        String content = "";
        DocumentStatus initialStatus = DocumentStatus.PROCESSING;
        try (var pdfDoc = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            content = new org.apache.pdfbox.text.PDFTextStripper().getText(pdfDoc);
        } catch (Exception e) {
            log.warn("PDF 텍스트 추출 실패: {}", e.getMessage());
            initialStatus = DocumentStatus.FAILED;
        }

        if (!content.isBlank()) {
            content = content.replace("\u0000", "");
        }
        if (initialStatus == DocumentStatus.PROCESSING && content.isBlank()) {
            initialStatus = DocumentStatus.FAILED;
        }

        DocumentSource source = parseSource(request.getSource());

        // 1차 트랜잭션: 저장 + embed 요청 body 준비
        DocumentPersistenceService.SavePayload payload = persistenceService.saveDocumentAndBuildEmbedRequest(
                title, content, request.getCategoryId(), source, originalFilename, file.getSize(), initialStatus
        );

        // PDF 파싱 실패 또는 빈 컨텐츠는 AI 호출 없이 종료
        if (initialStatus == DocumentStatus.FAILED || payload.embedRequest() == null) {
            return DocumentDetailResponse.from(persistenceService.findActiveForResponse(payload.documentId()));
        }

        // 트랜잭션 밖에서 AI embed 호출
        boolean aiSuccess = invokeAiEmbed(payload.embedRequest());

        // 2차 트랜잭션: 최종 상태 업데이트
        DocumentStatus finalStatus = aiSuccess ? DocumentStatus.COMPLETED : DocumentStatus.FAILED;
        persistenceService.updateStatus(payload.documentId(), finalStatus);

        return DocumentDetailResponse.from(persistenceService.findActiveForResponse(payload.documentId()));
    }

    private String resolveTitle(String requested, String originalFilename) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return originalFilename != null
                ? originalFilename.replaceFirst("[.][^.]+$", "")
                : "제목 없음";
    }

    /**
     * AI 서버 embed 호출. 성공(success) → true, partial_failure/예외 → false.
     * 반환값만 전달하고 상태 업데이트는 호출자가 별도 트랜잭션에서 수행한다.
     */
    private boolean invokeAiEmbed(AiEmbedRequest request) {
        try {
            AiEmbedResponse response = aiServerClient.embed(request);
            if (!response.isSuccess()) {
                log.warn("[AI] embed partial_failure for doc_id={}, embedded={}",
                        request.docId(), response.embeddedChunkCount());
                return false;
            }
            return true;
        } catch (AiServerException e) {
            log.error("[AI] embed failed for doc_id={}", request.docId(), e);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree() {
        List<DocumentCategory> all = categoryRepository.findAllOrdered();

        // parent가 null인 최상위 카테고리부터 트리 구성
        return all.stream()
                .filter(c -> c.getParent() == null)
                .map(root -> buildTree(root, all))
                .toList();
    }

    private CategoryTreeResponse buildTree(DocumentCategory parent, List<DocumentCategory> all) {
        List<CategoryTreeResponse> children = all.stream()
                .filter(c -> c.getParent() != null && c.getParent().getId().equals(parent.getId()))
                .map(child -> buildTree(child, all))
                .toList();
        return CategoryTreeResponse.from(parent, children);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentByCategoryResponse> getDocumentsByCategory(Long categoryId, int page, int pageSize) {
        validatePageParams(page, pageSize);
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 하위 카테고리가 존재하면 상위 카테고리이므로 조회 차단
        List<DocumentCategory> all = categoryRepository.findAllOrdered();
        boolean hasChildren = all.stream()
                .anyMatch(c -> c.getParent() != null && c.getParent().getId().equals(categoryId));
        if (hasChildren) {
            throw new BusinessException(ErrorCode.PARENT_CATEGORY_NOT_ALLOWED);
        }

        return PageResponse.from(
                documentRepository.findByCategoryIds(List.of(categoryId),
                        PageRequest.of(page, pageSize, Sort.by("updatedAt", "id").descending())),
                DocumentByCategoryResponse::from
        );
    }

    @Transactional
    public DocumentDetailPublicResponse getDocumentDetail(Long documentId) {
        documentRepository.incrementViewCount(documentId);
        Document document = findDocumentOrThrow(documentId);
        return DocumentDetailPublicResponse.from(document);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentSearchResponse> searchDocuments(Long categoryId, String keyword,
                                                                String sort, int page, int pageSize) {
        validatePageParams(page, pageSize);
        validateSort(sort);

        List<Long> categoryIds = List.of();
        boolean hasCategoryFilter = categoryId != null;
        if (hasCategoryFilter) {
            categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            List<DocumentCategory> all = categoryRepository.findAllOrdered();
            categoryIds = new ArrayList<>();
            collectCategoryIds(categoryId, all, categoryIds);
        }

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String escapedKeyword = normalizedKeyword == null ? null : escapeLike(normalizedKeyword);

        if ("popular".equalsIgnoreCase(sort)) {
            LocalDateTime since = LocalDateTime.now().minusDays(POPULAR_DAYS);
            return PageResponse.from(
                    documentRepository.searchActiveByPopularity(since, hasCategoryFilter, categoryIds,
                            escapedKeyword, PageRequest.of(page, pageSize)),
                    DocumentSearchResponse::from
            );
        }

        return PageResponse.from(
                documentRepository.searchActive(hasCategoryFilter, categoryIds, escapedKeyword,
                        PageRequest.of(page, pageSize, SORT_LATEST)),
                DocumentSearchResponse::from
        );
    }

    @Transactional(readOnly = true)
    public List<PopularDocumentResponse> getPopularDocuments() {
        LocalDateTime since = LocalDateTime.now().minusDays(POPULAR_DAYS);
        return documentRepository.findPopularDocuments(since, PageRequest.of(0, POPULAR_LIMIT)).stream()
                .map(PopularDocumentResponse::from)
                .toList();
    }

    private void collectCategoryIds(Long parentId, List<DocumentCategory> all, List<Long> result) {
        result.add(parentId);
        all.stream()
                .filter(c -> c.getParent() != null && c.getParent().getId().equals(parentId))
                .forEach(child -> collectCategoryIds(child.getId(), all, result));
    }

    private void validateSort(String sort) {
        if (sort == null || sort.isBlank() || "latest".equalsIgnoreCase(sort) || "popular".equalsIgnoreCase(sort)) {
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentListResponse> getDocuments(int page, int size) {
        validatePageParams(page, size);
        return PageResponse.from(
                documentRepository.findAllActive(PageRequest.of(page, size, Sort.by("createdAt", "id").descending())),
                DocumentListResponse::from
        );
    }

    @Transactional
    public DocumentDetailResponse updateCategory(Long documentId, Long categoryId) {
        Document document = findDocumentOrThrow(documentId);
        DocumentCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        document.updateCategory(category);
        return DocumentDetailResponse.from(document);
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getDocumentStatus(Long documentId) {
        Document document = findDocumentOrThrow(documentId);
        return DocumentStatusResponse.from(document);
    }

    /**
     * 재처리 플로우.
     * <ol>
     *     <li>독립 트랜잭션으로 상태 검증 + REPROCESSING 전이 + 기존 청크로 embed 요청 body 생성</li>
     *     <li>트랜잭션 밖에서 AI delete (기존 벡터/캐시 제거)</li>
     *     <li>트랜잭션 밖에서 AI embed</li>
     *     <li>독립 트랜잭션으로 최종 상태(COMPLETED/FAILED) 전이</li>
     * </ol>
     */
    public DocumentReprocessResponse reprocess(Long documentId) {
        DocumentPersistenceService.SavePayload payload =
                persistenceService.startReprocessingAndBuildRequest(documentId);

        if (payload.embedRequest() == null) {
            persistenceService.updateStatus(documentId, DocumentStatus.FAILED);
            return DocumentReprocessResponse.from(persistenceService.findActiveForResponse(documentId));
        }

        // 트랜잭션 밖에서 AI 기존 벡터 삭제 (실패해도 진행, 멱등성 보장)
        try {
            aiServerClient.deleteDocument(String.valueOf(documentId));
        } catch (AiServerException e) {
            log.error("[AI] reprocess delete step failed for doc_id={}, continuing", documentId, e);
        }

        boolean success = invokeAiEmbed(payload.embedRequest());
        DocumentStatus finalStatus = success ? DocumentStatus.COMPLETED : DocumentStatus.FAILED;
        persistenceService.updateStatus(documentId, finalStatus);

        return DocumentReprocessResponse.from(persistenceService.findActiveForResponse(documentId));
    }

    /**
     * 문서 삭제 플로우.
     * <ol>
     *     <li>독립 트랜잭션으로 활성 문서 존재 여부만 검증</li>
     *     <li>트랜잭션 밖에서 AI 삭제 호출 (실패 시 로그만 남기고 진행)</li>
     *     <li>독립 트랜잭션으로 청크 hard delete + Document soft delete</li>
     * </ol>
     */
    public void delete(Long documentId) {
        persistenceService.assertActiveExists(documentId);

        try {
            aiServerClient.deleteDocument(String.valueOf(documentId));
        } catch (AiServerException e) {
            log.error("[AI] delete call failed for doc_id={}, continuing with BE deletion", documentId, e);
        }

        persistenceService.hardDeleteChunksAndSoftDeleteDocument(documentId);
    }

    private Document findDocumentOrThrow(Long documentId) {
        return documentRepository.findActiveById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private void validatePageParams(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private DocumentSource parseSource(String source) {
        try {
            return DocumentSource.from(source);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validatePdf(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            byte[] header = new byte[5];
            int bytesRead = inputStream.readNBytes(header, 0, 5);
            if (bytesRead < 4 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F') {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

}
