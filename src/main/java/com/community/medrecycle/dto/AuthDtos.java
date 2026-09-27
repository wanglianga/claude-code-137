package com.community.medrecycle.dto;

import com.community.medrecycle.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 认证相关 DTO */
public class AuthDtos {

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 32, message = "用户名长度3-32") String username,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 64, message = "密码长度6-64") String password,
            @NotBlank(message = "姓名不能为空") String name,
            String phone) {
    }

    public record UserView(Long id, String username, String name, String phone,
                           User.Role role, Long communityPointId) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getName(), u.getPhone(),
                    u.getRole(), u.getCommunityPointId());
        }
    }

    public record LoginResponse(String token, UserView user) {
    }
}
