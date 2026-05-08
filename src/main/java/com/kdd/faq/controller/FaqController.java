package com.kdd.faq.controller;

import com.kdd.faq.dto.FaqResponse;
import com.kdd.faq.dto.FaqTopicResponse;
import com.kdd.faq.entity.FaqTopic;
import com.kdd.faq.service.FaqService;
import com.kdd.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "FAQ", description = "FAQ 조회 API")
@RestController
@RequestMapping("/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

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
}
