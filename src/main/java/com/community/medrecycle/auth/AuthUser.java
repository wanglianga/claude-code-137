package com.community.medrecycle.auth;

import com.community.medrecycle.domain.User;

/** 登录用户上下文 */
public record AuthUser(Long userId, String username, String name, User.Role role, Long communityPointId) {
}
