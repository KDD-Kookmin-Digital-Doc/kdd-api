package com.kdd.document.chunksync.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Health", description = "AI 서버 동기화용 헬스체크 (인증 불필요)")
@RestController
public class HealthController {

    @Operation(summary = "헬스체크", description = "서버 상태 확인용. 항상 {\"status\":\"ok\"} 반환.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
