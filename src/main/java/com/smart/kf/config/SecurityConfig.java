package com.smart.kf.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 * 权限设计分三层：
 *   1. URL路由级：公开白名单 / 需认证 / 管理员专属（通过 authorizeHttpRequests 配置）
 *   2. 方法级：通过 @PreAuthorize("hasAuthority('permCode')") 注解控制（已开启 @EnableMethodSecurity）
 *   3. 数据行级：通过 RbacService.hasResourcePermission() 在 Service 层做数据过滤
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 开启 @PreAuthorize / @PostAuthorize 注解支持（prePostEnabled 默认为 true）
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private OrgTagAuthorizationFilter orgTagAuthorizationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        try {
            http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                    // ===== 公开路径（无需认证）=====
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/", "/test.html", "/static/test.html", "/static/**",
                                     "/avatars/**", "/*.js", "/*.css", "/*.ico").permitAll()
                    .requestMatchers("/chat/**", "/ws/**").permitAll()
                    .requestMatchers("/api/v1/users/register", "/api/v1/users/login").permitAll()
                    .requestMatchers("/api/v1/test/**").permitAll()
                    .requestMatchers("/api/v1/chat/websocket-token").permitAll()

                    // ===== 管理员专属路径（URL级粗粒度保护，细粒度由 @PreAuthorize 控制）=====
                    .requestMatchers("/api/v1/admin/**").hasAuthority("system:admin")

                    // ===== 其他路径：只需登录认证，细粒度权限由方法级 @PreAuthorize 控制 =====
                    .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(orgTagAuthorizationFilter, JwtAuthenticationFilter.class);

            logger.info("Security configuration loaded successfully (RBAC mode).");
            return http.build();
        } catch (Exception e) {
            logger.error("Failed to configure security filter chain", e);
            throw e;
        }
    }
}
