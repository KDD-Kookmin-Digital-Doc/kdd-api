package com.kdd.ai.client;

import com.kdd.ai.dto.AiDeleteResponse;
import com.kdd.ai.dto.AiEmbedRequest;
import com.kdd.ai.dto.AiEmbedResponse;
import com.kdd.ai.dto.AiFaqAnalyzeRequest;
import com.kdd.ai.dto.AiFaqAnalyzeResponse;
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
     * AI 서버에 인기 질문 클러스터링 + 답변 초안 생성 요청.
     * <p>
     * BE 스케줄러가 ChatMessage 테이블의 최근 사용자 질문(role='user')을 추출하여 questions 배열로 전달하고,
     * AI는 클러스터링하여 인기 질문 top_k개와 답변 초안을 반환한다. 결과는 FAQ 후보로 영속화되며
     * 채팅 전 추천 질문 노출에도 동일 데이터가 사용된다 (BE-AI 명세 §2 - 인기 질문 TOP 5 & FAQ 후보 생성).
     * <p>
     * 데이터 부족(INSUFFICIENT_DATA)은 status="error"로 반환되며 호출부에서 정상 종료(다음 주기 재시도)로
     * 처리해야 한다. 본 메서드는 HTTP 레벨 오류만 {@link AiServerException}으로 래핑한다.
     */
    public AiFaqAnalyzeResponse analyzeFaq(AiFaqAnalyzeRequest request) {
        log.info("[AI] analyzeFaq call: questions={}, top_k={}, min_cluster_size={}",
                request.questions().size(), request.topK(), request.minClusterSize());
        try {
            AiFaqAnalyzeResponse response = aiServerRestClient.post()
                    .uri("/api/faq/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiFaqAnalyzeResponse.class);

            if (response == null) {
                throw new AiServerException("AI analyzeFaq returned null response");
            }
            int candidateCount = response.candidates() == null ? 0 : response.candidates().size();
            log.info("[AI] analyzeFaq result: status={}, candidates={}", response.status(), candidateCount);
            return response;
        } catch (AiServerException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("[AI] analyzeFaq HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AiServerException("AI analyzeFaq HTTP error: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            log.error("[AI] analyzeFaq network error: {}", e.getMessage());
            throw new AiServerException("AI analyzeFaq network error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[AI] analyzeFaq unexpected error", e);
            throw new AiServerException("AI analyzeFaq unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * AI 서버에 문서 청크 일괄 삭제 요청.
     * 존재하지 않는 doc_id도 성공 반환한다고 명세서에 명시됨 (멱등성).
     */
    public AiDeleteResponse deleteDocument(Long docId) {
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
