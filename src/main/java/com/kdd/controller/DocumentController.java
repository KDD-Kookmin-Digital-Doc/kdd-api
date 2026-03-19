package com.kdd.controller;

import com.kdd.config.AppConfig;
import com.kdd.entity.DocumentChunk;
import com.kdd.repository.DocumentChunkRepository;
import com.kdd.service.ChunkerService;
import com.kdd.service.JwtService;
import com.kdd.service.PdfParserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

@Slf4j
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
        if (!appConfig.getDocAdminEmailList().contains(email)) {
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

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex > 0 ? filename.substring(dotIndex) : "";
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
            log.error("문서 업로드 실패", e);
            return ResponseEntity.status(500).body(Map.of("detail", "문서 업로드 중 오류가 발생했습니다"));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments() {
        try {
            Map<String, Long> indexed = new HashMap<>();
            for (Object[] row : chunkRepo.countByDocNameGrouped()) {
                indexed.put((String) row[0], (Long) row[1]);
            }

            Path uploadDir = Paths.get(appConfig.getUploadDir());
            List<Map<String, Object>> files = new ArrayList<>();
            if (Files.exists(uploadDir)) {
                File[] fileList = uploadDir.toFile().listFiles();
                if (fileList != null) {
                    for (File f : fileList) {
                        if (f.isFile()) {
                            long chunkCount = indexed.getOrDefault(f.getName(), 0L);
                            files.add(Map.of(
                                    "filename", f.getName(),
                                    "size", f.length(),
                                    "type", getFileExtension(f.getName()),
                                    "chunk_count", chunkCount,
                                    "indexed", chunkCount > 0
                            ));
                        }
                    }
                }
            }
            return ResponseEntity.ok(Map.of("documents", files));
        } catch (Exception e) {
            log.error("문서 목록 조회 실패", e);
            return ResponseEntity.status(500).body(Map.of("detail", "문서 목록 조회 중 오류가 발생했습니다"));
        }
    }

    @Transactional
    @DeleteMapping("/documents/{filename}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String filename,
            HttpServletRequest request) {
        ResponseEntity<?> adminCheck = checkAdminAccess(request);
        if (adminCheck != null) return adminCheck;

        String safeFilename = sanitizeFilename(filename);
        String nfc = Normalizer.normalize(safeFilename, Normalizer.Form.NFC);
        String nfd = Normalizer.normalize(safeFilename, Normalizer.Form.NFD);

        int deleted = 0;
        List<DocumentChunk> chunks = chunkRepo.findByDocName(nfc);
        if (!chunks.isEmpty()) {
            chunkRepo.deleteByDocName(nfc);
            deleted += chunks.size();
        }
        if (!nfd.equals(nfc)) {
            List<DocumentChunk> nfdChunks = chunkRepo.findByDocName(nfd);
            if (!nfdChunks.isEmpty()) {
                chunkRepo.deleteByDocName(nfd);
                deleted += nfdChunks.size();
            }
        }

        Path filePath = Paths.get(appConfig.getUploadDir(), safeFilename);
        try { Files.deleteIfExists(filePath); } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("message", safeFilename + " 삭제 완료", "deleted_chunks", deleted));
    }

    @GetMapping("/documents/{filename}/preview")
    public ResponseEntity<Resource> previewDocument(@PathVariable String filename) {
        String safeFilename = sanitizeFilename(filename);
        Path uploadDir = Paths.get(appConfig.getUploadDir()).toAbsolutePath().normalize();
        Path filePath = uploadDir.resolve(safeFilename).normalize();

        if (!filePath.startsWith(uploadDir)) {
            return ResponseEntity.badRequest().build();
        }
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFilename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
