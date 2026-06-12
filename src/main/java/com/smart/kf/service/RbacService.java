package com.smart.kf.service;

import com.smart.kf.model.ResourcePermission;
import com.smart.kf.model.Role;
import com.smart.kf.model.User;
import com.smart.kf.repository.ResourcePermissionRepository;
import com.smart.kf.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RBAC 核心权限服务
 * 提供统一的权限判断入口，支持：
 *   1. 功能级权限校验：用户是否拥有某权限编码
 *   2. 资源级权限校验（行级数据权限）：用户是否对某具体资源有指定操作权限
 *   3. 资源授权管理：授予/撤销某资源的访问权限
 * 性能优化：
 *   - 用户权限集合缓存于 Redis，key = rbac:user_perms:{username}，TTL = 5分钟
 *   - 角色/权限变更时调用 evictUserPermissionCache() 清除缓存
 */
@Service
public class RbacService {

    private static final Logger logger = LoggerFactory.getLogger(RbacService.class);

    /** Redis 缓存前缀与 TTL */
    private static final String PERM_CACHE_PREFIX = "rbac:user_perms:";
    private static final long CACHE_TTL_MINUTES = 5;

    /** 权限级别层次，用于包含性判断（write 隐含 read，admin 隐含 write） */
    private static final Map<String, List<String>> PERM_HIERARCHY = Map.of(
        "admin",  List.of("admin", "write", "read", "delete", "run"),
        "write",  List.of("write", "read"),
        "delete", List.of("delete", "read"),
        "read",   List.of("read"),
        "run",    List.of("run"),
        "use",    List.of("use")
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourcePermissionRepository resourcePermissionRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // ========== 功能级权限 ==========

    /**
     * 判断用户是否拥有指定权限（功能级）
     *
     * @param username 用户名
     * @param permCode 权限编码，如 kb:write
     * @return 是否有权限
     */
    public boolean hasPermission(String username, String permCode) {
        if (username == null || permCode == null) {
            return false;
        }
        Set<String> userPerms = getUserPermissions(username);
        return userPerms.contains(permCode);
    }

    /**
     * 获取用户的全部权限编码集合（带 Redis 缓存）
     *
     * @param username 用户名
     * @return 权限编码集合
     */
    public Set<String> getUserPermissions(String username) {
        String cacheKey = PERM_CACHE_PREFIX + username;

        // 1. 先读 Redis 缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return new HashSet<>(Arrays.asList(cached.split(",")));
        }

        // 2. 从数据库查询
        Set<String> perms = loadUserPermissionsFromDB(username);

        // 3. 回写 Redis 缓存（空集合也缓存，防止缓存穿透）
        if (!perms.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, String.join(",", perms), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }

        return perms;
    }

    /**
     * 从数据库加载用户权限（不走缓存）
     */
    private Set<String> loadUserPermissionsFromDB(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            logger.warn("权限查询：用户不存在: {}", username);
            return Collections.emptySet();
        }

        User user = userOpt.get();
        Set<String> perms = new HashSet<>();

        // 从用户的角色集合中提取所有权限编码
        for (Role role : user.getRoles()) {
            role.getPermissions().forEach(p -> perms.add(p.getPermCode()));
        }

        // 兼容旧 legacyRole：若 roles 集合为空，则根据 legacyRole 推断基础权限
        @SuppressWarnings("deprecation")
        User.Role legacy = user.getLegacyRole();
        if (perms.isEmpty() && legacy != null) {
            if (legacy == User.Role.ADMIN) {
                perms.addAll(List.of(
                    "kb:read", "kb:write", "kb:delete", "kb:admin",
                    "doc:read", "doc:write", "doc:delete",
                    "agent:read", "agent:write", "agent:run",
                    "user:read", "user:write",
                    "system:admin", "chat:use"
                ));
            } else {
                perms.addAll(List.of(
                    "kb:read", "kb:write",
                    "doc:read", "doc:write", "doc:delete",
                    "agent:read", "agent:write", "agent:run",
                    "chat:use"
                ));
            }
        }

        logger.debug("用户 {} 拥有权限: {}", username, perms);
        return perms;
    }

    /**
     * 清除用户权限缓存（角色/权限变更时调用）
     *
     * @param username 用户名
     */
    public void evictUserPermissionCache(String username) {
        String cacheKey = PERM_CACHE_PREFIX + username;
        redisTemplate.delete(cacheKey);
        logger.debug("已清除用户权限缓存: {}", username);
    }

    /**
     * 批量清除多个用户的权限缓存
     */
    @SuppressWarnings("unused")
    public void evictUserPermissionCaches(List<String> usernames) {
        usernames.forEach(this::evictUserPermissionCache);
    }

    // ========== 资源级数据权限 ==========

    /**
     * 判断用户是否对某资源拥有指定操作权限（行级数据权限）
     * 检查顺序：
     *   1. 系统管理员直接放行
     *   2. 资源创建者（owner）拥有完整权限
     *   3. 查询 resource_permissions 表（用户直接授权、角色授权、组织标签授权）
     *
     * @param username     用户名
     * @param resourceType 资源类型：kb、doc、agent
     * @param resourceId   资源ID
     * @param requiredPerm 所需操作：read、write、delete、admin
     * @param ownerId      资源所有者用户名（可为 null）
     * @param orgTag       资源归属组织标签（可为 null）
     * @param isPublic     资源是否公开
     * @return 是否有访问权限
     */
    public boolean hasResourcePermission(String username, String resourceType, String resourceId,
                                          String requiredPerm, String ownerId, String orgTag, boolean isPublic) {
        if (username == null || resourceType == null || resourceId == null) {
            return false;
        }

        // 1. 系统管理员拥有一切权限
        if (hasPermission(username, "system:admin")) {
            logger.debug("资源权限 [{}/{}]: 系统管理员 {} 直接放行", resourceType, resourceId, username);
            return true;
        }

        // 2. 公开资源可以被任何已登录用户读取
        if (isPublic && "read".equals(requiredPerm)) {
            logger.debug("资源权限 [{}/{}]: 公开资源允许读取", resourceType, resourceId);
            return true;
        }

        // 3. 资源创建者拥有完整权限
        if (username.equals(ownerId)) {
            logger.debug("资源权限 [{}/{}]: 用户 {} 是资源创建者，拥有完整权限", resourceType, resourceId, username);
            return true;
        }

        // 4. 查询 resource_permissions 表（用户直接授权）
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();
        String userId = user.getId().toString();

        if (isGrantedByResourcePerm(resourceType, resourceId, "user", userId, requiredPerm)) {
            logger.debug("资源权限 [{}/{}]: 用户 {} 有直接授权", resourceType, resourceId, username);
            return true;
        }

        // 5. 角色授权：检查用户的每个角色是否被授权
        for (Role role : user.getRoles()) {
            if (isGrantedByResourcePerm(resourceType, resourceId, "role", role.getRoleCode(), requiredPerm)) {
                logger.debug("资源权限 [{}/{}]: 用户 {} 通过角色 {} 有授权", resourceType, resourceId, username, role.getRoleCode());
                return true;
            }
        }

        // 6. 组织标签授权（兼容旧 org_tag 逻辑）
        if (orgTag != null && !orgTag.isEmpty() && !"default".equals(orgTag)) {
            String userOrgTags = user.getOrgTags();
            if (userOrgTags != null && Arrays.asList(userOrgTags.split(",")).contains(orgTag)) {
                if (isGrantedByResourcePerm(resourceType, resourceId, "org", orgTag, requiredPerm)) {
                    logger.debug("资源权限 [{}/{}]: 用户 {} 通过组织标签 {} 有授权", resourceType, resourceId, username, orgTag);
                    return true;
                }
                // 兼容旧逻辑：属于同组织的用户默认有 read 权限
                if ("read".equals(requiredPerm)) {
                    logger.debug("资源权限 [{}/{}]: 用户 {} 通过组织标签 {} 兼容访问", resourceType, resourceId, username, orgTag);
                    return true;
                }
            }
        }

        logger.debug("资源权限 [{}/{}]: 用户 {} 无权访问（requiredPerm={}）", resourceType, resourceId, username, requiredPerm);
        return false;
    }

    /**
     * 检查 resource_permissions 表中是否存在匹配的授权记录（含权限层次包含判断）
     */
    private boolean isGrantedByResourcePerm(String resourceType, String resourceId,
                                              String granteeType, String granteeId, String requiredPerm) {
        List<ResourcePermission> grants = resourcePermissionRepository
            .findByResourceTypeAndResourceId(resourceType, resourceId);

        for (ResourcePermission grant : grants) {
            if (!granteeType.equals(grant.getGranteeType()) || !granteeId.equals(grant.getGranteeId())) {
                continue;
            }
            // 检查授予的权限是否包含所需权限（按层次）
            String grantedPerm = grant.getPermission();
            List<String> impliedPerms = PERM_HIERARCHY.getOrDefault(grantedPerm, List.of(grantedPerm));
            if (impliedPerms.contains(requiredPerm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取用户可访问的某类型资源 ID 列表（用于数据行级过滤）
     *
     * @param username     用户名
     * @param resourceType 资源类型
     * @param minPerm      最低所需权限（read/write/delete/admin）
     * @return 可访问的资源 ID 列表
     */
    @SuppressWarnings("unused")
    public List<String> getAccessibleResourceIds(String username, String resourceType, String minPerm) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Collections.emptyList();
        }
        User user = userOpt.get();
        String userId = user.getId().toString();

        // 收集被授权对象的所有 ID（用户ID、角色编码、组织标签）
        List<String> granteeIds = new ArrayList<>();
        granteeIds.add(userId);
        user.getRoles().forEach(r -> granteeIds.add(r.getRoleCode()));
        if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
            granteeIds.addAll(Arrays.asList(user.getOrgTags().split(",")));
        }

        // 查询可访问权限（admin 隐含所有低级权限）
        List<String> acceptedPerms = PERM_HIERARCHY.getOrDefault(minPerm, List.of(minPerm));

        Set<String> ids = new HashSet<>();
        for (String granteeId : granteeIds) {
            // 分别查 user / role / org 类型
            List<String> userIds = resourcePermissionRepository.findAccessibleResourceIds(
                resourceType, "user", List.of(granteeId), acceptedPerms);
            ids.addAll(userIds);
        }
        // 角色查询
        List<String> roleCodes = user.getRoles().stream()
            .map(Role::getRoleCode).collect(Collectors.toList());
        if (!roleCodes.isEmpty()) {
            ids.addAll(resourcePermissionRepository.findAccessibleResourceIds(
                resourceType, "role", roleCodes, acceptedPerms));
        }
        // 组织标签查询
        if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
            List<String> orgTags = Arrays.asList(user.getOrgTags().split(","));
            ids.addAll(resourcePermissionRepository.findAccessibleResourceIds(
                resourceType, "org", orgTags, acceptedPerms));
        }

        return new ArrayList<>(ids);
    }

    // ========== 资源权限管理 ==========

    /**
     * 授予用户/角色/组织对某资源的权限
     *
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @param granteeType  被授权类型：user、role、org
     * @param granteeId    被授权对象ID
     * @param permission   权限级别：read、write、delete、admin
     * @param grantedByUserId 授权人用户ID
     */
    @Transactional
    public ResourcePermission grantResourcePermission(String resourceType, String resourceId,
                                                       String granteeType, String granteeId,
                                                       String permission, Long grantedByUserId) {
        // 检查是否已存在
        Optional<ResourcePermission> existing = resourcePermissionRepository
            .findByResourceTypeAndResourceIdAndGranteeTypeAndGranteeId(
                resourceType, resourceId, granteeType, granteeId);

        ResourcePermission rp;
        if (existing.isPresent()) {
            rp = existing.get();
            rp.setPermission(permission); // 更新权限级别
        } else {
            rp = new ResourcePermission();
            rp.setResourceType(resourceType);
            rp.setResourceId(resourceId);
            rp.setGranteeType(granteeType);
            rp.setGranteeId(granteeId);
            rp.setPermission(permission);
            rp.setGrantedBy(grantedByUserId);
        }

        ResourcePermission saved = resourcePermissionRepository.save(rp);
        logger.info("授权: [{}/{}] -> granteeType={}, granteeId={}, perm={}",
            resourceType, resourceId, granteeType, granteeId, permission);
        return saved;
    }

    /**
     * 撤销授权
     */
    @Transactional
    public void revokeResourcePermission(String resourceType, String resourceId,
                                          String granteeType, String granteeId) {
        resourcePermissionRepository.deleteByResourceTypeAndResourceIdAndGranteeTypeAndGranteeId(
            resourceType, resourceId, granteeType, granteeId);
        logger.info("撤销授权: [{}/{}] -> granteeType={}, granteeId={}",
            resourceType, resourceId, granteeType, granteeId);
    }

    /**
     * 删除资源时清除其所有权限记录
     */
    @Transactional
    public void deleteAllResourcePermissions(String resourceType, String resourceId) {
        resourcePermissionRepository.deleteByResourceTypeAndResourceId(resourceType, resourceId);
        logger.info("清除资源所有权限记录: [{}/{}]", resourceType, resourceId);
    }

    /**
     * 查询某资源的权限列表
     */
    public List<ResourcePermission> listResourcePermissions(String resourceType, String resourceId) {
        return resourcePermissionRepository.findByResourceTypeAndResourceId(resourceType, resourceId);
    }
}
