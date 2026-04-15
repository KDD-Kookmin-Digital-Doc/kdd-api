package com.kdd.ai.client;

import com.kdd.ai.dto.AiDeleteResponse;
import com.kdd.ai.dto.AiEmbedRequest;
import com.kdd.ai.dto.AiEmbedResponse;
import com.kdd.ai.exception.AiServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestClient aiServerRestClient;

    /**
     * AI 서버에 청크 임베딩 요청.
     * 성공/부분실패 구분 없이 응답을 그대로 반환하며,
     * 네트워크/HTTP 오류는 {@link AiServerException}으로 래핑된다.
     */
    public AiEmbedResponse embed(AiEmbedRequest request) {
        log.info("[AI] embed call: doc_id={}, chunks={}", request.docId(), request.chunks().size());
        try {
            AiEmbedResponse response = aiServerRestClient.post()
                    .uri("/api/documents/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiEmbedResponse.class);

            if (response == null) {
                throw new AiServerException("AI embed returned null response");
            }
            log.info("[AI] embed result: status={}, embedded={}", response.status(), response.embeddedChunkCount());
            return response;
        } catch (AiServerException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("[AI] embed HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AiServerException("AI embed HTTP error: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            log.error("[AI] embed network error: {}", e.getMessage());
            throw new AiServerException("AI embed network error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[AI] embed unexpected error", e);
            throw new AiServerException("AI embed unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * AI 서버에 문서 청크 일괄 삭제 요청.
     * 존재하지 않는 doc_id도 성공 반환한다고 명세서에 명시됨 (멱등성).
     */
    public AiDeleteResponse deleteDocument(String docId) {
        log.info("[AI] delete call: doc_id={}", docId);
        try {
            AiDeleteResponse response = aiServerRestClient.delete()
                    .uri("/api/documents/{docId}", docId)
                    .retrieve()
                    .body(AiDeleteResponse.class);

            if (response == null) {
                throw new AiServerException("AI delete returned null response");
            }
            log.info("[AI] delete result: status={}, deleted_chunks={}",
                    response.status(), response.deletedChunkCount());
            return response;
        } catch (AiServerException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("[AI] delete HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AiServerException("AI delete HTTP error: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            log.error("[AI] delete network error: {}", e.getMessage());
            throw new AiServerException("AI delete network error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[AI] delete unexpected error", e);
            throw new AiServerException("AI delete unexpected error: " + e.getMessage(), e);
        }
    }
}
