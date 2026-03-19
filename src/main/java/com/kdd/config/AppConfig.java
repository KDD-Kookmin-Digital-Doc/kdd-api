package com.kdd.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@Getter
public class AppConfig {

    @Value("${app.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${app.google-client-id}")
    private String googleClientId;

    @Value("${app.allowed-domain}")
    private String allowedDomain;

    @Value("${app.doc-admin-emails:}")
    private String docAdminEmails;

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Value("${app.chunk-size}")
    private int chunkSize;

    @Value("${app.chunk-overlap}")
    private int chunkOverlap;

    @Value("${app.cors-allowed-origins:*}")
    private String corsAllowedOrigins;

    public List<String> getDocAdminEmailList() {
        if (docAdminEmails == null || docAdminEmails.isBlank()) return List.of();
        return Arrays.stream(docAdminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
