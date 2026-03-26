package com.kdd.domain.document.service;

import com.kdd.domain.document.dto.DocumentResponse;
import com.kdd.domain.document.dto.DocumentStatusResponse;
import com.kdd.domain.document.entity.*;
import com.kdd.domain.document.repository.DocumentCategoryRepository;
import com.kdd.domain.document.repository.DocumentChunkRepository;
import com.kdd.domain.document.repository.DocumentRepository;
import com.kdd.global.exception.CustomException;
import com.kdd.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentCategoryRepository categoryRepository;

    @Transactional
    public DocumentResponse upload(MultipartFile file, String title, Long categoryId) {
        if (file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        try (var inputStream = file.getInputStream()) {
            byte[] header = new byte[5];
            int bytesRead = inputStream.readNBytes(header, 0, 5);
            if (bytesRead < 4 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F') {
                throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        DocumentCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        String originalFileName = file.getOriginalFilename();
        if (title == null || title.isBlank()) {
            title = originalFileName != null
                    ? originalFileName.replaceFirst("[.][^.]+$", "")
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

        Document document = Document.builder()
                .title(title)
                .content(content)
                .category(category)
                .source(DocumentSource.KMU)
                .originalFilename(originalFileName)
                .mimeType("application/pdf")
                .fileSize(file.getSize())
                .status(status)
                .build();
        documentRepository.save(document);

        if (status == DocumentStatus.COMPLETED && !content.isBlank()) {
            int chunkSize = 550, overlap = 100, idx = 0, start = 0;
            while (start < content.length()) {
                int end = Math.min(start + chunkSize, content.length());
                String chunk = content.substring(start, end).strip();
                if (!chunk.isEmpty()) {
                    chunkRepository.save(DocumentChunk.builder()
                            .document(document).content(chunk).chunkIndex(idx++).hasTable(false).build());
                }
                start += chunkSize - overlap;
            }
        }

        return DocumentResponse.from(document);
    }

    public List<DocumentResponse> getDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(DocumentResponse::from).toList();
    }

    public DocumentStatusResponse getDocumentStatus(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
        return DocumentStatusResponse.from(document);
    }

    @Transactional
    public DocumentStatusResponse reprocess(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new CustomException(ErrorCode.DOCUMENT_ALREADY_PROCESSING);
        }
        document.updateStatus(DocumentStatus.PENDING);
        return DocumentStatusResponse.from(document);
    }

    @Transactional
    public void delete(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);
    }
}
