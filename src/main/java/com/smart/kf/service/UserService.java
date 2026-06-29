package com.smart.kf.service;

import com.smart.kf.config.LocaleContext;
import com.smart.kf.exception.CustomException;
import com.smart.kf.model.FileUpload;
import com.smart.kf.model.LoginRecord;
import com.smart.kf.model.OrganizationTag;
import com.smart.kf.model.OrganizationTagI18n;
import com.smart.kf.model.User;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.LoginRecordRepository;
import com.smart.kf.repository.OrganizationTagI18nRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.pagination.PageResult;
import com.smart.kf.utils.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * UserService 类用于处理用户注册和认证相关的业务逻辑。
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    private static final String DEFAULT_ORG_TAG = "default";
    private static final String ADMIN_ORG_TAG = "admin";
    private static final String DEFAULT_ORG_NAME = "默认组织";
    private static final String DEFAULT_ORG_DESCRIPTION = "系统默认组织标签，自动分配给所有新用户";
    private static final String PRIVATE_TAG_PREFIX = "PRIVATE_";
    private static final String PENDING_ORG_TAG = "new-users";
    private static final String PENDING_ORG_NAME = "新注册用户";
    private static final String PENDING_ORG_DESCRIPTION = "新注册用户的待分配组织，管理员可将用户分配到具体部门";

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Autowired
    private OrganizationTagI18nRepository organizationTagI18nRepository;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;
    
    @Autowired
    private OrgTagCacheService orgTagCacheService;

    @Autowired
    private LoginRecordRepository loginRecordRepository;

    @Autowired
    private I18nTranslationService i18nTranslationService;

    /*
     *
     * 注册新用户。
     *
     * @param username 要注册的用户名
     * @param password 要注册的用户密码
     * @throws CustomException 如果用户名已存在，则抛出异常
     */
    /** Backward-compatible overload used by tests and internal callers. */
    @Transactional
    public void registerUser(String username, String password) {
        registerUser(username, password, null);
    }

    @Transactional
    public void registerUser(String username, String password, String email) {
        // 检查数据库中是否已存在该用户名
        if (userRepository.findByUsername(username).isPresent()) {
            // 若用户名已存在，抛出自定义异常，状态码为 400 Bad Request
            throw new CustomException("用户名已存在，请跳转登录", HttpStatus.BAD_REQUEST);
        }

        // 邮箱唯一性校验（application 层强制）
        if (email != null && !email.isBlank()) {
            if (userRepository.findByEmail(email.trim().toLowerCase()).isPresent()) {
                throw new CustomException("该邮箱已被注册", HttpStatus.BAD_REQUEST);
            }
        }

        // 确保默认组织标签和新注册用户组织存在
        ensureDefaultOrgTagExists();
        ensurePendingOrgExists();

        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(password));
        user.setRole(User.Role.USER);
        if (email != null && !email.isBlank()) {
            user.setEmail(email.trim().toLowerCase());
        }

        // 将新用户放入"新注册用户"待分配组织，管理员后续分配具体部门
        user.setOrgTags(PENDING_ORG_TAG);
        user.setPrimaryOrg(PENDING_ORG_TAG);

        userRepository.save(user);

        // 缓存组织标签信息
        orgTagCacheService.cacheUserOrgTags(username, List.of(PENDING_ORG_TAG));
        orgTagCacheService.cacheUserPrimaryOrg(username, PENDING_ORG_TAG);

        logger.info("User registered successfully, placed in pending org '{}': {}", PENDING_ORG_TAG, username);
    }
    
    /**
     * 确保"新注册用户"待分配组织标签存在
     */
    private void ensurePendingOrgExists() {
        if (!organizationTagRepository.existsByTagId(PENDING_ORG_TAG)) {
            logger.info("Creating pending org tag '{}'", PENDING_ORG_TAG);
            Optional<User> adminUser = userRepository.findAll().stream()
                    .filter(u -> User.Role.ADMIN.equals(u.getRole()))
                    .findFirst();
            User creator = adminUser.orElseGet(this::createSystemAdminIfNotExists);

            OrganizationTag pendingTag = new OrganizationTag();
            pendingTag.setTagId(PENDING_ORG_TAG);
            pendingTag.setName(PENDING_ORG_NAME);
            pendingTag.setDescription(PENDING_ORG_DESCRIPTION);
            pendingTag.setCreatedBy(creator);
            organizationTagRepository.save(pendingTag);
            logger.info("Pending org tag '{}' created successfully", PENDING_ORG_TAG);
        }
    }

    /**
     * 确保默认组织标签存在
     * 同时处理旧版 "DEFAULT" (大写) 标签的数据迁移
     */
    private void ensureDefaultOrgTagExists() {
        // 先检查是否需要迁移旧版 "DEFAULT" (大写) 标签
        if (organizationTagRepository.existsByTagId("DEFAULT") && !organizationTagRepository.existsByTagId(DEFAULT_ORG_TAG)) {
            logger.info("Migrating legacy DEFAULT tag to lowercase 'default'");
            OrganizationTag legacyTag = organizationTagRepository.findByTagId("DEFAULT").orElse(null);
            if (legacyTag != null) {
                legacyTag.setTagId(DEFAULT_ORG_TAG);
                organizationTagRepository.save(legacyTag);
            }
            // 同时更新 users 表和 file_upload 表中的引用
            migrateOrgTagReferences();
            logger.info("Legacy DEFAULT tag migration completed");
        }
        
        if (!organizationTagRepository.existsByTagId(DEFAULT_ORG_TAG)) {
            logger.info("Creating default organization tag");
            
            // 寻找一个管理员用户作为创建者
            Optional<User> adminUser = userRepository.findAll().stream()
                    .filter(user -> User.Role.ADMIN.equals(user.getRole()))
                    .findFirst();
            
            User creator = adminUser.orElseGet(this::createSystemAdminIfNotExists);
            
            // 创建默认组织标签
            OrganizationTag defaultTag = new OrganizationTag();
            defaultTag.setTagId(DEFAULT_ORG_TAG);
            defaultTag.setName(DEFAULT_ORG_NAME);
            defaultTag.setDescription(DEFAULT_ORG_DESCRIPTION);
            defaultTag.setCreatedBy(creator);
            
            organizationTagRepository.save(defaultTag);
            logger.info("Default organization tag created successfully");
        }
    }
    
    /**
     * 迁移组织标签引用（更新 users 表和 file_upload 表中的标签ID）
     */
    private void migrateOrgTagReferences() {
        // 更新 users 表
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (user.getOrgTags() != null && user.getOrgTags().contains("DEFAULT")) {
                String updatedOrgTags = user.getOrgTags().replace("DEFAULT", DEFAULT_ORG_TAG);
                user.setOrgTags(updatedOrgTags);
            }
            if ("DEFAULT".equals(user.getPrimaryOrg())) {
                user.setPrimaryOrg(DEFAULT_ORG_TAG);
            }
            userRepository.save(user);
        }
        
        // 更新 file_upload 表
        List<FileUpload> allFiles = fileUploadRepository.findAll();
        for (FileUpload file : allFiles) {
            if ("DEFAULT".equals(file.getOrgTag())) {
                file.setOrgTag(DEFAULT_ORG_TAG);
                fileUploadRepository.save(file);
            }
        }
        
        logger.info("Migrated org tag references from 'DEFAULT' to '{}': {} users, scanned all files", DEFAULT_ORG_TAG, allUsers.size());
    }
    
    /**
     * 如果系统中没有管理员用户，则创建一个系统管理员
     */
    private User createSystemAdminIfNotExists() {
        String systemAdminUsername = "system_admin";
        
        return userRepository.findByUsername(systemAdminUsername)
                .orElseGet(() -> {
                    logger.info("Creating system admin user");
                    User systemAdmin = new User();
                    systemAdmin.setUsername(systemAdminUsername);
                    // 生成随机密码
                    String randomPassword = generateRandomPassword();
                    systemAdmin.setPassword(PasswordUtil.encode(randomPassword));
                    systemAdmin.setRole(User.Role.ADMIN);
                    
                    logger.info("System admin created with password: {}", randomPassword);
                    return userRepository.save(systemAdmin);
                });
    }
    
    /**
     * 生成随机密码
     */
    private String generateRandomPassword() {
        // 生成16位随机密码
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 创建管理员用户。
     *
     * @param username 要注册的管理员用户名
     * @param password 要注册的管理员密码
     * @param creatorUsername 创建者的用户名（必须是已存在的管理员）
     * @throws CustomException 如果用户名已存在或创建者不是管理员，则抛出异常
     */
    public void createAdminUser(String username, String password, String creatorUsername) {
        // 验证创建者是否为管理员
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new CustomException("Creator not found", HttpStatus.NOT_FOUND));
        
        if (creator.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can create admin accounts", HttpStatus.FORBIDDEN);
        }
        
        // 检查数据库中是否已存在该用户名
        if (userRepository.findByUsername(username).isPresent()) {
            throw new CustomException("Username already exists", HttpStatus.BAD_REQUEST);
        }
        
        User adminUser = new User();
        adminUser.setUsername(username);
        adminUser.setPassword(PasswordUtil.encode(password));
        adminUser.setRole(User.Role.ADMIN);
        userRepository.save(adminUser);
    }

    /**
     * 对用户进行认证。
     *
     * @param identifier 要认证的用户名
     * @param password 要认证的用户密码
     * @return 认证成功后返回用户的用户名
     * @throws CustomException 如果用户名或密码无效，则抛出异常
     */
    public String authenticateUser(String identifier, String password) {
        // 先按用户名查找，再按邮箱回退，统一错误信息避免信息泄露
        String normalizedId = identifier == null ? "" : identifier.trim();
        User user = userRepository.findByUsername(normalizedId)
                .or(() -> userRepository.findByEmail(normalizedId.toLowerCase()))
                .orElseThrow(() -> new CustomException("用户名或密码错误", HttpStatus.UNAUTHORIZED));
        if (!PasswordUtil.matches(password, user.getPassword())) {
            throw new CustomException("用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        return user.getUsername();
    }
    
    /**
     * 创建组织标签
     * 
     * @param tagId 标签唯一标识
     * @param name 标签名称
     * @param description 标签描述
     * @param parentTag 父标签ID（可选）
     * @param creatorUsername 创建者用户名（必须是管理员）
     */
    @Transactional
    public OrganizationTag createOrganizationTag(String tagId, String name, String description,
                                                String parentTag, String creatorUsername) {
        // 参数基础校验
        if (tagId == null || tagId.isBlank()) {
            throw new CustomException("Tag ID is required", HttpStatus.BAD_REQUEST);
        }
        if (name == null || name.isBlank()) {
            throw new CustomException("Tag name is required", HttpStatus.BAD_REQUEST);
        }

        // 验证创建者是否为管理员
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new CustomException("Creator not found", HttpStatus.NOT_FOUND));
        
        if (creator.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can create organization tags", HttpStatus.FORBIDDEN);
        }
        
        // 检查标签ID是否已存在
        if (organizationTagRepository.existsByTagId(tagId)) {
            throw new CustomException("Tag ID already exists", HttpStatus.BAD_REQUEST);
        }
        
        // 如果指定了父标签，检查父标签是否存在
        if (parentTag != null && !parentTag.isEmpty()) {
            organizationTagRepository.findByTagId(parentTag)
                    .orElseThrow(() -> new CustomException("Parent tag not found", HttpStatus.NOT_FOUND));
        }
        
        OrganizationTag tag = new OrganizationTag();
        tag.setTagId(tagId);
        tag.setName(name);
        tag.setDescription(description);
        tag.setParentTag(parentTag);
        tag.setCreatedBy(creator);
        
        OrganizationTag savedTag = organizationTagRepository.save(tag);
        i18nTranslationService.translateOrgTagAsync(tagId, name, description);
        orgTagCacheService.invalidateAllEffectiveTagsCache();
        return savedTag;
    }
    
    /**
     * 为用户分配组织标签
     * 
     * @param userId 用户ID
     * @param orgTags 组织标签ID列表
     * @param adminUsername 管理员用户名
     */
    @Transactional
    public void assignOrgTagsToUser(Long userId, List<String> orgTags, String adminUsername) {
        // 验证操作者是否为管理员
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can assign organization tags", HttpStatus.FORBIDDEN);
        }
        
        // 查找用户
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        // 验证所有标签是否存在
        for (String tagId : orgTags) {
            if (!organizationTagRepository.existsByTagId(tagId)) {
                throw new CustomException("Organization tag " + tagId + " not found", HttpStatus.NOT_FOUND);
            }
        }
        
        // 将 admin 提供的标签列表作为用户的完整组织标签（替换而非追加）
        Set<String> finalTags = new HashSet<>(orgTags);
        String orgTagsStr = String.join(",", finalTags);
        user.setOrgTags(orgTagsStr);

        // 以 admin 指定的第一个标签作为主组织；若清空则保留原值
        if (!orgTags.isEmpty()) {
            user.setPrimaryOrg(orgTags.get(0));
        }

        userRepository.save(user);

        // 更新缓存
        orgTagCacheService.deleteUserOrgTagsCache(user.getUsername());
        orgTagCacheService.cacheUserOrgTags(user.getUsername(), new ArrayList<>(finalTags));
        orgTagCacheService.deleteUserEffectiveTagsCache(user.getUsername());

        if (user.getPrimaryOrg() != null && !user.getPrimaryOrg().isEmpty()) {
            orgTagCacheService.cacheUserPrimaryOrg(user.getUsername(), user.getPrimaryOrg());
        }
    }
    
    /**
     * 获取用户的组织标签信息
     * 
     * @param username 用户名
     * @return 包含用户组织标签信息的Map
     */
    public Map<String, Object> getUserOrgTags(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        // 尝试从缓存获取
        List<String> orgTags = orgTagCacheService.getUserOrgTags(username);
        String primaryOrg = orgTagCacheService.getUserPrimaryOrg(username);
        
        // 如果缓存中没有，则从数据库获取
        if (orgTags == null || orgTags.isEmpty()) {
            String rawTags = user.getOrgTags();
            orgTags = (rawTags != null && !rawTags.isBlank())
                    ? Arrays.asList(rawTags.split(","))
                    : java.util.Collections.emptyList();
            // 更新缓存
            orgTagCacheService.cacheUserOrgTags(username, orgTags);
        }
        
        if (primaryOrg == null || primaryOrg.isEmpty()) {
            primaryOrg = user.getPrimaryOrg();
            // 更新缓存
            orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
        }
        
        // 获取组织标签的详细信息
        List<Map<String, String>> orgTagDetails = new ArrayList<>();
        for (String tagId : orgTags) {
            OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                    .orElse(null);
            if (tag != null) {
                Map<String, String> tagInfo = new HashMap<>();
                tagInfo.put("tagId", tag.getTagId());
                tagInfo.put("name", tag.getName());
                tagInfo.put("description", tag.getDescription());
                orgTagDetails.add(tagInfo);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("orgTags", orgTags);
        result.put("primaryOrg", primaryOrg);
        result.put("orgTagDetails", orgTagDetails);
        
        return result;
    }
    
    /**
     * 设置用户的主组织标签
     * 
     * @param username 用户名
     * @param primaryOrg 主组织标签
     */
    public void setUserPrimaryOrg(String username, String primaryOrg) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        // 检查该组织标签是否已分配给用户
        String rawTags = user.getOrgTags();
        Set<String> userTags = (rawTags != null && !rawTags.isBlank())
                ? Arrays.stream(rawTags.split(",")).collect(Collectors.toSet())
                : new java.util.HashSet<>();
        if (!userTags.contains(primaryOrg)) {
            throw new CustomException("Organization tag not assigned to user", HttpStatus.BAD_REQUEST);
        }
        
        user.setPrimaryOrg(primaryOrg);
        userRepository.save(user);
        
        // 更新缓存
        orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
    }
    
    /**
     * 获取用户的主组织标签
     * 
     * @param userId 用户ID
     * @return 用户的主组织标签
     */
    public String getUserPrimaryOrg(String userId) {
        // 先通过userId查找用户，然后获取username
        User user;
        try {
            Long userIdLong = Long.parseLong(userId);
            user = userRepository.findById(userIdLong)
                .orElseThrow(() -> new CustomException("User not found with ID: " + userId, HttpStatus.NOT_FOUND));
        } catch (NumberFormatException e) {
            // 如果userId不是数字格式，则假设它就是username
            user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
        }
        
        String username = user.getUsername();
        
        // 尝试从缓存获取
        String primaryOrg = orgTagCacheService.getUserPrimaryOrg(username);
        
        // 如果缓存中没有，则从数据库获取
        if (primaryOrg == null || primaryOrg.isEmpty()) {
            primaryOrg = user.getPrimaryOrg();
            
            // 如果用户没有设置主组织标签，则尝试使用第一个分配的组织标签
            if (primaryOrg == null || primaryOrg.isEmpty()) {
                String rawOrgTags = user.getOrgTags();
                String[] tags = (rawOrgTags != null && !rawOrgTags.isBlank()) ? rawOrgTags.split(",") : new String[0];
                if (tags.length > 0) {
                    primaryOrg = tags[0];
                    // 更新用户的主组织标签
                    user.setPrimaryOrg(primaryOrg);
                    userRepository.save(user);
                } else {
                    // 如果用户没有任何组织标签，则使用默认标签
                    primaryOrg = DEFAULT_ORG_TAG;
                }
            }
            
            // 更新缓存
            orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
        }
        
        return primaryOrg;
    }

    /**
     * 获取组织标签树结构
     * 
     * @return 组织标签树结构
     */
    public List<Map<String, Object>> getOrganizationTagTree() {
        // 获取所有根节点（parentTag为null的标签）
        List<OrganizationTag> rootTags = organizationTagRepository.findByParentTag(null);
        
        // 递归构建标签树
        return buildTagTreeRecursive(rootTags);
    }
    
    /**
     * 递归构建标签树
     * 
     * @param tags 当前级别的标签列表
     * @return 树形结构
     */
    private List<Map<String, Object>> buildTagTreeRecursive(List<OrganizationTag> tags) {
        List<Map<String, Object>> result = new ArrayList<>();
        String lang = LocaleContext.get();

        for (OrganizationTag tag : tags) {
            // 跳过历史遗留的私人空间标签（PRIVATE_ 前缀），不在管理 UI 中展示
            if (tag.getTagId().startsWith(PRIVATE_TAG_PREFIX)) {
                continue;
            }
            Map<String, Object> node = new HashMap<>();
            node.put("tagId", tag.getTagId());
            node.put("parentTag", tag.getParentTag());

            String name = tag.getName();
            String description = tag.getDescription();
            if (lang != null && !lang.equals("zh-CN")) {
                var i18nOpt = organizationTagI18nRepository.findByTagIdAndLang(tag.getTagId(), lang);
                if (i18nOpt.isPresent()) {
                    OrganizationTagI18n i18n = i18nOpt.get();
                    if (i18n.getName() != null && !i18n.getName().isBlank()) name = i18n.getName();
                    if (i18n.getDescription() != null && !i18n.getDescription().isBlank()) description = i18n.getDescription();
                }
            }
            node.put("name", name);
            node.put("description", description);

            List<OrganizationTag> children = organizationTagRepository.findByParentTag(tag.getTagId());
            if (!children.isEmpty()) {
                node.put("children", buildTagTreeRecursive(children));
            }

            result.add(node);
        }

        return result;
    }

    @Transactional
    public OrganizationTagI18n upsertOrganizationTagI18n(String tagId, String lang, String name, String description) {
        OrganizationTagI18n i18n = organizationTagI18nRepository
            .findByTagIdAndLang(tagId, lang)
            .orElseGet(() -> {
                OrganizationTagI18n newI18n = new OrganizationTagI18n();
                newI18n.setTagId(tagId);
                newI18n.setLang(lang);
                return newI18n;
            });
        if (name != null) i18n.setName(name);
        if (description != null) i18n.setDescription(description);
        return organizationTagI18nRepository.save(i18n);
    }

    public List<OrganizationTagI18n> getOrganizationTagI18n(String tagId) {
        return organizationTagI18nRepository.findByTagId(tagId);
    }
    
    /**
     * 更新组织标签
     * 
     * @param tagId 标签ID
     * @param name 新名称
     * @param description 新描述
     * @param parentTag 新父标签ID
     * @param adminUsername 管理员用户名
     * @return 更新后的组织标签
     */
    @Transactional
    public OrganizationTag updateOrganizationTag(String tagId, String name, String description, 
                                                String parentTag, String adminUsername) {
        // 验证操作者是否为管理员
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can update organization tags", HttpStatus.FORBIDDEN);
        }
        
        // 获取要更新的标签
        OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                .orElseThrow(() -> new CustomException("Organization tag not found", HttpStatus.NOT_FOUND));
        
        // 如果指定了父标签，检查父标签是否存在
        if (parentTag != null && !parentTag.isEmpty()) {
            // 检查是否为自身
            if (tagId.equals(parentTag)) {
                throw new CustomException("A tag cannot be its own parent", HttpStatus.BAD_REQUEST);
            }
            
            // 检查是否存在
            organizationTagRepository.findByTagId(parentTag)
                    .orElseThrow(() -> new CustomException("Parent tag not found", HttpStatus.NOT_FOUND));
            
            // 检查是否会形成循环
            if (wouldFormCycle(tagId, parentTag)) {
                throw new CustomException("Setting this parent would create a cycle in the tag hierarchy", HttpStatus.BAD_REQUEST);
            }
        }
        
        // 更新标签
        if (name != null && !name.isEmpty()) {
            tag.setName(name);
        }

        if (description != null) {
            tag.setDescription(description);
        }

        // 空字符串统一转为 null，保持 findByParentTag(null) 能正确检索根节点
        tag.setParentTag((parentTag == null || parentTag.isBlank()) ? null : parentTag);
        
        OrganizationTag updatedTag = organizationTagRepository.save(tag);
        i18nTranslationService.retranslateOrgTagAsync(tagId, updatedTag.getName(), updatedTag.getDescription());
        orgTagCacheService.invalidateAllEffectiveTagsCache();
        return updatedTag;
    }
    
    /**
     * 检查是否会形成标签层级循环
     * 
     * @param tagId 要设置父标签的标签ID
     * @param newParentId 新的父标签ID
     * @return 是否会形成循环
     */
    private boolean wouldFormCycle(String tagId, String newParentId) {
        String currentParentId = newParentId;
        
        // 检查是否形成循环
        while (currentParentId != null && !currentParentId.isEmpty()) {
            if (tagId.equals(currentParentId)) {
                return true; // 形成循环
            }
            
            // 获取父标签的父标签
            Optional<OrganizationTag> parentTag = organizationTagRepository.findByTagId(currentParentId);
            if (parentTag.isEmpty()) {
                break;
            }
            
            currentParentId = parentTag.get().getParentTag();
        }
        
        return false;
    }
    
    /**
     * 删除组织标签
     * 系统保护标签（default、admin）不可删除。
     * 对于分配给用户的标签：自动从用户中移除该标签，并将主组织替换为默认标签。
     * 对于关联文档的标签：自动将文档的组织标签替换为默认标签。
     * 
     * @param tagId 标签ID
     * @param adminUsername 管理员用户名
     * @return 删除结果，包含受影响的用户数和文档数
     */
    @Transactional
    public Map<String, Object> deleteOrganizationTag(String tagId, String adminUsername) {
        // 验证操作者是否为管理员
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can delete organization tags", HttpStatus.FORBIDDEN);
        }
        
        // 获取要删除的标签
        OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                .orElseThrow(() -> new CustomException("Organization tag not found", HttpStatus.NOT_FOUND));
        
        // 系统内置标签不可删除
        if (DEFAULT_ORG_TAG.equals(tagId)) {
            throw new CustomException("默认组织标签是系统内置标签，无法删除", HttpStatus.BAD_REQUEST);
        }
        if (ADMIN_ORG_TAG.equals(tagId)) {
            throw new CustomException("管理员组织标签是系统内置标签，无法删除", HttpStatus.BAD_REQUEST);
        }
        if (PENDING_ORG_TAG.equals(tagId)) {
            throw new CustomException("新注册用户组织是系统内置标签，无法删除", HttpStatus.BAD_REQUEST);
        }
        
        // 检查是否有子标签 - 自动将子标签重新分配到被删除标签的父标签
        List<OrganizationTag> children = organizationTagRepository.findByParentTag(tagId);
        int reassignedChildrenCount = 0;
        if (!children.isEmpty()) {
            String newParentTag = tag.getParentTag();
            for (OrganizationTag child : children) {
                child.setParentTag(newParentTag);
                organizationTagRepository.save(child);
                reassignedChildrenCount++;
            }
            logger.info("Reassigned {} child tags of '{}' to parent '{}'", children.size(), tagId, newParentTag);
        }
        
        // 从用户中自动移除该标签，并将主组织替换为默认标签
        List<User> allUsers = userRepository.findAll();
        int affectedUserCount = 0;
        for (User user : allUsers) {
            if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                Set<String> userTags = new HashSet<>(Arrays.asList(user.getOrgTags().split(",")));
                if (userTags.remove(tagId)) {
                    affectedUserCount++;
                    // 确保用户至少有默认标签
                    userTags.add(DEFAULT_ORG_TAG);
                    user.setOrgTags(String.join(",", userTags));
                    
                    // 如果被删除的标签是用户的主组织，替换为默认标签
                    if (tagId.equals(user.getPrimaryOrg())) {
                        user.setPrimaryOrg(DEFAULT_ORG_TAG);
                    }
                    userRepository.save(user);
                    
                    // 清除用户缓存
                    orgTagCacheService.deleteUserOrgTagsCache(user.getUsername());
                    orgTagCacheService.deleteUserEffectiveTagsCache(user.getUsername());
                }
            }
        }
        if (affectedUserCount > 0) {
            logger.info("Unassigned tag '{}' from {} users, reassigned to default tag", tagId, affectedUserCount);
        }
        
        // 将关联文档的组织标签替换为默认标签
        List<FileUpload> filesWithTag = fileUploadRepository.findByOrgTag(tagId);
        int affectedDocumentCount = 0;
        if (!filesWithTag.isEmpty()) {
            for (FileUpload file : filesWithTag) {
                file.setOrgTag(DEFAULT_ORG_TAG);
                fileUploadRepository.save(file);
                affectedDocumentCount++;
            }
            logger.info("Reassigned {} documents from tag '{}' to default tag", filesWithTag.size(), tagId);
        }
        
        // 删除标签
        organizationTagRepository.delete(tag);
        
        // 清除所有标签缓存，因为层级关系可能变化
        orgTagCacheService.invalidateAllEffectiveTagsCache();
        
        logger.info("Organization tag deleted successfully: {}, affected users: {}, affected documents: {}, reassigned children: {}", 
                    tagId, affectedUserCount, affectedDocumentCount, reassignedChildrenCount);
        
        // 返回删除结果
        Map<String, Object> result = new HashMap<>();
        result.put("affectedUserCount", affectedUserCount);
        result.put("affectedDocumentCount", affectedDocumentCount);
        result.put("reassignedChildrenCount", reassignedChildrenCount);
        return result;
    }
    
    /**
     * 获取用户列表，支持分页和过滤
     * 
     * @param keyword 搜索关键词
     * @param orgTag 组织标签过滤
     * @param status 用户状态过滤
     * @param page 页码
     * @param size 每页大小
     * @return 用户列表数据
     */
    public Map<String, Object> getUserList(String keyword, String orgTag, Integer status, int page, int size, String cursor) {
        PageQuery pageQuery = PageQuery.of(page, size, cursor);
        List<User> filteredUsers = userRepository.findAll().stream()
                .filter(user -> {
                    if (orgTag != null && !orgTag.isEmpty()) {
                        if (user.getOrgTags() == null || user.getOrgTags().isEmpty()) {
                            return false;
                        }
                        Set<String> userTags = new HashSet<>(Arrays.asList(user.getOrgTags().split(",")));
                        if (!userTags.contains(orgTag)) {
                            return false;
                        }
                    }

                    if (keyword != null && !keyword.isEmpty()) {
                        boolean matchesKeyword = user.getUsername() != null && user.getUsername().contains(keyword);
                        if (!matchesKeyword) {
                            return false;
                        }
                    }

                    if (status != null) {
                        return user.getRole() == (status == 1 ? User.Role.USER : User.Role.ADMIN);
                    }

                    return true;
                })
                .sorted(java.util.Comparator.comparing(
                        User::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())
                ).reversed())
                .toList();

        PageResult<User> userPage = PageResult.fromList(filteredUsers, pageQuery);
        List<Map<String, Object>> userList = userPage.records().stream()
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("userId", user.getId());
                    userMap.put("id", user.getId());
                    userMap.put("username", user.getUsername());
                    
                    // 获取用户组织标签的详细信息
                    List<Map<String, String>> orgTagDetails = new ArrayList<>();
                    if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                        Arrays.stream(user.getOrgTags().split(","))
                                .forEach(tagId -> {
                                    OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                                            .orElse(null);
                                    if (tag != null) {
                                        Map<String, String> tagInfo = new HashMap<>();
                                        tagInfo.put("tagId", tag.getTagId());
                                        tagInfo.put("name", tag.getName());
                                        orgTagDetails.add(tagInfo);
                                    }
                                });
                    }
                    
                    userMap.put("orgTags", orgTagDetails);
                    userMap.put("primaryOrg", user.getPrimaryOrg());
                    userMap.put("status", user.getRole() == User.Role.USER ? 1 : 0);
                    userMap.put("createdAt", user.getCreatedAt());
                    userMap.put("createTime", user.getCreatedAt());
                    userMap.put("updatedAt", user.getUpdatedAt());

                    // RBAC 角色列表
                    List<Map<String, Object>> rolesList = user.getRoles().stream()
                            .map(r -> {
                                Map<String, Object> rm = new LinkedHashMap<>();
                                rm.put("roleCode", r.getRoleCode());
                                rm.put("roleName", r.getRoleName());
                                return rm;
                            })
                            .toList();
                    userMap.put("roles", rolesList);
                    
                    return userMap;
                })
                .collect(Collectors.toList());
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", userList);
        result.put("content", userList);
        result.put("total", userPage.total());
        result.put("totalElements", userPage.total());
        result.put("totalPages", userPage.totalPages());
        result.put("size", userPage.size());
        result.put("page", userPage.page());
        result.put("number", userPage.page());
        result.put("hasNext", userPage.hasNext());
        result.put("nextCursor", userPage.nextCursor());
        
        return result;
    }

    /**
     * 解析 User-Agent 字符串为可读的设备信息
     * 提取操作系统 + 浏览器信息
     */
    private String parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知设备";
        }

        // 操作系统识别
        String os;
        if (userAgent.contains("Windows NT 10.0") || userAgent.contains("Windows NT 11.0")) {
            os = "Windows 10/11";
        } else if (userAgent.contains("Windows NT 6.1")) {
            os = "Windows 7";
        } else if (userAgent.contains("Windows")) {
            os = "Windows";
        } else if (userAgent.contains("Mac OS X")) {
            os = "macOS";
        } else if (userAgent.contains("Android")) {
            // 尝试提取 Android 版本
            int idx = userAgent.indexOf("Android ");
            String ver = "";
            if (idx >= 0) {
                String sub = userAgent.substring(idx + 8);
                int end = sub.indexOf(';');
                if (end < 0) end = sub.indexOf(')');
                if (end > 0) ver = " " + sub.substring(0, end).trim();
            }
            os = "Android" + ver;
        } else if (userAgent.contains("iPhone") || userAgent.contains("iOS")) {
            os = "iOS";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        } else {
            os = "未知系统";
        }

        // 浏览器识别（顺序重要：Edge > Chrome > Safari > Firefox > Others）
        String browser;
        if (userAgent.contains("Edg/")) {
            browser = "Edge";
        } else if (userAgent.contains("Chrome/") && !userAgent.contains("Chromium")) {
            browser = "Chrome";
        } else if (userAgent.contains("Safari/") && !userAgent.contains("Chrome")) {
            browser = "Safari";
        } else if (userAgent.contains("Firefox/")) {
            browser = "Firefox";
        } else if (userAgent.contains("OPR/") || userAgent.contains("Opera/")) {
            browser = "Opera";
        } else if (userAgent.contains("MSIE") || userAgent.contains("Trident/")) {
            browser = "IE";
        } else if (userAgent.contains("okhttp") || userAgent.contains("Retrofit") || userAgent.contains("Java/")) {
            browser = "客户端应用";
        } else {
            browser = "未知浏览器";
        }

        return os + " / " + browser;
    }

    /**
     * 记录登录事件
     * 
     * @param username 用户名
     * @param userId 用户ID（可为 null，会尝试通过用户名自动查询补全）
     * @param ipAddress IP地址
     * @param rawUserAgent 原始 User-Agent 字符串
     * @param status 登录状态（SUCCESS / FAILED）
     * @param failReason 失败原因（可选）
     */
    public void recordLogin(String username, Long userId, String ipAddress, 
                            String rawUserAgent, String status, String failReason) {
        // 如果 userId 未传入，尝试通过用户名查询
        Long resolvedUserId = userId;
        if (resolvedUserId == null && username != null && !username.isBlank()) {
            try {
                resolvedUserId = userRepository.findByUsername(username)
                        .map(User::getId)
                        .orElse(null);
            } catch (Exception e) {
                logger.warn("recordLogin: could not resolve userId for username={}", username);
            }
        }

        // 解析 User-Agent 为可读设备描述
        String deviceDesc = parseUserAgent(rawUserAgent);

        LoginRecord record = new LoginRecord();
        record.setUserId(resolvedUserId);
        record.setUsername(username);
        record.setLoginTime(java.time.LocalDateTime.now());
        record.setIpAddress(ipAddress);
        record.setDeviceInfo(deviceDesc);
        record.setLocation(""); // 留空，可后续通过 IP 解析补充
        record.setStatus(status);
        record.setFailReason(failReason);

        loginRecordRepository.save(record);
        logger.info("Login record saved: user={}, status={}, ip={}, device={}", username, status, ipAddress, deviceDesc);
    }

    /**
     * 获取用户登录记录（分页）
     * 
     * @param username 用户名
     * @param page 页码（从1开始）
     * @param size 每页条数
     * @return 分页结果
     */
    public Map<String, Object> getLoginRecords(String username, int page, int size) {
        userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        int pageIndex = page > 0 ? page - 1 : 0;
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by("loginTime").descending());

        Page<LoginRecord> recordPage = loginRecordRepository
                .findByUsernameOrderByLoginTimeDesc(username, pageable);

        List<Map<String, Object>> records = recordPage.getContent().stream()
                .map(record -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", record.getId());
                    map.put("username", record.getUsername());
                    map.put("loginTime", record.getLoginTime());
                    map.put("ipAddress", record.getIpAddress());
                    map.put("deviceInfo", record.getDeviceInfo());
                    map.put("location", record.getLocation());
                    map.put("status", record.getStatus());
                    map.put("failReason", record.getFailReason());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", records);
        result.put("totalElements", recordPage.getTotalElements());
        result.put("totalPages", recordPage.getTotalPages());
        result.put("size", recordPage.getSize());
        result.put("number", recordPage.getNumber() + 1);

        return result;
    }

    /**
     * 获取用户登录统计信息
     * 
     * @param username 用户名
     * @return 统计信息
     */
    public Map<String, Object> getLoginStatistics(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        long totalLogins = loginRecordRepository.countByUserId(user.getId());
        long successLogins = loginRecordRepository.countByUserIdAndStatus(user.getId(), "SUCCESS");
        long failedLogins = loginRecordRepository.countByUserIdAndStatus(user.getId(), "FAILED");

        // 最近10条记录
        List<LoginRecord> recentRecords = loginRecordRepository
                .findTop10ByUserIdOrderByLoginTimeDesc(user.getId());

        List<Map<String, Object>> recentList = recentRecords.stream()
                .map(record -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", record.getId());
                    map.put("username", record.getUsername());
                    map.put("loginTime", record.getLoginTime());
                    map.put("ipAddress", record.getIpAddress());
                    map.put("deviceInfo", record.getDeviceInfo());
                    map.put("location", record.getLocation());
                    map.put("status", record.getStatus());
                    map.put("failReason", record.getFailReason());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("totalLogins", totalLogins);
        result.put("successLogins", successLogins);
        result.put("failedLogins", failedLogins);
        result.put("recentRecords", recentList);

        return result;
    }
}
