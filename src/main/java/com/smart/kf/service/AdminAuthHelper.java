package com.smart.kf.service;

import com.smart.kf.exception.CustomException;
import com.smart.kf.model.User;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Admin 子控制器共享的鉴权辅助方法，从原 AdminController 抽出，避免各子控制器重复实现。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthHelper {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    public void validateAdmin(String username) {
        if (username == null || username.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }

        User admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Unauthorized access: Admin role required", HttpStatus.FORBIDDEN);
        }
    }

    public String resolveAdminUsername(String bearerToken) {
        String username = jwtUtils.extractUsernameFromToken(bearerToken.replace("Bearer ", ""));
        validateAdmin(username);
        return username;
    }

    /**
     * 从 JWT token 解析管理员用户 ID，解析失败返回 {@code null}（不影响主流程）。
     */
    public Long extractAdminUserId(String bearerToken) {
        try {
            String uid = jwtUtils.extractUserIdFromToken(bearerToken.replace("Bearer ", ""));
            return uid != null && !uid.isBlank() ? Long.valueOf(uid) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
