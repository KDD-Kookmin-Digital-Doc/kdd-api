package com.kdd.document.service;

import com.kdd.document.dto.*;
import com.kdd.document.entity.*;
import com.kdd.document.repository.DocumentCategoryRepository;
import com.kdd.document.repository.DocumentChunkRepository;
import com.kdd.document.repository.DocumentRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import com.kdd.global.response.PageResponse;
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
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentCategoryRepository categoryRepository;

    private static final int CHUNK_SIZE = 550;
    private static final int CHUNK_OVERLAP = 100;

    @Transactional
    public DocumentDetailResponse upload(MultipartFile file, DocumentUploadRequest request) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        validatePdf(file);

        DocumentCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        String title = request.getTitle();
        String originalFilename = file.getOriginalFilename();
        if (title == null || title.isBlank()) {
            title = originalFilename != null
                    ? originalFilename.replaceFirst("[.][^.]+$", "")
                    : "제목 없음";
        }

        String content = "";
        DocumentStatus status = DocumentStatus.COMPLETED;
        try (var pdfDoc = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            content = new org.apache.pdfbox.text.PDFTextStripper().getText(pdfDoc);
        } catch (Exception e) {
            log.warn("PDF 텍스트 추출 실패: {}", e.getMessage());
            status = DocumentStatus.FAILED;
        }

        if (!content.isBlank()) {
            content = content.replace("\u0000", "");
        }

        if (status == DocumentStatus.COMPLETED && content.isBlank()) {
            status = DocumentStatus.FAILED;
        }

        Document document = Document.builder()
                .title(title)
                .content(content)
                .category(category)
                .source(parseSource(request.getSource()))
                .originalFilename(originalFilename)
                .mimeType("application/pdf")
                .fileSize(file.getSize())
                .status(status)
                .build();
        documentRepository.save(document);

        if (status == DocumentStatus.COMPLETED && !content.isBlank()) {
            createChunks(document, content);
        }

        return DocumentDetailResponse.from(document);
    }

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

    public PageResponse<DocumentByCategoryResponse> getDocumentsByCategory(Long categoryId, int page, int pageSize) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        // 해당 카테고리 + 하위 카테고리 ID 수집
        List<DocumentCategory> all = categoryRepository.findAllOrdered();
        List<Long> categoryIds = new ArrayList<>();
        collectCategoryIds(categoryId, all, categoryIds);

        return PageResponse.from(
                documentRepository.findByCategoryIds(categoryIds,
                        PageRequest.of(page, pageSize, Sort.by("createdAt", "id").descending())),
                DocumentByCategoryResponse::from
        );
    }

    private void collectCategoryIds(Long parentId, List<DocumentCategory> all, List<Long> result) {
        result.add(parentId);
        all.stream()
                .filter(c -> c.getParent() != null && c.getParent().getId().equals(parentId))
                .forEach(child -> collectCategoryIds(child.getId(), all, result));
    }

    public PageResponse<DocumentListResponse> getDocuments(int page, int size) {
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

    public DocumentStatusResponse getDocumentStatus(Long documentId) {
        Document document = findDocumentOrThrow(documentId);
        return DocumentStatusResponse.from(document);
    }

    @Transactional
    public DocumentReprocessResponse reprocess(Long documentId) {
        Document document = findDocumentOrThrow(documentId);
        if (document.getStatus() == DocumentStatus.PROCESSING || document.getStatus() == DocumentStatus.REPROCESSING) {
            throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_PROCESSING);
        }
        document.updateStatus(DocumentStatus.REPROCESSING);
        return DocumentReprocessResponse.from(document);
    }

    @Transactional
    public void delete(Long documentId) {
        Document document = findDocumentOrThrow(documentId);
        chunkRepository.deleteByDocumentId(documentId);
        document.softDelete();
    }

    private Document findDocumentOrThrow(Long documentId) {
        return documentRepository.findActiveById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
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

    private void createChunks(Document document, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int idx = 0;
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + CHUNK_SIZE, content.length());
            String chunk = content.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(DocumentChunk.builder()
                        .document(document)
                        .content(chunk)
                        .chunkIndex(idx++)
                        .hasTable(false)
                        .build());
            }
            start += CHUNK_SIZE - CHUNK_OVERLAP;
        }
        chunkRepository.saveAll(chunks);
    }
}
