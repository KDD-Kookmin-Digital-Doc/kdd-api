package com.kdd.controller;

import com.kdd.entity.DocumentChunk;
import com.kdd.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chunks")
@RequiredArgsConstructor
public class ChunkController {

    private final DocumentChunkRepository chunkRepo;

    private Map<String, Object> toFullMap(DocumentChunk c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("content", c.getContent());
        m.put("doc_name", c.getDocName());
        m.put("section_path", c.getSectionPath());
        m.put("page", c.getPage());
        m.put("has_table", c.isHasTable());
        m.put("source_url", c.getSourceUrl());
        m.put("category", c.getCategory());
        m.put("created_at", c.getCreatedAt());
        return m;
    }

    private Map<String, Object> toMetaMap(DocumentChunk c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("doc_name", c.getDocName());
        m.put("section_path", c.getSectionPath());
        m.put("page", c.getPage());
        m.put("has_table", c.isHasTable());
        m.put("source_url", c.getSourceUrl());
        m.put("category", c.getCategory());
        m.put("created_at", c.getCreatedAt());
        return m;
    }

    /**
     * 전체 청크 조회 (페이징)
     * GET /api/chunks?page=0&size=100
     * GET /api/chunks?docName=학칙.pdf
     * GET /api/chunks?docName=[공지]  → 공지사항만
     */
    @GetMapping
    public ResponseEntity<?> getChunks(
            @RequestParam(required = false) String docName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        List<DocumentChunk> chunks;
        if (docName != null && !docName.isBlank()) {
            chunks = chunkRepo.findByDocName(docName);
        } else {
            chunks = chunkRepo.findAll();
        }

        int total = chunks.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<Map<String, Object>> result = chunks.subList(from, to).stream()
                .map(this::toFullMap).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "chunks", result,
                "total", total,
                "page", page,
                "size", size,
                "total_pages", (int) Math.ceil((double) total / size)
        ));
    }

    /**
     * 특정 청크 1개 조회
     * GET /api/chunks/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getChunk(@PathVariable String id) {
        return chunkRepo.findById(id)
                .map(c -> ResponseEntity.ok((Object) toFullMap(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 문서별 청크 목록 (content 없이 메타데이터만)
     * GET /api/chunks/by-doc?docName=학칙.pdf
     */
    @GetMapping("/by-doc")
    public ResponseEntity<?> getChunksByDoc(@RequestParam String docName) {
        List<DocumentChunk> chunks = chunkRepo.findByDocName(docName);
        List<Map<String, Object>> result = chunks.stream()
                .map(this::toMetaMap).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("chunks", result, "total", result.size()));
    }

    /**
     * 통계 요약
     * GET /api/chunks/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long totalChunks = chunkRepo.count();
        long totalDocs = chunkRepo.countDistinctDocNames();
        List<Object[]> grouped = chunkRepo.countByDocNameGrouped();

        List<Map<String, Object>> docs = grouped.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("doc_name", row[0]);
            m.put("chunk_count", row[1]);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "total_chunks", totalChunks,
                "total_docs", totalDocs,
                "documents", docs
        ));
    }

    /**
     * 전체 청크 일괄 조회 (페이징 없이, AI팀 벡터DB 구축용)
     * GET /api/chunks/all
     * 주의: 데이터가 많으면 응답이 클 수 있음
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllChunks() {
        List<DocumentChunk> chunks = chunkRepo.findAll();
        List<Map<String, Object>> result = chunks.stream()
                .map(this::toFullMap).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("chunks", result, "total", result.size()));
    }
}
