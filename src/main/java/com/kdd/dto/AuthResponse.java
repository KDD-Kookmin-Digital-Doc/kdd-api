package com.kdd.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

@Getter
@Builder
@ToString(exclude = "token")
public class AuthResponse {
    private String token;
    private Map<String, Object> user;
}
