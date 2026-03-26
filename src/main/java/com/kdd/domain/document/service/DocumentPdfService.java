package com.kdd.domain.document.service;

import com.kdd.domain.document.entity.Document;
import com.kdd.domain.document.repository.DocumentRepository;
import com.kdd.global.exception.CustomException;
import com.kdd.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPdfService {

    private final DocumentRepository documentRepository;

    private static final Path PDF_DIR = Path.of("pdf-cache");

    public byte[] getPdf(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_NOT_FOUND));

        if (document.getStorageKey() == null || document.getStorageKey().isBlank()) {
            throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        Path pdfFile = PDF_DIR.resolve(document.getStorageKey());
        if (!Files.exists(pdfFile)) {
            throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        try {
            return Files.readAllBytes(pdfFile);
        } catch (IOException e) {
            log.error("PDF 파일 읽기 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
