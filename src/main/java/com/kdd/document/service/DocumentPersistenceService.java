package com.kdd.document.service;

import com.kdd.ai.dto.AiEmbedRequest;
import com.kdd.document.entity.Document;
import com.kdd.document.entity.DocumentCategory;
import com.kdd.document.entity.DocumentChunk;
import com.kdd.document.entity.DocumentSource;
import com.kdd.document.entity.DocumentStatus;
import com.kdd.document.repository.DocumentCategoryRepository;
import com.kdd.document.repository.DocumentChunkRepository;
import com.kdd.document.repository.DocumentRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 서버 호출을 트랜잭션 경계 밖으로 빼내기 위한 영속성 전용 서비스.
 * 각 public 메서드가 독립 트랜잭션을 가지므로, DocumentService 외곽 로직이
 * 이 서비스를 호출한 뒤 트랜잭션이 닫힌 상태에서 AI 네트워크 호출을 할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class DocumentPersistenceService {

    private static final int CHUNK_SIZE = 550;
    private static final int CHUNK_OVERLAP = 100;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentCategoryRepository categoryRepository;

    /**
     * Document와 Chunk를 저장하고, AI embed 요청 body를 함께 만들어 반환한다.
     * 트랜잭션이 닫힌 뒤 호출자가 embedRequest를 AI로 그대로 전송할 수 있다.
     */
    @Transactional
    public SavePayload saveDocumentAndBuildEmbedRequest(
            String title,
            String content,
            List<String> pageTexts,
            Long categoryId,
            DocumentSource source,
            String originalFilename,
            String storageKey,
            long fileSize,
            DocumentStatus initialStatus
    ) {
        DocumentCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Document document = Document.builder()
                .title(title)
                .content(content)
                .category(category)
                .source(source)
                .originalFilename(originalFilename)
                .storageKey(storageKey)
                .mimeType("application/pdf")
                .fileSize(fileSize)
                .status(initialStatus)
                .build();
        // saveAndFlush로 PK 즉시 할당하여 AI embed 요청에 Long 타입 chunk_id가 null이 되지 않도록 보장
        documentRepository.saveAndFlush(document);

        if (initialStatus == DocumentStatus.FAILED || pageTexts == null || pageTexts.isEmpty()) {
            return new SavePayload(document.getId(), null);
        }

        List<DocumentChunk> chunks = createAndSaveChunks(document, pageTexts);
        if (chunks.isEmpty()) {
            return new SavePayload(document.getId(), null);
        }
        AiEmbedRequest embedRequest = buildEmbedRequest(document, chunks);
        return new SavePayload(document.getId(), embedRequest);
    }

    /** 문서 상태를 변경하고 커밋. REPROCESSING 중인 경우 COMPLETED/FAILED로 전이할 때 주로 사용. */
    @Transactional
    public void updateStatus(Long documentId, DocumentStatus status) {
        Document document = findActiveOrThrow(documentId);
        document.updateStatus(status);
    }

    /** 조회 전용 — Document 존재 여부 검증 + DocumentDetailResponse 재구성용. */
    @Transactional(readOnly = true)
    public Document findActiveForResponse(Long documentId) {
        return findActiveOrThrow(documentId);
    }

    /**
     * 재처리 시작: 상태 검증 후 REPROCESSING으로 전이하고,
     * 기존 청크를 읽어 AI embed 요청 body를 만들어 반환한다.
     * 청크가 없으면 SavePayload.embedRequest == null.
     */
    @Transactional
    public SavePayload startReprocessingAndBuildRequest(Long documentId) {
        Document document = findActiveOrThrow(documentId);
        if (document.getStatus() == DocumentStatus.PROCESSING
                || document.getStatus() == DocumentStatus.REPROCESSING) {
            throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_PROCESSING);
        }
        document.updateStatus(DocumentStatus.REPROCESSING);

        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (chunks.isEmpty()) {
            return new SavePayload(documentId, null);
        }
        return new SavePayload(documentId, buildEmbedRequest(document, chunks));
    }

    /**
     * 삭제 대상 존재 여부 검증과 함께 storageKey를 반환한다.
     * 호출자가 트랜잭션 밖에서 디스크 파일을 정리할 수 있도록 키를 노출.
     */
    @Transactional(readOnly = true)
    public String assertActiveExistsAndGetStorageKey(Long documentId) {
        return findActiveOrThrow(documentId).getStorageKey();
    }

    /** 청크 hard delete + Document soft delete. */
    @Transactional
    public void hardDeleteChunksAndSoftDeleteDocument(Long documentId) {
        Document document = findActiveOrThrow(documentId);
        chunkRepository.deleteByDocumentId(documentId);
        document.softDelete();
    }

    private Document findActiveOrThrow(Long documentId) {
        return documentRepository.findActiveById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    private List<DocumentChunk> createAndSaveChunks(Document document, List<String> pageTexts) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int idx = 0;
        // 페이지별로 독립 청킹하여 청크가 페이지 경계를 넘지 않도록 보장 (RAG 출처 page 태깅 정확도 확보)
        for (int pageIdx = 0; pageIdx < pageTexts.size(); pageIdx++) {
            String pageContent = pageTexts.get(pageIdx);
            if (pageContent == null || pageContent.isBlank()) continue;
            int pageNumber = pageIdx + 1;

            int start = 0;
            while (start < pageContent.length()) {
                int end = Math.min(start + CHUNK_SIZE, pageContent.length());
                // 이모지 등 서로게이트 페어(U+10000 이상)가 경계에서 쪼개져 깨진 문자가 임베딩에 섞이지 않도록 보정
                if (end < pageContent.length() && Character.isHighSurrogate(pageContent.charAt(end - 1))) {
                    end--;
                }
                String chunkText = pageContent.substring(start, end).strip();
                if (!chunkText.isEmpty()) {
                    chunks.add(DocumentChunk.builder()
                            .document(document)
                            .content(chunkText)
                            .chunkIndex(idx++)
                            .page(pageNumber)
                            .hasTable(false)
                            .build());
                }
                int nextStart = start + CHUNK_SIZE - CHUNK_OVERLAP;
                if (nextStart < pageContent.length() && Character.isLowSurrogate(pageContent.charAt(nextStart))) {
                    nextStart++;
                }
                start = nextStart;
            }
        }
        // saveAllAndFlush로 PK 즉시 할당 (batch_size 설정 변경 대비)
        return chunkRepository.saveAllAndFlush(chunks);
    }

    private AiEmbedRequest buildEmbedRequest(Document document, List<DocumentChunk> chunks) {
        // AI 서버가 doc_name/category를 min_length=1로 검증하므로 null/blank 방어
        String docName = (document.getOriginalFilename() != null && !document.getOriginalFilename().isBlank())
                ? document.getOriginalFilename()
                : document.getTitle();
        String categoryName = (document.getCategory() != null && document.getCategory().getName() != null
                && !document.getCategory().getName().isBlank())
                ? document.getCategory().getName()
                : "미분류";
        AiEmbedRequest.Metadata metadata = new AiEmbedRequest.Metadata(
                docName,
                categoryName,
                null // enforcement_date: 의도적으로 null, AI 서버가 Optional[date]로 전환한 뒤 시행일 정책 확정하면 반영 예정
        );
        List<AiEmbedRequest.Chunk> aiChunks = chunks.stream()
                .map(c -> new AiEmbedRequest.Chunk(
                        c.getId(),
                        c.getContent(),
                        c.getPage() == null ? 1 : c.getPage()
                ))
                .toList();
        return new AiEmbedRequest(
                document.getId(),
                metadata,
                aiChunks
        );
    }

    /**
     * DB 저장 결과 + AI 호출을 위한 요청 body 묶음.
     * embedRequest가 null이면 청크가 없거나 FAILED 상태라 AI 호출 스킵해야 함.
     */
    public record SavePayload(Long documentId, AiEmbedRequest embedRequest) {
    }
}
