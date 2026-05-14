package com.kdd.faq.controller;

import com.kdd.faq.dto.FaqChatStartResponse;
import com.kdd.faq.dto.FaqResponse;
import com.kdd.faq.dto.FaqTopicResponse;
import com.kdd.faq.entity.FaqTopic;
import com.kdd.faq.service.FaqChatService;
import com.kdd.faq.service.FaqService;
import com.kdd.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "FAQ", description = "FAQ 조회 API")
@RestController
@RequestMapping("/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;
    private final FaqChatService faqChatService;

    @Operation(summary = "FAQ 목록 조회", description = "카테고리(topic) 필터를 지원하며 생성일 최신순으로 페이지네이션된 FAQ 목록을 반환한다.")
    @GetMapping
    public ResponseEntity<PageResponse<FaqResponse>> getFaqs(
            @RequestParam(required = false) FaqTopic topic,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(faqService.getFaqs(topic, page, pageSize));
    }

    @Operation(summary = "FAQ 토픽 조회", description = "사전에 정의된 FAQ 카테고리 목록(topic, label)을 조회한다.")
    @GetMapping("/topics")
    public ResponseEntity<Map<String, List<FaqTopicResponse>>> getTopics() {
        return ResponseEntity.ok(Map.of("topics", faqService.getTopics()));
    }

    @Operation(summary = "FAQ 상세 조회", description = "특정 FAQ의 상세 정보를 조회한다.")
    @GetMapping("/{faqId}")
    public ResponseEntity<FaqResponse> getFaq(@PathVariable Long faqId) {
        return ResponseEntity.ok(faqService.getFaq(faqId));
    }

    @Operation(summary = "FAQ 기반 채팅 시작",
            description = "선택한 FAQ의 질문/답변을 초기 user/assistant 메시지로 가진 새 채팅 세션을 생성한다. " +
                    "source_type='faq' 세션의 초기 메시지는 사용량 제한 카운터에 포함되지 않는다.")
    @PostMapping("/{faqId}/chat")
    public ResponseEntity<FaqChatStartResponse> startChat(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long faqId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(faqChatService.startChat(faqId, userId));
    }
}
