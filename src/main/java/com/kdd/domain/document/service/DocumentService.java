package com.kdd.domain.document.service;

import com.kdd.domain.document.dto.DocumentResponse;
import com.kdd.domain.document.entity.Document;
import com.kdd.domain.document.entity.DocumentChunk;
import com.kdd.domain.document.entity.DocumentSource;
import com.kdd.domain.document.entity.DocumentStatus;
import com.kdd.domain.document.repository.DocumentChunkRepository;
import com.kdd.domain.document.repository.DocumentRepository;
import com.kdd.global.exception.CustomException;
import com.kdd.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public DocumentResponse upload(MultipartFile file, String title, String category) {
        if (file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // PDF 파일 검증 (Content-Type + 매직바이트)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
        try {
            byte[] header = file.getBytes();
            if (header.length < 5 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F') {
                throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

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
                .category(category != null && !category.isBlank() ? category : "미분류")
                .source(DocumentSource.KMU)
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
}
