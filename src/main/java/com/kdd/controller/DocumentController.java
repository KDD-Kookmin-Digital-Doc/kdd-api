package com.kdd.controller;

import com.kdd.config.AppConfig;
import com.kdd.repository.DocumentChunkRepository;
import com.kdd.security.JwtService;
import com.kdd.service.ChunkerService;
import com.kdd.service.NoticeCrawlerService;
import com.kdd.service.PdfParserService;
import com.kdd.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.kdd.entity.DocumentChunk;

import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final AppConfig appConfig;
    private final JwtService jwtService;
    private final SearchService searchService;
    private final PdfParserService pdfParser;
    private final ChunkerService chunkerService;
    private final DocumentChunkRepository chunkRepo;
    private final NoticeCrawlerService noticeCrawler;

    private void requireAdmin(String auth) {
        String email = jwtService.extractEmail(auth);
        if (email == null || !appConfig.getAdminEmailList().contains(email)) {
            throw new RuntimeException("Admin access required");
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String auth) {
        requireAdmin(auth);
        try {
            Path uploadDir = Paths.get(appConfig.getUploadDir());
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(file.getOriginalFilename());
            file.transferTo(filePath.toFile());

            List<Map<String, Object>> parsed;
            if (file.getOriginalFilename().endsWith(".pdf")) {
                parsed = pdfParser.parse(filePath.toString(), file.getOriginalFilename());
            } else {
                return ResponseEntity.badRequest().body(Map.of("detail", "Unsupported file type"));
            }

            List<Map<String, Object>> allChunks = new ArrayList<>();
            for (Map<String, Object> section : parsed) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) section.get("metadata");
                List<Map<String, Object>> sub = chunkerService.chunk((String) section.get("content"), metadata);
                allChunks.addAll(sub);
            }

            for (Map<String, Object> chunk : allChunks) {
                String chunkId = UUID.randomUUID().toString();
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) chunk.get("metadata");
                searchService.indexChunk(chunkId, (String) chunk.get("content"), metadata);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Successfully indexed " + allChunks.size() + " chunks",
                    "filename", file.getOriginalFilename(),
                    "chunks", allChunks.size()
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
            @RequestHeader("Authorization") String auth) {
        requireAdmin(auth);
        // NFD/NFC 양쪽 매칭으로 RDB에서 삭제
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

        // Qdrant 삭제 시도 (실패해도 무시)
        try { searchService.deleteByDocName(filename); } catch (Exception ignored) {}

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

    /**
     * RDB 전용 업로드 (임베딩/Qdrant 없이 SQLite에만 저장)
     */
    @PostMapping("/upload-rdb")
    public ResponseEntity<?> uploadRdbOnly(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "") String category,
            @RequestHeader("Authorization") String auth) {
        requireAdmin(auth);
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

    @PostMapping("/reset")
    public ResponseEntity<?> resetIndex(@RequestHeader("Authorization") String auth) {
        requireAdmin(auth);
        searchService.deleteAll();
        return ResponseEntity.ok(Map.of("message", "Index reset successfully"));
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
            @RequestHeader("Authorization") String auth) {
        try {
            requireAdmin(auth);
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

    @Transactional
    @PostMapping("/admin/bulk-update-category")
    public ResponseEntity<?> bulkUpdateCategory(@RequestHeader("Authorization") String auth) {
        requireAdmin(auth);

        Map<String, String> categoryMap = new LinkedHashMap<>();

        // 핵심학사
        for (String name : List.of(
                "학칙.pdf", "학칙 (1).pdf", "학사규정.pdf", "교육과정위원회 규정.pdf",
                "국가장학금 운영위원회 규정.pdf", "국내외 전문교육기관 파견 현장실습교육 운영규정.pdf",
                "등록금 수납 및 반환에 관한 내규.pdf", "명예졸업에 관한 규정.pdf",
                "미래인재교육원 교육과정 운영내규.pdf", "비교과 교육과정 운영에 관한 규정.pdf",
                "외국자매대학및부속기관취득학점인정에관한규정.pdf", "원격수업 운영 규정.pdf",
                "입학사정관 운영위원회 규정.pdf", "입학사정관제 운영에 관한 규정.pdf",
                "장학규정.pdf", "전공상담센터 운영규정.pdf", "졸업인증제 운영 요강.pdf",
                "학점은행제 유사전공심의위원회 규정.pdf", "학점은행제 평가인정 학습과정에 관한 운영규정.pdf",
                "해외대학과의 교육과정 공동운영에 관한 규정.pdf",
                "현장실습 운영에 관한 규정.pdf", "현장실습 운영에 관한 내규.pdf")) {
            categoryMap.put(name, "핵심학사");
        }

        // 학생지원
        for (String name : List.of(
                "국민대학교 학생연구자 지원규정.pdf", "법률상담센터 운영규정.pdf",
                "복지시설 관리 운영 규정.pdf", "생활관규정.pdf",
                "학생생활상담센터 규정.pdf", "학생자치활동 지원에 관한 내규.pdf")) {
            categoryMap.put(name, "학생지원");
        }

        // 대학생활
        categoryMap.put("성곡도서관 운영규정.pdf", "대학생활");
        categoryMap.put("2025국민대학교요람.pdf", "대학생활");

        int totalUpdated = 0;
        Map<String, Integer> details = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : categoryMap.entrySet()) {
            int updated = chunkRepo.updateCategoryByDocName(entry.getKey(), entry.getValue());
            totalUpdated += updated;
            if (updated > 0) details.put(entry.getKey(), updated);
        }

        // 개별 청크 순회하며 매칭 (NFD/NFC 유니코드 호환)
        List<DocumentChunk> allChunks = chunkRepo.findAll();
        int chunkUpdated = 0;
        for (DocumentChunk chunk : allChunks) {
            if (chunk.getCategory() != null) continue;
            String docName = chunk.getDocName();
            if (docName == null) continue;

            String normalized = Normalizer.normalize(docName, Normalizer.Form.NFC);

            if (normalized.startsWith("[공지]")) {
                chunk.setCategory("공지사항");
                chunkRepo.save(chunk);
                chunkUpdated++;
            } else {
                String matched = categoryMap.get(normalized);
                if (matched != null) {
                    chunk.setCategory(matched);
                    chunkRepo.save(chunk);
                    chunkUpdated++;
                }
            }
        }
        totalUpdated += chunkUpdated;
        if (chunkUpdated > 0) details.put("개별매칭(NFD호환)", chunkUpdated);

        return ResponseEntity.ok(Map.of(
                "message", "카테고리 일괄 업데이트 완료",
                "total_updated", totalUpdated,
                "details", details
        ));
    }

}
