package com.kdd.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.kdd.auth.dto.GoogleUserInfo;
import com.kdd.global.error.BusinessException;
import com.kdd.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
public class GoogleOAuthService {

    @Value("${app.google.client-id}")
    private String clientId;

    @Value("${app.google.client-secret}")
    private String clientSecret;

    @Value("${app.google.redirect-uri}")
    private String redirectUri;

    private GoogleIdTokenVerifier verifier;
    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
        this.webClient = WebClient.builder()
                .baseUrl("https://oauth2.googleapis.com")
                .build();
    }

    public GoogleUserInfo verifyAndExtract(String code) {
        try {
            String idTokenString = exchangeCodeForIdToken(code);
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new BusinessException(ErrorCode.UNVERIFIED_EMAIL);
            }

            return new GoogleUserInfo(payload.getEmail(), (String) payload.get("name"));
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.warn("Google OAuth code exchange failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        } catch (Exception e) {
            log.error("Google OAuth verification failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String exchangeCodeForIdToken(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        Map<String, Object> response = webClient.post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .block();

        if (response == null || !response.containsKey("id_token")) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }

        return (String) response.get("id_token");
    }
}
