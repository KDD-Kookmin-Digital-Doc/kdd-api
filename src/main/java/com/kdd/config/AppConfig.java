package com.kdd.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AppConfig {

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
}
