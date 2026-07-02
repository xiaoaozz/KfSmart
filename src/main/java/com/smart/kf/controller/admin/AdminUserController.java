package com.smart.kf.controller.admin;

import com.smart.kf.exception.CustomException;
import com.smart.kf.model.KnowledgeBase;
import com.smart.kf.model.OrganizationTag;
import com.smart.kf.model.Role;
import com.smart.kf.model.User;
import com.smart.kf.repository.ConversationRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.RoleRepository;
import com.smart.kf.repository.UserFavoriteRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.AdminAuthHelper;
import com.smart.kf.service.RbacService;
import com.smart.kf.service.UserService;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import com.smart.kf.utils.PasswordUtil;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 管理员用户管理控制器（从 AdminController 拆分而来）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('system:admin')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final RbacService rbacService;
    private final JwtUtils jwtUtils;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final ConversationRepository conversationRepository;
    private final OrganizationTagRepository organizationTagRepository;
    private final RoleRepository roleRepository;
    private final AdminAuthHelper adminAuthHelper;

    /**
     * 获取所有用户列表
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ALL_USERS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            adminAuthHelper.validateAdmin(adminUsername);

            LogUtils.logBusiness("ADMIN_GET_ALL_USERS", adminUsername, "管理员开始获取所有用户列表");

            List<User> users = userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
            // 移除敏感信息
            users.forEach(user -> user.setPassword(null));

            LogUtils.logUserOperation(adminUsername, "ADMIN_GET_ALL_USERS", "user_list", "SUCCESS");
            LogUtils.logBusiness("ADMIN_GET_ALL_USERS", adminUsername, "成功获取用户列表，用户数量: %d", users.size());
            monitor.end("获取用户列表成功");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get all users successful",
                "data", PageResult.fromList(users, PageQuery.of(page, size, cursor))
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_USERS", adminUsername, "获取所有用户失败", e);
            monitor.end("获取用户列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to get users: " + e.getMessage()));
        }
    }

    /**
     * 更新用户信息（用户名、邮箱、角色）
     */
    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        try {
            adminAuthHelper.validateAdmin(adminUsername);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            if (request.containsKey("username")) user.setUsername((String) request.get("username"));
            if (request.containsKey("email")) user.setEmail((String) request.get("email"));
            if (request.containsKey("role")) {
                user.setRole(User.Role.valueOf(((String) request.get("role")).toUpperCase()));
            }
            userRepository.save(user);
            user.setPassword(null);
            LogUtils.logUserOperation(adminUsername, "ADMIN_UPDATE_USER", "user:" + userId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "用户信息更新成功", "data", user));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_USER", adminUsername, "更新用户失败: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新用户失败: " + e.getMessage()));
        }
    }

    /**
     * 删除用户（不允许删除管理员账号）
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        try {
            adminAuthHelper.validateAdmin(adminUsername);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            if (user.getRole() == User.Role.ADMIN) {
                throw new CustomException("不能删除管理员账号", HttpStatus.FORBIDDEN);
            }
            User admin = userRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new CustomException("管理员账号不存在", HttpStatus.NOT_FOUND));

            // 删除用户私有数据
            userFavoriteRepository.deleteAll(userFavoriteRepository.findByUserOrderByUpdatedAtDesc(user));
            conversationRepository.deleteAll(conversationRepository.findByUserId(userId));

            // 将该用户创建的共享资源转让给执行删除的管理员
            List<OrganizationTag> ownedTags = organizationTagRepository.findByCreatedBy(user);
            ownedTags.forEach(tag -> tag.setCreatedBy(admin));
            organizationTagRepository.saveAll(ownedTags);

            List<KnowledgeBase> ownedKbs = knowledgeBaseRepository.findByCreatedBy(user);
            ownedKbs.forEach(kb -> kb.setCreatedBy(admin));
            knowledgeBaseRepository.saveAll(ownedKbs);

            userRepository.delete(user);
            LogUtils.logUserOperation(adminUsername, "ADMIN_DELETE_USER", "user:" + userId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "用户删除成功"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_USER", adminUsername, "删除用户失败: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除用户失败: " + e.getMessage()));
        }
    }

    /**
     * 重置用户密码（生成随机12位密码并返回，管理员转告用户）
     */
    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<?> resetUserPassword(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        try {
            adminAuthHelper.validateAdmin(adminUsername);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            String newPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            user.setPassword(PasswordUtil.encode(newPassword));
            userRepository.save(user);
            LogUtils.logUserOperation(adminUsername, "ADMIN_RESET_PASSWORD", "user:" + userId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "密码重置成功", "data", Map.of("newPassword", newPassword)));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_RESET_PASSWORD", adminUsername, "重置密码失败: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "重置密码失败: " + e.getMessage()));
        }
    }

    /**
     * 创建管理员用户
     */
    @PostMapping("/users/create-admin")
    public ResponseEntity<?> createAdminUser(
            @RequestHeader("Authorization") String token,
            @RequestBody AdminUserRequest request) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);

        try {
            userService.createAdminUser(request.username(), request.password(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "管理员用户创建成功"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ADMIN_USER", adminUsername, "创建管理员用户失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ADMIN_USER", adminUsername, "创建管理员用户异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建管理员用户失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户列表
     */
    @GetMapping("/users/list")
    public ResponseEntity<?> getUserList(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orgTag,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);

        try {
            Map<String, Object> usersData = userService.getUserList(keyword, orgTag, status, page, size, cursor);
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取用户列表成功",
                "data", usersData
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_LIST", adminUsername, "获取用户列表失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_LIST", adminUsername, "获取用户列表异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取用户列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定用户的角色列表
     */
    @GetMapping("/users/{userId}/roles")
    public ResponseEntity<?> getUserRoles(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            List<Map<String, Object>> roles = user.getRoles().stream()
                .map(r -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("roleCode", r.getRoleCode());
                    m.put("roleName", r.getRoleName());
                    m.put("description", r.getDescription());
                    m.put("isSystem", r.isSystem());
                    return m;
                }).toList();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取用户角色成功", "data", roles));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_ROLES", adminUsername, "获取用户角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取用户角色失败: " + e.getMessage()));
        }
    }

    /**
     * 为用户分配角色（全量替换）
     */
    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<?> assignRolesToUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody AssignRolesRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));

            // 查找所有目标角色
            Set<Role> newRoles = new HashSet<>();
            for (String roleCode : request.roleCodes()) {
                Role role = roleRepository.findByRoleCode(roleCode)
                        .orElseThrow(() -> new CustomException("角色不存在: " + roleCode, HttpStatus.BAD_REQUEST));
                newRoles.add(role);
            }

            user.setRoles(newRoles);
            // 同步 legacyRole
            boolean isAdmin = newRoles.stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getRoleCode()));
            user.setRole(isAdmin ? User.Role.ADMIN : User.Role.USER);

            userRepository.save(user);

            // 清除权限缓存
            rbacService.evictUserPermissionCache(user.getUsername());

            LogUtils.logUserOperation(adminUsername, "ASSIGN_ROLES",
                "user:" + userId, "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "角色分配成功"));
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ROLES", adminUsername, "分配角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "分配角色失败: " + e.getMessage()));
        }
    }

    /**
     * 为用户分配组织标签
     */
    @PutMapping("/users/{userId}/org-tags")
    public ResponseEntity<?> assignOrgTagsToUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody AssignOrgTagsRequest request) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);

        try {
            userService.assignOrgTagsToUser(userId, request.orgTags(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "组织标签分配成功"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", adminUsername, "分配组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", adminUsername, "分配组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "分配组织标签失败: " + e.getMessage()));
        }
    }

    /** 分配角色请求体 */
    public record AssignRolesRequest(List<String> roleCodes) {}

    /** 管理员用户请求体 */
    public record AdminUserRequest(String username, String password) {}

    /** 分配组织标签请求体 */
    public record AssignOrgTagsRequest(List<String> orgTags) {}
}
