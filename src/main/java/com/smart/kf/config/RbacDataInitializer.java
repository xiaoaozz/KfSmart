package com.smart.kf.config;

import com.smart.kf.model.Permission;
import com.smart.kf.model.Role;
import com.smart.kf.model.User;
import com.smart.kf.repository.PermissionRepository;
import com.smart.kf.repository.RoleRepository;
import com.smart.kf.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * RBAC 内置数据初始化器
 * 在应用启动时自动创建内置角色、权限，并将已有用户迁移到新的角色体系
 * 执行顺序在 AdminUserInitializer（Order=1）之后
 */
@Component
@Order(2)
public class RbacDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RbacDataInitializer.class);

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    // ============ 内置权限定义 ============
    private static final String[][] BUILT_IN_PERMISSIONS = {
        // {permCode, permName, resourceType, action, description}
        {"kb:read",      "知识库读取",     "kb",     "read",   "查看和搜索知识库"},
        {"kb:write",     "知识库创建/编辑","kb",     "write",  "创建和修改知识库"},
        {"kb:delete",    "知识库删除",     "kb",     "delete", "删除知识库"},
        {"kb:admin",     "知识库管理",     "kb",     "admin",  "管理知识库权限和成员"},
        {"doc:read",     "文档读取",       "doc",    "read",   "查看和下载文档"},
        {"doc:write",    "文档上传/编辑",  "doc",    "write",  "上传和修改文档"},
        {"doc:delete",   "文档删除",       "doc",    "delete", "删除文档"},
        {"agent:read",   "Agent读取",      "agent",  "read",   "查看Agent工作流"},
        {"agent:write",  "Agent创建/编辑", "agent",  "write",  "创建和修改Agent工作流"},
        {"agent:run",    "Agent执行",      "agent",  "run",    "运行Agent工作流"},
        {"user:read",    "用户信息读取",   "user",   "read",   "查看用户列表和信息"},
        {"user:write",   "用户管理",       "user",   "write",  "创建、修改和删除用户"},
        {"system:admin", "系统配置管理",   "system", "admin",  "管理系统配置、API Key、组织标签等"},
        {"chat:use",     "聊天功能",       "chat",   "use",    "使用AI聊天功能"},
    };

    // ============ 内置角色定义 ============
    // {roleCode, roleName, description, isSystem, permCodes...}
    private static final String ROLE_ADMIN_CODE      = "ROLE_ADMIN";
    private static final String ROLE_KB_MANAGER_CODE = "ROLE_KB_MANAGER";
    private static final String ROLE_USER_CODE       = "ROLE_USER";
    private static final String ROLE_VIEWER_CODE     = "ROLE_VIEWER";

    @Override
    @Transactional
    public void run(String... args) {
        logger.info("开始初始化 RBAC 内置角色权限数据...");

        try {
            // 1. 初始化权限
            Map<String, Permission> permMap = initPermissions();

            // 2. 初始化角色
            initRoles(permMap);

            // 3. 迁移已有用户到新角色体系
            migrateExistingUsers();

            logger.info("RBAC 内置数据初始化完成");
        } catch (Exception e) {
            logger.error("RBAC 初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化内置权限（已存在则跳过）
     */
    private Map<String, Permission> initPermissions() {
        Map<String, Permission> permMap = new HashMap<>();

        for (String[] permDef : BUILT_IN_PERMISSIONS) {
            String permCode = permDef[0];
            if (!permissionRepository.existsByPermCode(permCode)) {
                Permission perm = new Permission();
                perm.setPermCode(permDef[0]);
                perm.setPermName(permDef[1]);
                perm.setResourceType(permDef[2]);
                perm.setAction(permDef[3]);
                perm.setDescription(permDef[4]);
                permissionRepository.save(perm);
                logger.info("创建权限: {}", permCode);
            }
            permMap.put(permCode, permissionRepository.findByPermCode(permCode).orElseThrow());
        }
        return permMap;
    }

    /**
     * 初始化内置角色（已存在则更新权限集合）
     */
    private void initRoles(Map<String, Permission> permMap) {
        // ROLE_ADMIN: 全部权限
        Set<Permission> adminPerms = new HashSet<>(permMap.values());
        createOrUpdateRole(ROLE_ADMIN_CODE, "系统管理员", "拥有全部权限的系统管理员", adminPerms);

        // ROLE_KB_MANAGER: 知识库+文档管理权限
        Set<Permission> kbMgrPerms = new HashSet<>(Arrays.asList(
            permMap.get("kb:read"), permMap.get("kb:write"), permMap.get("kb:delete"), permMap.get("kb:admin"),
            permMap.get("doc:read"), permMap.get("doc:write"), permMap.get("doc:delete"),
            permMap.get("chat:use")
        ));
        createOrUpdateRole(ROLE_KB_MANAGER_CODE, "知识库管理员", "管理知识库和文档的专属角色", kbMgrPerms);

        // ROLE_USER: 普通用户权限
        Set<Permission> userPerms = new HashSet<>(Arrays.asList(
            permMap.get("kb:read"), permMap.get("kb:write"),
            permMap.get("doc:read"), permMap.get("doc:write"), permMap.get("doc:delete"),
            permMap.get("agent:read"), permMap.get("agent:write"), permMap.get("agent:run"),
            permMap.get("chat:use")
        ));
        createOrUpdateRole(ROLE_USER_CODE, "普通用户", "标准用户权限，可使用知识库和聊天功能", userPerms);

        // ROLE_VIEWER: 只读用户
        Set<Permission> viewerPerms = new HashSet<>(Arrays.asList(
            permMap.get("kb:read"), permMap.get("doc:read"), permMap.get("chat:use")
        ));
        createOrUpdateRole(ROLE_VIEWER_CODE, "只读用户", "仅可查看内容，不可修改", viewerPerms);
    }

    private void createOrUpdateRole(String roleCode, String roleName, String description,
                                     Set<Permission> permissions) {
        Optional<Role> existing = roleRepository.findByRoleCode(roleCode);
        Role role;
        if (existing.isEmpty()) {
            role = new Role();
            role.setRoleCode(roleCode);
            logger.info("创建角色: {}", roleCode);
        } else {
            role = existing.get();
        }
        role.setRoleName(roleName);
        role.setDescription(description);
        role.setSystem(true); // 通过此初始化器创建的角色均为内置系统角色
        role.setPermissions(permissions);
        roleRepository.save(role);
    }

    /**
     * 将现有用户的 legacyRole 迁移到新的 roles 集合
     * 仅处理 roles 集合为空的用户（尚未迁移）
     */
    private void migrateExistingUsers() {
        Role adminRole = roleRepository.findByRoleCode(ROLE_ADMIN_CODE).orElse(null);
        Role userRole  = roleRepository.findByRoleCode(ROLE_USER_CODE).orElse(null);
        if (adminRole == null || userRole == null) {
            logger.warn("内置角色不存在，跳过用户迁移");
            return;
        }

        List<User> allUsers = userRepository.findAll();
        int migrated = 0;
        for (User user : allUsers) {
            if (!user.getRoles().isEmpty()) {
                continue; // 已迁移，跳过
            }
            @SuppressWarnings("deprecation")
            User.Role legacyRole = user.getLegacyRole();
            if (legacyRole == null) {
                // legacyRole 为 null 时，按 legacyRole 字段名兼容处理
                legacyRole = User.Role.USER;
            }
            if (legacyRole == User.Role.ADMIN) {
                user.getRoles().add(adminRole);
            } else {
                user.getRoles().add(userRole);
            }
            userRepository.save(user);
            migrated++;
            logger.info("用户 '{}' 迁移到角色: {}", user.getUsername(), legacyRole);
        }
        logger.info("用户角色迁移完成，共迁移 {} 位用户", migrated);
    }
}
