package com.kdd.controller;

import com.kdd.config.AppConfig;
import com.kdd.entity.DocumentChunk;
import com.kdd.repository.DocumentChunkRepository;
import com.kdd.service.ChunkerService;
import com.kdd.service.JwtService;
import com.kdd.service.PdfParserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final AppConfig appConfig;
    private final JwtService jwtService;
    private final PdfParserService pdfParser;
    private final ChunkerService chunkerService;
    private final DocumentChunkRepository chunkRepo;

    private ResponseEntity<?> checkAdminAccess(HttpServletRequest request) {
        String email = jwtService.extractEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "인증이 필요합니다"));
        }
        if (!appConfig.getDocAdminEmails().contains(email)) {
            return ResponseEntity.status(403).body(Map.of("detail", "관리자 권한이 필요합니다"));
        }
        return null;
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return UUID.randomUUID().toString() + ".pdf";
        }
        return Paths.get(original).getFileName().toString();
    }

    @PostMapping("/upload-rdb")
    public ResponseEntity<?> uploadRdbOnly(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "") String category,
            HttpServletRequest request) {
        ResponseEntity<?> adminCheck = checkAdminAccess(request);
        if (adminCheck != null) return adminCheck;
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(Map.of("detail", "Unsupported file type"));
            }

            String safeFilename = sanitizeFilename(originalFilename);
            Path uploadDir = Paths.get(appConfig.getUploadDir());
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(safeFilename);
            file.transferTo(filePath.toFile());

            List<Map<String, Object>> parsed = pdfParser.parse(filePath.toString(), safeFilename);

            List<Map<String, Object>> allChunks = new ArrayList<>();
            for (Map<String, Object> section : parsed) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) section.get("metadata");
                allChunks.addAll(chunkerService.chunk((String) section.get("content"), metadata));
            }

            int saved = 0;
            for (Map<String, Object> chunk : allChunks) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) chunk.get("metadata");
                chunkRepo.save(DocumentChunk.builder()
                        .id(UUID.randomUUID().toString())
                        .content((String) chunk.get("content"))
                        .docName((String) metadata.getOrDefault("doc_name", ""))
                        .sectionPath((String) metadata.getOrDefault("section_path", ""))
                        .page(metadata.get("page") != null ? ((Number) metadata.get("page")).intValue() : null)
                        .hasTable(Boolean.TRUE.equals(metadata.get("has_table")))
                        .sourceUrl((String) metadata.getOrDefault("source_url", ""))
                        .category(category)
                        .build());
                saved++;
            }

            return ResponseEntity.ok(Map.of(
                    "message", "RDB 저장 완료: " + saved + " chunks",
                    "filename", safeFilename,
                    "chunks", saved
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("detail", e.getMessage()));
        }
    }
}
