package com.kdd.document.service;

import com.kdd.ai.client.AiServerClient;
import com.kdd.ai.dto.AiEmbedRequest;
import com.kdd.ai.dto.AiEmbedResponse;
import com.kdd.ai.exception.AiServerException;
import com.kdd.document.dto.*;
import com.kdd.document.entity.*;
import com.kdd.document.repository.DocumentCategoryRepository;
import com.kdd.document.repository.DocumentRepository;
import com.kdd.document.repository.DocumentViewRepository;
import com.kdd.document.storage.DocumentFileStorage;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.response.PageResponse;

import java.nio.file.Path;
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
    private final DocumentViewRepository documentViewRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final DocumentPersistenceService persistenceService;
    private final AiServerClient aiServerClient;
    private final DocumentFileStorage fileStorage;

    private static final int POPULAR_DAYS = 7;
    private static final int POPULAR_LIMIT = 10;
    // 요구사항 3-(2)-5: 동일 사용자+문서 10분 내 중복 조회는 1회로 집계.
    private static final int VIEW_DEDUP_MINUTES = 10;
    // 인증된 클라이언트가 pageSize=Integer.MAX_VALUE 등 비정상 값으로 전체 테이블을 읽거나
    // 인기 정렬 native query(chat_message_sources JOIN)로 DB 부하를 폭증시키지 못하도록 차단 (#58).
    private static final int MAX_PAGE_SIZE = 100;

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
        // 디스크 저장 전에 요청 값(source) 검증을 끝낸다.
        // 검증 실패가 store 이후에 일어나면 보상 흐름이 닿지 않아 디스크에 고아 PDF가 남는다.
        DocumentSource source = parseSource(request.getSource());

        // PDF 원본을 디스크에 먼저 저장한다.
        // - /documents/{id}/file 엔드포인트로 추후 다시 서빙
        // - 이어지는 텍스트 추출이 file.getBytes()로 전체 바이트를 힙에 적재하지 않고
        //   RandomAccessReadBufferedFile(4KB 페이지 캐시)로 스트리밍 파싱하도록 함 (#46 OOM 방지)
        String storageKey = fileStorage.store(file);
        Path storedPath = fileStorage.getPath(storageKey);

        // PDF 텍스트 추출 (트랜잭션 없음, 페이지별 분리하여 청크 tagging용 정보 확보)
        List<String> pageTexts = List.of();
        DocumentStatus initialStatus = DocumentStatus.PROCESSING;
        try (var pdfDoc = org.apache.pdfbox.Loader.loadPDF(storedPath.toFile())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            int pageCount = pdfDoc.getNumberOfPages();
            List<String> pages = new ArrayList<>(pageCount);
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(pdfDoc);
                pages.add(pageText == null ? "" : pageText.replace("\u0000", ""));
            }
            pageTexts = pages;
        } catch (java.io.IOException | RuntimeException e) {
            // PDFBox 손상 PDF/암호화/IO 등 추출 실패 → status=FAILED 로 진행 (디스크 PDF 는 reprocess 용도로 유지).
            log.warn("PDF 텍스트 추출 실패: {}", e.getMessage());
            initialStatus = DocumentStatus.FAILED;
        } catch (Error e) {
            // OOM 등 JVM Error 는 DB 트랜잭션 시작 전 단계에서 발생하므로 디스크 PDF 가 orphan 으로 남는다.
            // 보상 삭제 후 재전파 (#59). Spring 의 GlobalExceptionHandler 는 Exception 만 매핑하므로
            // 여기서 직접 로깅하지 않으면 Error 발생 사실이 운영 로그에 남지 않는다.
            log.error("PDF 추출 중 JVM Error 발생, 디스크 PDF 보상 삭제: storageKey={}", storageKey, e);
            fileStorage.deleteIfExists(storageKey);
            throw e;
        }

        String content = String.join("\n", pageTexts);
        if (initialStatus == DocumentStatus.PROCESSING && content.isBlank()) {
            initialStatus = DocumentStatus.FAILED;
        }

        // 1차 트랜잭션: 저장 + embed 요청 body 준비
        // DB 저장이 실패하면 디스크에 이미 쓴 PDF가 고아로 남으므로 보상 삭제 후 재던진다.
        // OOM 등 Error 도 트랜잭션이 commit 되지 못한 채 escape 할 수 있어 같이 정리 (#59).
        DocumentPersistenceService.SavePayload payload;
        try {
            payload = persistenceService.saveDocumentAndBuildEmbedRequest(
                    title, content, pageTexts, request.getCategoryId(), source, originalFilename,
                    storageKey, file.getSize(), initialStatus
            );
        } catch (RuntimeException | Error e) {
            // Error 는 GlobalExceptionHandler 가 안 잡아주니 운영 로그에 직접 남긴다.
            log.error("문서 저장 트랜잭션 실패, 디스크 PDF 보상 삭제: storageKey={}", storageKey, e);
            fileStorage.deleteIfExists(storageKey);
            throw e;
        }

        // PDF 파싱 실패 또는 빈 컨텐츠는 AI 호출 없이 종료
        // initialStatus=PROCESSING인데 embedRequest가 null이면 청킹 결과가 0개인 비정상 케이스 → FAILED로 명시 전이
        if (initialStatus == DocumentStatus.FAILED || payload.embedRequest() == null) {
            if (initialStatus == DocumentStatus.PROCESSING) {
                persistenceService.updateStatus(payload.documentId(), DocumentStatus.FAILED);
            }
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

        // 요구사항 3-(2)-2: 상위 카테고리 선택 시 하위 카테고리 문서도 함께 조회한다.
        // searchDocuments와 동일한 expansion 정책을 적용 — 단일 카테고리는 그대로, 상위는 자손까지 평탄화.
        List<DocumentCategory> all = categoryRepository.findAllOrdered();
        List<Long> categoryIds = new ArrayList<>();
        collectCategoryIds(categoryId, all, categoryIds);

        return PageResponse.from(
                documentRepository.findByCategoryIds(categoryIds,
                        PageRequest.of(page, pageSize, Sort.by("updatedAt", "id").descending())),
                DocumentByCategoryResponse::from
        );
    }

    @Transactional
    public DocumentDetailPublicResponse getDocumentDetail(Long documentId, Long userId, boolean isAdmin) {
        // 관리자 조회는 view_count/document_views 추적에서 제외 — 명세 "관리자 및 테스트 계정의 이벤트는
        // 집계에서 제외" 정책 + popular 쿼리의 admin 필터(u.role <> 'admin')와 일관성 유지.
        // 추적을 건너뛰어도 dedup 분기 이후의 findCompletedDocumentOrThrow는 그대로 실행되어 404 응답은 보장된다.
        if (isAdmin) {
            return DocumentDetailPublicResponse.from(findCompletedDocumentOrThrow(documentId));
        }

        // 요구사항 3-(2)-5: 동일 사용자+문서 10분 내 중복 조회는 view_count 누적 및 이벤트 적재를 모두 스킵.
        // 두 작업이 분리되면 view_count(누적)와 인기 점수 윈도우 집계가 어긋날 수 있어 dedup 분기는 하나로 묶는다.
        // existsWithinWindow는 native query로 DB now()를 기준 — JVM/PG TZ 불일치 방어.
        boolean withinDedupWindow = documentViewRepository.existsWithinWindow(
                documentId, userId, VIEW_DEDUP_MINUTES);

        if (!withinDedupWindow) {
            // V10의 (document_id, user_id, 10분 epoch 버킷) unique 인덱스 + ON CONFLICT DO NOTHING으로
            // read-then-write race를 DB 레벨에서 차단. 두 동시 요청이 모두 existsWithinWindow=false를 봐도
            // 실제 INSERT는 한 번만 성공하고, 두 번째 시도는 inserted=0을 받아 view_count 증가도 함께 건너뛴다.
            // ConstraintViolationException 캐치 대신 ON CONFLICT를 쓰는 이유: PG는 violation 발생 시 TX를
            // aborted 상태로 만들어 후속 쿼리가 모두 실패하지만, ON CONFLICT는 DB가 swallow 해주므로 안전.
            int inserted = documentViewRepository.insertIfAbsent(documentId, userId);
            if (inserted == 1) {
                // 단일 UPDATE로 status=COMPLETED + view_count +1을 원자적으로 처리.
                // affected rows = 0이면 대상 문서 없음(미존재 / 삭제 / NOT COMPLETED) → 404.
                // 분리된 SELECT-then-UPDATE 흐름이 가졌던 reprocess race condition 차단.
                int affected = documentRepository.incrementViewCount(documentId);
                if (affected == 0) {
                    throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
                }
            } else {
                log.debug("[Document] view dedup race resolved by unique constraint: doc={}, user={}",
                        documentId, userId);
            }
        }

        // dedup으로 스킵된 경로에서도 문서가 여전히 노출 가능한 상태인지 확인해야 한다.
        return DocumentDetailPublicResponse.from(findCompletedDocumentOrThrow(documentId));
    }

    @Transactional(readOnly = true)
    public DocumentFileDownload getDocumentFile(Long documentId) {
        Document document = findCompletedDocumentOrThrow(documentId);
        if (document.getStorageKey() == null || document.getStorageKey().isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return new DocumentFileDownload(
                fileStorage.loadAsResource(document.getStorageKey()),
                document.getOriginalFilename() != null ? document.getOriginalFilename() : document.getTitle() + ".pdf"
        );
    }

    public record DocumentFileDownload(org.springframework.core.io.Resource resource, String filename) {}

    @Transactional(readOnly = true)
    public PageResponse<DocumentSearchResponse> searchDocuments(Long categoryId, String keyword,
                                                                String sort, int page, int pageSize) {
        validatePageParams(page, pageSize);
        validateSort(sort);

        List<Long> categoryIds = List.of(-1L);
        boolean hasCategoryFilter = categoryId != null;
        if (hasCategoryFilter) {
            categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            List<DocumentCategory> all = categoryRepository.findAllOrdered();
            categoryIds = new ArrayList<>();
            collectCategoryIds(categoryId, all, categoryIds);
        }

        // Hibernate 6 + PostgreSQL JDBC가 타입 힌트 없는 null String 파라미터를 bytea로 추론해
        // LIKE 절이 깨지는 회귀를 피하기 위해, keyword는 항상 non-null(빈 문자열)로 전달하고
        // hasKeyword 플래그로 LIKE 적용 여부를 결정한다 (#72).
        String normalizedKeyword = (keyword == null) ? "" : keyword.trim();
        boolean hasKeyword = !normalizedKeyword.isEmpty();
        String escapedKeyword = hasKeyword ? escapeLike(normalizedKeyword) : "";

        if ("popular".equalsIgnoreCase(sort)) {
            LocalDateTime since = LocalDateTime.now().minusDays(POPULAR_DAYS);
            return PageResponse.from(
                    documentRepository.searchActiveByPopularity(since, hasCategoryFilter, categoryIds,
                            hasKeyword, escapedKeyword, PageRequest.of(page, pageSize)),
                    DocumentSearchResponse::from
            );
        }

        return PageResponse.from(
                documentRepository.searchActive(hasCategoryFilter, categoryIds, hasKeyword, escapedKeyword,
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
    public PageResponse<DocumentListResponse> getDocuments(int page, int pageSize) {
        validatePageParams(page, pageSize);
        return PageResponse.from(
                documentRepository.findAllActive(PageRequest.of(page, pageSize, Sort.by("createdAt", "id").descending())),
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
            aiServerClient.deleteDocument(documentId);
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
        // 삭제 전 storageKey를 먼저 확보하여 BE soft delete 이후 디스크 파일도 같이 정리
        String storageKey = persistenceService.assertActiveExistsAndGetStorageKey(documentId);

        try {
            aiServerClient.deleteDocument(documentId);
        } catch (AiServerException e) {
            log.error("[AI] delete call failed for doc_id={}, continuing with BE deletion", documentId, e);
        }

        persistenceService.hardDeleteChunksAndSoftDeleteDocument(documentId);
        // 디스크 PDF 정리 — 실패해도 BE/AI 삭제는 이미 완료됐으므로 스왈로우 (deleteIfExists 내부에서 처리)
        fileStorage.deleteIfExists(storageKey);
    }

    private Document findDocumentOrThrow(Long documentId) {
        return documentRepository.findActiveById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /**
     * 일반 사용자 진입점 전용. 처리 완료된 문서만 통과시키며 그 외 status는 404로 차단한다.
     * 관리자 진입점은 findDocumentOrThrow 사용 (모든 status 조회 가능).
     */
    private Document findCompletedDocumentOrThrow(Long documentId) {
        Document document = findDocumentOrThrow(documentId);
        if (document.getStatus() != DocumentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    private void validatePageParams(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
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
