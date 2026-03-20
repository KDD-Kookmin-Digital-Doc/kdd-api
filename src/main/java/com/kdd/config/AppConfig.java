package com.kdd.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@Getter
public class AppConfig {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    @Value("${ALLOWED_DOMAIN:kookmin.ac.kr}")
    private String allowedDomain;

    @Value("${DOC_ADMIN_EMAILS:}")
    private String docAdminEmails;

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    @Value("${UPLOAD_DIR:uploads}")
    private String uploadDir;

    @Value("${CHUNK_SIZE:550}")
    private int chunkSize;

    @Value("${CHUNK_OVERLAP:100}")
    private int chunkOverlap;

    @Value("${CORS_ALLOWED_ORIGINS:*}")
    private String corsAllowedOrigins;

    public List<String> getDocAdminEmailList() {
        if (docAdminEmails == null || docAdminEmails.isBlank()) return List.of();
        return Arrays.stream(docAdminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
