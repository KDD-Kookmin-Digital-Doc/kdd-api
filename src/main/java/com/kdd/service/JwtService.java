package com.kdd.service;

import com.kdd.config.AppConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppConfig appConfig;
    private SecretKey cachedKey;

    @PostConstruct
    private void init() {
        byte[] keyBytes = HexFormat.of().parseHex(appConfig.getJwtSecret());
        this.cachedKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getKey() {
        return cachedKey;
    }

    public String createToken(String email, String name) {
        return Jwts.builder()
                .claim("email", email)
                .claim("name", name)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 7 * 24 * 3600 * 1000L))
                .signWith(getKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try {
            Claims claims = parseToken(auth.substring(7));
            return claims.get("email", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
