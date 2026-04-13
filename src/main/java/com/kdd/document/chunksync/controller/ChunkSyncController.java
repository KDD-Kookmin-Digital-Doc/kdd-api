package com.kdd.document.chunksync.controller;

import com.kdd.document.chunksync.dto.ChunkFullListResponse;
import com.kdd.document.chunksync.dto.ChunkMetadataResponse;
import com.kdd.document.chunksync.dto.ChunkPagedResponse;
import com.kdd.document.chunksync.dto.ChunkResponse;
import com.kdd.document.chunksync.dto.ChunkStatsResponse;
import com.kdd.document.chunksync.service.ChunkSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "AI Chunk Sync", description = "AI 서버가 벡터DB 동기화를 위해 호출하는 청크 제공 API (인증 불필요)")
@RestController
@RequestMapping("/api/chunks")
@RequiredArgsConstructor
public class ChunkSyncController {

    private final ChunkSyncService chunkSyncService;

    @Operation(summary = "전체 청크 일괄 조회", description = "벡터DB 초기 구축용. 모든 활성 청크를 한 번에 반환한다.")
    @GetMapping("/all")
    public ResponseEntity<ChunkFullListResponse> getAllChunks() {
        return ResponseEntity.ok(chunkSyncService.getAllChunks());
    }

    @Operation(summary = "페이징 조회", description = "docName 파라미터로 특정 문서만 필터 가능.")
    @GetMapping
    public ResponseEntity<ChunkPagedResponse> getChunks(
            @RequestParam(required = false) String docName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(chunkSyncService.getChunksPaged(docName, page, size));
    }

    @Operation(summary = "메타데이터만 조회", description = "content를 제외한 메타데이터만 반환한다.")
    @GetMapping("/by-doc")
    public ResponseEntity<Map<String, List<ChunkMetadataResponse>>> getChunksByDoc(
            @RequestParam String docName) {
        return ResponseEntity.ok(Map.of("chunks", chunkSyncService.getChunksMetadataByDoc(docName)));
    }

    @Operation(summary = "통계 요약", description = "전체 문서/청크 개수 + 문서별 청크 수를 반환한다.")
    @GetMapping("/stats")
    public ResponseEntity<ChunkStatsResponse> getStats() {
        return ResponseEntity.ok(chunkSyncService.getStats());
    }

    @Operation(summary = "단건 조회", description = "청크 ID로 단일 청크를 조회한다.")
    @GetMapping("/{id}")
    public ResponseEntity<ChunkResponse> getChunkById(@PathVariable String id) {
        return ResponseEntity.ok(chunkSyncService.getChunkById(id));
    }
}
