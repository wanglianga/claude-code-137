package com.community.medrecycle.auth;

import com.community.medrecycle.common.ApiResponse;
import com.community.medrecycle.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_AUTH_USER = "AUTH_USER";

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RequireRole require = hm.getMethodAnnotation(RequireRole.class);
        if (require == null) {
            require = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        if (require == null) {
            return true;
        }
        AuthUser user = tokenStore.get(resolveToken(request));
        if (user == null) {
            write(response, 401, ApiResponse.error(40100, "未登录或登录已失效"));
            return false;
        }
        User.Role[] allowed = require.value();
        if (allowed.length > 0 && Arrays.stream(allowed).noneMatch(r -> r == user.role())) {
            write(response, 403, ApiResponse.error(40300, "无权限访问该资源"));
            return false;
        }
        request.setAttribute(ATTR_AUTH_USER, user);
        return true;
    }

    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return request.getParameter("token");
    }

    private void write(HttpServletResponse response, int status, ApiResponse<Void> body) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
