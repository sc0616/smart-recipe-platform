package com.example.delicious.common.config;

import com.example.delicious.common.exception.CustomException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            throw new CustomException("401", "请先登录");
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            request.setAttribute("userId", claims.get("id"));
            request.setAttribute("userName", claims.get("name"));
            request.setAttribute("userLevel", claims.get("level"));
        } catch (Exception e) {
            throw new CustomException("401", "token无效或已过期，请重新登录");
        }
        return true;
    }
}