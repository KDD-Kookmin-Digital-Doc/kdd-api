package com.kdd.document.chunksync.service;

import com.kdd.document.chunksync.dto.ChunkFullListResponse;
import com.kdd.document.chunksync.dto.ChunkMetadataResponse;
import com.kdd.document.chunksync.dto.ChunkPagedResponse;
import com.kdd.document.chunksync.dto.ChunkResponse;
import com.kdd.document.chunksync.dto.ChunkStatsResponse;
import com.kdd.document.chunksync.dto.DocumentStatResponse;
import com.kdd.document.entity.DocumentChunk;
import com.kdd.document.repository.DocumentChunkRepository;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChunkSyncService {

    private final DocumentChunkRepository chunkRepository;

    public ChunkFullListResponse getAllChunks() {
        List<DocumentChunk> chunks = chunkRepository.findAllActiveWithDocument();
        return new ChunkFullListResponse(
                chunks.stream().map(ChunkResponse::from).toList(),
                chunks.size()
        );
    }

    public ChunkPagedResponse getChunksPaged(String docName, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DocumentChunk> result;
        if (docName != null && !docName.isBlank()) {
            result = chunkRepository.findByDocNamePaged(docName, pageable);
        } else {
            result = chunkRepository.findAllActivePaged(pageable);
        }
        return new ChunkPagedResponse(
                result.getContent().stream().map(ChunkResponse::from).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize(),
                result.getTotalPages()
        );
    }

    public ChunkResponse getChunkById(String id) {
        Long longId;
        try {
            longId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        DocumentChunk chunk = chunkRepository.findByIdActive(longId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        return ChunkResponse.from(chunk);
    }

    public List<ChunkMetadataResponse> getChunksMetadataByDoc(String docName) {
        return chunkRepository.findByDocName(docName).stream()
                .map(ChunkMetadataResponse::from)
                .toList();
    }

    public ChunkStatsResponse getStats() {
        long totalChunks = chunkRepository.countActive();
        long totalDocs = chunkRepository.countDistinctDocNames();
        List<DocumentStatResponse> documents = chunkRepository.countGroupByDocName().stream()
                .map(row -> new DocumentStatResponse((String) row[0], (Long) row[1]))
                .toList();
        return new ChunkStatsResponse(totalDocs, totalChunks, documents);
    }
}
