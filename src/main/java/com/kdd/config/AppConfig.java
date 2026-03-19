package com.kdd.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AppConfig {

    @Value("${app.google-client-id}")
    private String googleClientId;

    @Value("${app.allowed-domain}")
    private String allowedDomain;

    @Value("${app.doc-admin-emails}")
    private String docAdminEmails;

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Value("${app.chunk-size}")
    private int chunkSize;

    @Value("${app.chunk-overlap}")
    private int chunkOverlap;
}
