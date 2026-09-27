package com.community.medrecycle.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 简单内存令牌存储（演示用途；重启后需重新登录） */
@Component
public class TokenStore {

    private final Map<String, AuthUser> tokens = new ConcurrentHashMap<>();

    public String issue(AuthUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, user);
        return token;
    }

    public AuthUser get(String token) {
        return token == null ? null : tokens.get(token);
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }
}
