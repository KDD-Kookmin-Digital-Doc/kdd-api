package com.kdd.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.kdd.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final AppConfig appConfig;

    public Map<String, String> verifyToken(String credential) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(appConfig.getGoogleClientId()))
                    .build();

            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new RuntimeException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();

            // 허용 도메인 검증
            String domain = email.substring(email.indexOf("@") + 1);
            if (!domain.equals(appConfig.getAllowedDomain())) {
                throw new RuntimeException("허용되지 않은 이메일 도메인입니다: " + domain);
            }

            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("email", email);
            userInfo.put("name", (String) payload.get("name"));
            userInfo.put("picture", (String) payload.get("picture"));
            return userInfo;
        } catch (Exception e) {
            throw new RuntimeException("Google token verification failed: " + e.getMessage(), e);
        }
    }
}
