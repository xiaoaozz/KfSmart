package com.smart.kf.service;

import com.smart.kf.model.Permission;
import com.smart.kf.model.Role;
import com.smart.kf.model.User;
import com.smart.kf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 实现 Spring Security 的 UserDetailsService 接口，用于加载用户的详细信息（包括用户名、密码和权限）。
 * 重构后支持完整的 RBAC 权限体系：
 *   - ROLE_xxx：角色权限（用于 hasRole 判断）
 *   - 具体权限编码（如 kb:write）：用于 hasAuthority / @PreAuthorize 判断
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * 根据用户名加载用户详细信息，包含角色和细粒度权限。
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                buildAuthorities(user)
        );
    }

    /**
     * 构建用户的全部权限列表：
     *   1. 角色权限：ROLE_ADMIN、ROLE_USER 等（Spring Security 约定以 ROLE_ 开头）
     *   2. 细粒度权限编码：kb:write、doc:delete、system:admin 等
     * 兼容逻辑：
     *   - 若用户的 roles 集合不为空，则从 roles 提取权限
     *   - 若 roles 为空（过渡期旧数据），则根据 legacyRole 枚举推断基础权限集
     */
    private Collection<? extends GrantedAuthority> buildAuthorities(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            // RBAC 模式：从角色集合加载
            for (Role role : user.getRoles()) {
                // 添加角色权限（ROLE_xxx 前缀）
                authorities.add(new SimpleGrantedAuthority(role.getRoleCode()));

                // 添加角色包含的细粒度权限
                for (Permission perm : role.getPermissions()) {
                    authorities.add(new SimpleGrantedAuthority(perm.getPermCode()));
                }
            }
        } else {
            // 兼容旧模式：根据 legacyRole 枚举生成基础权限
            @SuppressWarnings("deprecation")
            User.Role legacyRole = user.getLegacyRole();
            if (legacyRole == null) {
                legacyRole = User.Role.USER;
            }
            if (legacyRole == User.Role.ADMIN) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                addAdminPermissions(authorities);
            } else {
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                addUserPermissions(authorities);
            }
        }

        return authorities;
    }

    private void addAdminPermissions(Set<GrantedAuthority> authorities) {
        String[] adminPerms = {
            "kb:read", "kb:write", "kb:delete", "kb:admin",
            "doc:read", "doc:write", "doc:delete",
            "agent:read", "agent:write", "agent:run",
            "user:read", "user:write",
            "system:admin", "chat:use"
        };
        for (String perm : adminPerms) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }
    }

    private void addUserPermissions(Set<GrantedAuthority> authorities) {
        String[] userPerms = {
            "kb:read", "kb:write",
            "doc:read", "doc:write", "doc:delete",
            "agent:read", "agent:write", "agent:run",
            "chat:use"
        };
        for (String perm : userPerms) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }
    }
}
