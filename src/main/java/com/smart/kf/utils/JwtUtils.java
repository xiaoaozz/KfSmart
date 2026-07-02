package com.smart.kf.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.smart.kf.model.Permission;
import com.smart.kf.model.Role;
import com.smart.kf.model.User;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.TokenCacheService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.Set;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jwt.secret-key}")
    private String secretKeyBase64; // 支持 Base64 编码或纯文本（生产须为高熵随机串）

    private static final long EXPIRATION_TIME = Duration.ofHours(1).toMillis(); // 1 小时
    private static final long REFRESH_TOKEN_EXPIRATION_TIME = Duration.ofDays(7).toMillis(); // 7 天
    private static final long REFRESH_THRESHOLD = Duration.ofMinutes(5).toMillis(); // 剩余 <5 分钟开始刷新
    private static final long REFRESH_WINDOW = Duration.ofMinutes(10).toMillis(); // 过期后 10 分钟宽限期
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TokenCacheService tokenCacheService;

    /**
     * 解析密钥：优先按 Base64 解码，失败则按纯文本 UTF-8 字节使用。
     * 兼容历史 Base64 配置与纯文本开发默认值，生产应使用高熵随机串。
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        } catch (IllegalArgumentException notBase64) {
            keyBytes = secretKeyBase64.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 token 的脱敏指纹（哈希前 8 位），用于日志定位，不泄漏原始 token。
     */
    private static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "<empty>";
        }
        int hash = token.hashCode();
        return String.format("%08x", hash);
    }

    /**
     * 生成 JWT Token（集成Redis缓存）
     */
    public String generateToken(String username) {
        SecretKey key = getSigningKey(); // 解析密钥
        
        // 获取用户信息
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 生成唯一的tokenId
        String tokenId = generateTokenId();
        long expireTime = System.currentTimeMillis() + EXPIRATION_TIME;
        
        // 创建token内容
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenId", tokenId); // 添加tokenId用于Redis缓存
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId().toString()); // 添加用户ID到JWT
        
        // 添加组织标签信息
        if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
            claims.put("orgTags", user.getOrgTags());
        }
        
        // 添加主组织标签信息
        if (user.getPrimaryOrg() != null && !user.getPrimaryOrg().isEmpty()) {
            claims.put("primaryOrg", user.getPrimaryOrg());
        }
        
        // 添加用户权限编码列表（RBAC，用于前端菜单/按钮渲染）
        Set<String> permissions = buildUserPermissions(user);
        if (!permissions.isEmpty()) {
            claims.put("permissions", String.join(",", permissions));
        }

        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setExpiration(new Date(expireTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        
        // 缓存token信息到Redis
        tokenCacheService.cacheToken(tokenId, user.getId().toString(), username, expireTime);
        
        logger.info("Token generated and cached for user: {}, tokenId: {}", username, tokenId);
        return token;
    }

    /**
     * 验证 JWT Token 是否有效（优先使用Redis缓存）
     */
    public boolean validateToken(String token) {
        try {
            // 首先从JWT中提取tokenId（快速失败）
            String tokenId = extractTokenIdFromToken(token);
            if (tokenId == null) {
                logger.warn("Token does not contain tokenId");
                return false;
            }
            
            // 检查Redis缓存中的token状态
            if (!tokenCacheService.isTokenValid(tokenId)) {
                logger.debug("Token invalid in cache: {}", tokenId);
                return false;
            }
            
            // Redis验证通过，再验证JWT签名（双重验证）
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            logger.debug("Token validation successful: {}", tokenId);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("Token expired: {}", e.getClaims().get("tokenId", String.class));
        } catch (io.jsonwebtoken.security.SecurityException e) {
            logger.warn("Invalid token signature");
        } catch (Exception e) {
            logger.error("Error validating token", e);
        }
        return false;
    }

    /**
     * 从 JWT Token 中提取用户名
     */
    public String extractUsernameFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.getSubject() : null;
        } catch (Exception e) {
            logger.error("Error extracting username from token (fingerprint={}): {}", maskToken(token), e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取用户ID
     */
    public String extractUserIdFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("userId", String.class) : null;
        } catch (Exception e) {
            logger.error("Error extracting userId from token (fingerprint={}): {}", maskToken(token), e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取用户角色
     */
    public String extractRoleFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("role", String.class) : null;
        } catch (Exception e) {
            logger.error("Error extracting role from token (fingerprint={}): {}", maskToken(token), e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取组织标签
     */
    public String extractOrgTagsFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("orgTags", String.class) : null;
        } catch (Exception e) {
            logger.error("Error extracting organization tags from token (fingerprint={}): {}", maskToken(token), e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取主组织标签
     */
    public String extractPrimaryOrgFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("primaryOrg", String.class) : null;
        } catch (Exception e) {
            logger.error("Error extracting primary organization from token (fingerprint={}): {}", maskToken(token), e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查token是否应该刷新（剩余时间少于阈值）
     */
    public boolean shouldRefreshToken(String token) {
        try {
            Claims claims = extractClaims(token);
            if (claims == null) return false;
            
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();
            long remainingTime = expirationTime - currentTime;
            
            return remainingTime > 0 && remainingTime < REFRESH_THRESHOLD;
        } catch (Exception e) {
            logger.debug("Cannot check if token should refresh: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查过期token是否仍可刷新（在宽限期内）
     */
    public boolean canRefreshExpiredToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            if (claims == null) return false;
            
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();
            long expiredTime = currentTime - expirationTime;
            
            return expiredTime > 0 && expiredTime < REFRESH_WINDOW;
        } catch (Exception e) {
            logger.debug("Cannot check if expired token can refresh: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 刷新token（生成新的token）
     */
    public String refreshToken(String oldToken) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(oldToken);
            if (claims == null) return null;
            
            String username = claims.getSubject();
            if (username == null || username.isEmpty()) return null;
            
            // 重新生成token
            String newToken = generateToken(username);
            logger.info("Token refreshed successfully for user: {}", username);
            return newToken;
        } catch (Exception e) {
            logger.error("Error refreshing token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 提取Claims，忽略过期异常
     */
    private Claims extractClaimsIgnoreExpiration(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // 忽略过期异常，返回claims
            return e.getClaims();
        } catch (Exception e) {
            logger.debug("Cannot extract claims from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 提取Claims（正常验证）
     */
    private Claims extractClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 生成 Refresh Token（长期有效的刷新令牌，集成Redis缓存）
     */
    public String generateRefreshToken(String username) {
        SecretKey key = getSigningKey();
        
        // 获取用户信息
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 生成唯一的refreshTokenId
        String refreshTokenId = generateTokenId();
        long expireTime = System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_TIME;
        
        // 创建refreshToken内容（相对简单，只包含基本信息）
        Map<String, Object> claims = new HashMap<>();
        claims.put("refreshTokenId", refreshTokenId); // 添加refreshTokenId
        claims.put("userId", user.getId().toString());
        claims.put("type", "refresh"); // 标识这是一个refresh token

        String refreshToken = Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setExpiration(new Date(expireTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        
        // 缓存refresh token信息到Redis
        tokenCacheService.cacheRefreshToken(refreshTokenId, user.getId().toString(), null, expireTime);
        
        logger.info("Refresh token generated and cached for user: {}, refreshTokenId: {}", username, refreshTokenId);
        return refreshToken;
    }
    
    /**
     * 验证 Refresh Token 是否有效（优先使用Redis缓存）
     */
    public boolean validateRefreshToken(String refreshToken) {
        try {
            // 首先从JWT中提取refreshTokenId
            String refreshTokenId = extractRefreshTokenIdFromToken(refreshToken);
            if (refreshTokenId == null) {
                logger.warn("Refresh token does not contain refreshTokenId");
                return false;
            }
            
            // 检查Redis缓存中的refresh token状态
            if (!tokenCacheService.isRefreshTokenValid(refreshTokenId)) {
                logger.debug("Refresh token invalid in cache: {}", refreshTokenId);
                return false;
            }
            
            // Redis验证通过，再验证JWT签名
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(refreshToken)
                    .getBody();
            
            // 验证是否为refresh token类型
            String tokenType = claims.get("type", String.class);
            if (!"refresh".equals(tokenType)) {
                logger.warn("Token is not a refresh token");
                return false;
            }

            logger.debug("Refresh token validation successful: {}", refreshTokenId);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("Refresh token expired: {}", e.getClaims().get("refreshTokenId", String.class));
        } catch (io.jsonwebtoken.security.SecurityException e) {
            logger.warn("Invalid refresh token signature");
        } catch (Exception e) {
            logger.error("Error validating refresh token", e);
        }
        return false;
    }
    
    /**
     * 从 JWT Token 中提取refreshTokenId
     */
    public String extractRefreshTokenIdFromToken(String refreshToken) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(refreshToken);
            return claims != null ? claims.get("refreshTokenId", String.class) : null;
        } catch (Exception e) {
            logger.debug("Error extracting refreshTokenId from token", e);
            return null;
        }
    }
    
    /**
     * 生成唯一的tokenId
     */
    private String generateTokenId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 从 JWT Token 中提取tokenId
     */
    public String extractTokenIdFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            return claims != null ? claims.get("tokenId", String.class) : null;
        } catch (Exception e) {
            logger.debug("Error extracting tokenId from token", e);
            return null;
        }
    }
    
    /**
     * 使token失效（加入Redis黑名单）
     */
    public void invalidateToken(String token) {
        try {
            String tokenId = extractTokenIdFromToken(token);
            if (tokenId != null) {
                Claims claims = extractClaimsIgnoreExpiration(token);
                if (claims != null) {
                    long expireTime = claims.getExpiration().getTime();
                    String userId = claims.get("userId", String.class);
                    
                    // 加入黑名单
                    tokenCacheService.blacklistToken(tokenId, expireTime);
                    // 从缓存中移除
                    tokenCacheService.removeToken(tokenId, userId);
                    
                    logger.info("Token invalidated: {}", tokenId);
                }
            }
        } catch (Exception e) {
            logger.error("Error invalidating token", e);
        }
    }
    
    /**
     * 使用户所有token失效（批量登出）
     */
    public void invalidateAllUserTokens(String userId) {
        try {
            tokenCacheService.removeAllUserTokens(userId);
            logger.info("All tokens invalidated for user: {}", userId);
        } catch (Exception e) {
            logger.error("Error invalidating all user tokens: {}", userId, e);
        }
    }

    /**
     * 从 JWT Token 中提取权限编码列表
     */
    @SuppressWarnings("unused")
    public Set<String> extractPermissionsFromToken(String token) {
        try {
            Claims claims = extractClaimsIgnoreExpiration(token);
            if (claims == null) return new java.util.HashSet<>();
            String permsStr = claims.get("permissions", String.class);
            if (permsStr == null || permsStr.isEmpty()) return new java.util.HashSet<>();
            return new java.util.HashSet<>(Arrays.asList(permsStr.split(",")));
        } catch (Exception e) {
            logger.error("Error extracting permissions from token", e);
            return new java.util.HashSet<>();
        }
    }

    /**
     * 构建用户权限编码集合（用于写入 JWT）
     */
    private Set<String> buildUserPermissions(User user) {
        Set<String> perms = new java.util.HashSet<>();
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            for (Role role : user.getRoles()) {
                for (Permission perm : role.getPermissions()) {
                    perms.add(perm.getPermCode());
                }
            }
        } else {
            // 兼容旧 legacyRole
            @SuppressWarnings("deprecation")
            User.Role legacy = user.getLegacyRole();
            if (legacy == User.Role.ADMIN) {
                perms.addAll(java.util.Arrays.asList(
                    "kb:read", "kb:write", "kb:delete", "kb:admin",
                    "doc:read", "doc:write", "doc:delete",
                    "agent:read", "agent:write", "agent:run",
                    "user:read", "user:write",
                    "system:admin", "chat:use"
                ));
            } else {
                perms.addAll(java.util.Arrays.asList(
                    "kb:read", "kb:write",
                    "doc:read", "doc:write", "doc:delete",
                    "agent:read", "agent:write", "agent:run",
                    "chat:use"
                ));
            }
        }
        return perms;
    }
}
