package com.kdd.controller;

import com.kdd.config.AppConfig;
import com.kdd.dto.AuthRequest;
import com.kdd.dto.AuthResponse;
import com.kdd.entity.UserProfile;
import com.kdd.repository.UserProfileRepository;
import com.kdd.service.GoogleAuthService;
import com.kdd.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final GoogleAuthService googleAuthService;
    private final JwtService jwtService;
    private final UserProfileRepository userProfileRepository;
    private final AppConfig appConfig;

    @PostMapping("/auth/google")
    public ResponseEntity<?> googleLogin(@RequestBody AuthRequest req) {
        try {
            Map<String, String> userInfo = googleAuthService.verifyToken(req.getCredential());
            String email = userInfo.get("email");
            String name = userInfo.get("name");

            // 첫 로그인 시 프로필 자동 생성
            if (!userProfileRepository.existsById(email)) {
                UserProfile profile = UserProfile.builder()
                        .email(email)
                        .name(name)
                        .role("user")
                        .build();
                userProfileRepository.save(profile);
            }

            String token = jwtService.createToken(email, name);

            // 관리자 여부 확인
            boolean isAdmin = appConfig.getDocAdminEmails().contains(email);

            Map<String, Object> user = new HashMap<>();
            user.put("email", email);
            user.put("name", name);
            user.put("picture", userInfo.get("picture"));
            user.put("isAdmin", isAdmin);

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .user(user)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        String email = jwtService.extractEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "인증이 필요합니다"));
        }
        return userProfileRepository.findById(email)
                .map(profile -> {
                    Map<String, Object> user = new HashMap<>();
                    user.put("email", profile.getEmail());
                    user.put("name", profile.getName());
                    user.put("role", profile.getRole());
                    user.put("isAdmin", appConfig.getDocAdminEmails().contains(email));
                    return ResponseEntity.ok(user);
                })
                .orElse(ResponseEntity.status(404).body(null));
    }

    @GetMapping("/auth/dev-token")
    public ResponseEntity<?> devToken(@RequestParam String email,
                                      @RequestParam(defaultValue = "개발자") String name) {
        String token = jwtService.createToken(email, name);
        return ResponseEntity.ok(Map.of("token", token));
    }
}
