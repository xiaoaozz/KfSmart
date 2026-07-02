package com.smart.kf.controller.admin;

import com.smart.kf.exception.CustomException;
import com.smart.kf.model.Permission;
import com.smart.kf.model.Role;
import com.smart.kf.model.User;
import com.smart.kf.repository.PermissionRepository;
import com.smart.kf.repository.RoleRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.AdminAuthHelper;
import com.smart.kf.service.RbacService;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员角色/权限管理控制器（从 AdminController 拆分而来）。
 * 全部接口需要 system:admin 权限（在 SecurityConfig 路由级保护，此处方法级双重保障）
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('system:admin')")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RbacService rbacService;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final AdminAuthHelper adminAuthHelper;

    /**
     * 获取所有角色列表
     */
    @GetMapping("/roles")
    public ResponseEntity<?> getAllRoles(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            List<Map<String, Object>> roles = roleRepository.findAll().stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("roleCode", r.getRoleCode());
                    m.put("roleName", r.getRoleName());
                    m.put("description", r.getDescription());
                    m.put("isSystem", r.isSystem());
                    m.put("permissions", r.getPermissions().stream()
                        .map(p -> Map.of("permCode", p.getPermCode(), "permName", p.getPermName()))
                        .toList());
                    return m;
                }).toList();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取角色列表成功", "data", roles));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_ROLES", adminUsername, "获取角色列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取角色列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有权限列表（用于角色编辑时选择）
     */
    @GetMapping("/permissions")
    public ResponseEntity<?> getAllPermissions(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            List<Map<String, Object>> perms = permissionRepository.findAll().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("permCode", p.getPermCode());
                    m.put("permName", p.getPermName());
                    m.put("resourceType", p.getResourceType());
                    m.put("action", p.getAction());
                    m.put("description", p.getDescription());
                    return m;
                }).toList();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取权限列表成功", "data", perms));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_PERMISSIONS", adminUsername, "获取权限列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取权限列表失败: " + e.getMessage()));
        }
    }

    /**
     * 创建角色
     */
    @PostMapping("/roles")
    public ResponseEntity<?> createRole(
            @RequestHeader("Authorization") String token,
            @RequestBody CreateRoleRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            if (roleRepository.existsByRoleCode(request.roleCode())) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "角色编码已存在: " + request.roleCode()));
            }
            Role role = new Role();
            role.setRoleCode(request.roleCode());
            role.setRoleName(request.roleName());
            role.setDescription(request.description());
            role.setSystem(false);
            roleRepository.save(role);
            LogUtils.logUserOperation(adminUsername, "CREATE_ROLE", "role:" + request.roleCode(), "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色创建成功"));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ROLE", adminUsername, "创建角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建角色失败: " + e.getMessage()));
        }
    }

    /**
     * 更新角色基本信息及其权限列表
     */
    @PutMapping("/roles/{roleId}")
    public ResponseEntity<?> updateRole(
            @RequestHeader("Authorization") String token,
            @PathVariable Long roleId,
            @RequestBody UpdateRoleRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new CustomException("角色不存在", HttpStatus.NOT_FOUND));
            if (request.roleName() != null) role.setRoleName(request.roleName());
            if (request.description() != null) role.setDescription(request.description());
            if (request.permCodes() != null) {
                Set<Permission> newPerms = new HashSet<>();
                for (String permCode : request.permCodes()) {
                    Permission perm = permissionRepository.findByPermCode(permCode)
                            .orElseThrow(() -> new CustomException("权限不存在: " + permCode, HttpStatus.BAD_REQUEST));
                    newPerms.add(perm);
                }
                role.setPermissions(newPerms);
            }
            roleRepository.save(role);
            // 清除所有拥有该角色的用户的权限缓存
            List<User> affectedUsers = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getId().equals(roleId)))
                .toList();
            affectedUsers.forEach(u -> rbacService.evictUserPermissionCache(u.getUsername()));
            LogUtils.logUserOperation(adminUsername, "UPDATE_ROLE", "role:" + roleId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色更新成功"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ROLE", adminUsername, "更新角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新角色失败: " + e.getMessage()));
        }
    }

    /**
     * 删除角色（内置角色不允许删除）
     */
    @DeleteMapping("/roles/{roleId}")
    public ResponseEntity<?> deleteRole(
            @RequestHeader("Authorization") String token,
            @PathVariable Long roleId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new CustomException("角色不存在", HttpStatus.NOT_FOUND));
            if (role.isSystem()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "内置角色不允许删除"));
            }
            // 清除所有拥有该角色用户的权限缓存
            List<User> affectedUsers = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getId().equals(roleId)))
                .toList();
            affectedUsers.forEach(u -> rbacService.evictUserPermissionCache(u.getUsername()));
            roleRepository.delete(role);
            LogUtils.logUserOperation(adminUsername, "DELETE_ROLE", "role:" + roleId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色删除成功"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ROLE", adminUsername, "删除角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除角色失败: " + e.getMessage()));
        }
    }

    /** 创建角色请求体 */
    public record CreateRoleRequest(String roleCode, String roleName, String description) {}

    /** 更新角色请求体（权限列表全量替换） */
    public record UpdateRoleRequest(String roleName, String description, List<String> permCodes) {}
}
