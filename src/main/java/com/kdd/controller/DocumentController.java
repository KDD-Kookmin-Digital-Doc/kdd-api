package com.kdd.controller;

import com.kdd.config.AppConfig;
import com.kdd.entity.DocumentChunk;
import com.kdd.repository.DocumentChunkRepository;
import com.kdd.service.ChunkerService;
import com.kdd.service.JwtService;
import com.kdd.service.NoticeCrawlerService;
import com.kdd.service.PdfParserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final AppConfig appConfig;
    private final JwtService jwtService;
    private final PdfParserService pdfParser;
    private final ChunkerService chunkerService;
    private final DocumentChunkRepository chunkRepo;
    private final NoticeCrawlerService noticeCrawler;

    private void requireAdmin(HttpServletRequest request) {
        String email = jwtService.extractEmail(request);
        if (email == null || !appConfig.getDocAdminEmails().contains(email)) {
            throw new RuntimeException("Admin access required");
        }
    }

    @PostMapping("/upload-rdb")
    public ResponseEntity<?> uploadRdbOnly(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "") String category,
            HttpServletRequest request) {
        requireAdmin(request);
        try {
            Path uploadDir = Paths.get(appConfig.getUploadDir());
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(file.getOriginalFilename());
            file.transferTo(filePath.toFile());

            if (!file.getOriginalFilename().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(Map.of("detail", "Unsupported file type"));
            }

            List<Map<String, Object>> parsed = pdfParser.parse(filePath.toString(), file.getOriginalFilename());

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
                    "filename", file.getOriginalFilename(),
                    "chunks", saved
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("detail", e.getMessage()));
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
                for (File f : uploadDir.toFile().listFiles()) {
                    if (f.isFile()) {
                        long chunkCount = indexed.getOrDefault(f.getName(), 0L);
                        files.add(Map.of(
                                "filename", f.getName(),
                                "size", f.length(),
                                "type", f.getName().substring(f.getName().lastIndexOf(".")),
                                "chunk_count", chunkCount,
                                "indexed", chunkCount > 0
                        ));
                    }
                }
            }
            return ResponseEntity.ok(Map.of("documents", files));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("detail", e.getMessage()));
        }
    }

    @Transactional
    @DeleteMapping("/documents/{filename}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String filename,
            HttpServletRequest request) {
        requireAdmin(request);
        String nfc = Normalizer.normalize(filename, Normalizer.Form.NFC);
        String nfd = Normalizer.normalize(filename, Normalizer.Form.NFD);

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

        Path filePath = Paths.get(appConfig.getUploadDir(), filename);
        try { Files.deleteIfExists(filePath); } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("message", filename + " 삭제 완료", "deleted_chunks", deleted));
    }

    @GetMapping("/documents/{filename}/preview")
    public ResponseEntity<Resource> previewDocument(@PathVariable String filename) {
        Path filePath = Paths.get(appConfig.getUploadDir(), filename);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/crawl/notices/list")
    public ResponseEntity<?> getNoticeList() {
        List<Object[]> rows = chunkRepo.findNoticeDocs();
        List<Map<String, Object>> notices = new ArrayList<>();
        for (Object[] row : rows) {
            notices.add(Map.of(
                    "doc_name", row[0],
                    "chunk_count", row[1],
                    "source_url", ""
            ));
        }
        return ResponseEntity.ok(Map.of("notices", notices));
    }

    @PostMapping("/crawl/notices")
    public ResponseEntity<?> crawlNotices(
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request) {
        try {
            requireAdmin(request);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("detail", "Admin access required"));
        }
        try {
            Map<String, Object> result = noticeCrawler.crawlRecentNotices(days);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return ResponseEntity.status(500).body(Map.of("detail", msg));
        }
    }
}
