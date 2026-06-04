package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.KnowledgeBase;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.KnowledgeBaseRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.*;

/**
 * 知识库服务
 * 提供知识库的创建、查询、更新、删除以及统计功能
 * 支持筛选功能：按关键字搜索、按组织标签筛选、按公开状态筛选、按创建者筛选
 */
@Service
public class KnowledgeBaseService {
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgTagCacheService orgTagCacheService;

    private static final int CHUNK_SIZE_BYTES = 4096;

    /**
     * 创建知识库
     */
    @Transactional
    public KnowledgeBase createKnowledgeBase(String name, String description, String orgTag, 
                                              boolean isPublic, String icon, String creatorUsername) {
        LogUtils.logBusiness("CREATE_KB", creatorUsername, "创建知识库: name=%s, orgTag=%s, isPublic=%s", 
            name, orgTag, isPublic);
        
        // 检查同名知识库是否已存在
        if (knowledgeBaseRepository.existsByName(name)) {
            throw new IllegalArgumentException("知识库名称已存在: " + name);
        }
        
        // 生成唯一kbId
        String kbId = "kb_" + UUID.randomUUID().toString().substring(0, 8);
        
        // 查找创建者
        Optional<User> creatorOpt = userRepository.findByUsername(creatorUsername);
        if (creatorOpt.isEmpty()) {
            throw new IllegalArgumentException("用户不存在: " + creatorUsername);
        }
        
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbId(kbId);
        kb.setName(name);
        kb.setDescription(description);
        kb.setOrgTag(orgTag);
        kb.setPublic(isPublic);
        kb.setIcon(icon != null ? icon : "folder");
        kb.setCreatedBy(creatorOpt.get());
        
        KnowledgeBase saved = knowledgeBaseRepository.save(kb);
        LogUtils.logBusiness("CREATE_KB", creatorUsername, "知识库创建成功: kbId=%s, name=%s", kbId, name);
        
        return saved;
    }
    
    /**
     * 判断用户是否可访问该知识库
     */
    private boolean isAccessible(KnowledgeBase kb, String username, Set<String> userOrgTags) {
        // 用户创建的知识库
        if (kb.getCreatedBy() != null && kb.getCreatedBy().getUsername().equals(username)) {
            return true;
        }
        // 公开的知识库
        if (kb.isPublic()) {
            return true;
        }
        // 用户所属组织标签关联的知识库
        return kb.getOrgTag() != null && userOrgTags.contains(kb.getOrgTag());
    }

    /**
     * 判断用户是否无权修改该知识库（更严格的权限检查：非所有者且非管理员）
     */
    private boolean isNotModifiable(KnowledgeBase kb, String username, Set<String> userOrgTags) {
        // 知识库创建者有权修改
        if (kb.getCreatedBy() != null && kb.getCreatedBy().getUsername().equals(username)) {
            return false;
        }
        // 管理员有权修改（通过组织标签匹配判断，组织标签关联的用户视为管理员）
        return kb.getOrgTag() == null || !userOrgTags.contains(kb.getOrgTag());
    }

    /**
     * 解析用户组织标签集合（包含层级权限）
     */
    private Set<String> resolveUserOrgTags(String username, String orgTags) {
        Set<String> userOrgTags = new HashSet<>();
        if (orgTags != null && !orgTags.isEmpty()) {
            userOrgTags.addAll(Arrays.asList(orgTags.split(",")));
        }
        List<String> effectiveTags = orgTagCacheService.getUserEffectiveOrgTags(username);
        if (effectiveTags != null) {
            userOrgTags.addAll(effectiveTags);
        }
        return userOrgTags;
    }

    /**
     * 获取用户可访问的知识库列表（无筛选）
     * 包括：用户创建的、公开的、用户所属组织标签关联的
     */
    public List<Map<String, Object>> getAccessibleKnowledgeBases(String username, String orgTags) {
        return getAccessibleKnowledgeBases(username, orgTags, null, null, null, null, null);
    }

    /**
     * 获取用户可访问的知识库列表（带筛选参数）
     * 包括：用户创建的、公开的、用户所属组织标签关联的
     * 支持筛选：关键字搜索、组织标签、公开状态、创建者、更新时间范围
     *
     * @param username 用户名
     * @param orgTags 用户组织标签（逗号分隔）
     * @param keyword 搜索关键字（匹配知识库名称和描述）
     * @param filterOrgTag 按组织标签筛选
     * @param filterIsPublic 按公开状态筛选（true=仅公开，false=仅私有，null=全部）
     * @param filterCreatedBy 按创建者筛选
     * @param filterUpdatedAfter 按更新时间筛选（只返回在此时间之后更新的知识库）
     */
    public List<Map<String, Object>> getAccessibleKnowledgeBases(String username, String orgTags, 
            String keyword, String filterOrgTag, Boolean filterIsPublic, 
            String filterCreatedBy, LocalDateTime filterUpdatedAfter) {
        LogUtils.logBusiness("GET_KB_LIST", username, "获取知识库列表: orgTags=%s, keyword=%s, filterOrgTag=%s, filterIsPublic=%s, filterCreatedBy=%s, filterUpdatedAfter=%s", 
            orgTags, keyword, filterOrgTag, filterIsPublic, filterCreatedBy, filterUpdatedAfter);
        
        List<KnowledgeBase> allKbs = knowledgeBaseRepository.findAll();
        
        // 解析用户的组织标签（使用层级权限）
        Set<String> userOrgTags = new HashSet<>();
        if (orgTags != null && !orgTags.isEmpty()) {
            userOrgTags.addAll(Arrays.asList(orgTags.split(",")));
        }
        // 同时加入层级权限中的有效标签
        List<String> effectiveTags = orgTagCacheService.getUserEffectiveOrgTags(username);
        if (effectiveTags != null) {
            userOrgTags.addAll(effectiveTags);
        }
        
        // 过滤出用户可访问的知识库
        List<KnowledgeBase> accessibleKbs = allKbs.stream()
            .filter(kb -> isAccessible(kb, username, userOrgTags))
            .toList();
        
        // 应用筛选条件
        List<KnowledgeBase> filteredKbs = accessibleKbs.stream()
            .filter(kb -> {
                // 关键字筛选（匹配名称或描述）
                if (keyword != null && !keyword.isEmpty()) {
                    boolean nameMatch = kb.getName() != null && kb.getName().contains(keyword);
                    boolean descMatch = kb.getDescription() != null && kb.getDescription().contains(keyword);
                    if (!nameMatch && !descMatch) return false;
                }
                // 组织标签筛选
                if (filterOrgTag != null && !filterOrgTag.isEmpty()) {
                    if (!filterOrgTag.equals(kb.getOrgTag())) return false;
                }
                // 公开状态筛选
                if (filterIsPublic != null) {
                    if (filterIsPublic != kb.isPublic()) return false;
                }
                // 创建者筛选
                if (filterCreatedBy != null && !filterCreatedBy.isEmpty()) {
                    if (kb.getCreatedBy() == null || !filterCreatedBy.equals(kb.getCreatedBy().getUsername())) return false;
                }
                // 更新时间筛选
                return filterUpdatedAfter == null || (kb.getUpdatedAt() != null && !kb.getUpdatedAt().isBefore(filterUpdatedAfter));
            })
            .toList();
        
        // 构建返回数据（含统计信息）
        return filteredKbs.stream().map(this::buildKbDto).toList();
    }

    /**
     * 构建知识库DTO（含统计信息）
     */
    private Map<String, Object> buildKbDto(KnowledgeBase kb) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", kb.getId());
        dto.put("kbId", kb.getKbId());
        dto.put("name", kb.getName());
        dto.put("description", kb.getDescription());
        dto.put("orgTag", kb.getOrgTag());
        dto.put("isPublic", kb.isPublic());
        dto.put("icon", kb.getIcon());
        dto.put("createdAt", kb.getCreatedAt());
        dto.put("updatedAt", kb.getUpdatedAt());
        dto.put("createdBy", kb.getCreatedBy() != null ? kb.getCreatedBy().getUsername() : null);
        
        // 计算该知识库下的文档数和总大小
        long docCount = 0;
        long totalSize = 0;
        // 按kbId查询文档
        var filesByKbId = fileUploadRepository.findByKbId(kb.getKbId());
        if (!filesByKbId.isEmpty()) {
            docCount = filesByKbId.stream().filter(f -> f.getStatus() == 1).count();
            totalSize = filesByKbId.stream().filter(f -> f.getStatus() == 1).mapToLong(FileUpload::getTotalSize).sum();
        }
        dto.put("fileCount", docCount);
        dto.put("totalSize", totalSize);
        dto.put("chunkCount", Math.max(1, (int) Math.floor((double) totalSize / CHUNK_SIZE_BYTES)));
        dto.put("status", docCount > 0 ? "正常" : "空库");
        
        return dto;
    }
    
    /**
     * 获取知识库统计概览
     */
    public Map<String, Object> getKnowledgeBaseStats(String username, String orgTags) {
        List<Map<String, Object>> kbList = getAccessibleKnowledgeBases(username, orgTags);
        
        long totalDocs = kbList.stream().mapToLong(kb -> (Long) kb.get("fileCount")).sum();
        long totalSize = kbList.stream().mapToLong(kb -> (Long) kb.get("totalSize")).sum();
        long totalChunks = kbList.stream().mapToLong(kb -> (Integer) kb.get("chunkCount")).sum();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("knowledgeBaseCount", kbList.size());
        stats.put("documentCount", totalDocs);
        stats.put("totalSize", totalSize);
        stats.put("chunkCount", totalChunks);
        stats.put("knowledgeBases", kbList);
        
        return stats;
    }
    
    /**
     * 更新知识库（带权限校验）
     */
    @Transactional
    public KnowledgeBase updateKnowledgeBase(String kbId, String name, String description, 
                                              String orgTag, Boolean isPublic, String icon, 
                                              String operatorUsername, String operatorOrgTags) {
        LogUtils.logBusiness("UPDATE_KB", operatorUsername, "更新知识库: kbId=%s", kbId);
        
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByKbId(kbId);
        if (kbOpt.isEmpty()) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        
        KnowledgeBase kb = kbOpt.get();
        Set<String> userOrgTags = resolveUserOrgTags(operatorUsername, operatorOrgTags);
        if (isNotModifiable(kb, operatorUsername, userOrgTags)) {
            throw new SecurityException("无权修改该知识库: " + kbId);
        }
        
        if (name != null) kb.setName(name);
        if (description != null) kb.setDescription(description);
        if (orgTag != null) kb.setOrgTag(orgTag);
        if (isPublic != null) kb.setPublic(isPublic);
        if (icon != null) kb.setIcon(icon);
        
        return knowledgeBaseRepository.save(kb);
    }
    
    /**
     * 删除知识库（带权限校验）
     * 注意：删除知识库不会删除其中的文件，文件仍然保留在系统中
     */
    @Transactional
    public void deleteKnowledgeBase(String kbId, String operatorUsername, String operatorOrgTags) {
        LogUtils.logBusiness("DELETE_KB", operatorUsername, "删除知识库: kbId=%s", kbId);
        
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByKbId(kbId);
        if (kbOpt.isEmpty()) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        
        KnowledgeBase kb = kbOpt.get();
        Set<String> userOrgTags = resolveUserOrgTags(operatorUsername, operatorOrgTags);
        if (isNotModifiable(kb, operatorUsername, userOrgTags)) {
            throw new SecurityException("无权删除该知识库: " + kbId);
        }
        
        knowledgeBaseRepository.delete(kb);
    }
    
    /**
     * 根据kbId获取知识库详情（带访问控制）
     */
    public Map<String, Object> getKnowledgeBaseDetail(String kbId, String username, String orgTags) {
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByKbId(kbId);
        if (kbOpt.isEmpty()) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        
        KnowledgeBase kb = kbOpt.get();
        Set<String> userOrgTags = resolveUserOrgTags(username, orgTags);
        if (!isAccessible(kb, username, userOrgTags)) {
            throw new SecurityException("无权访问该知识库: " + kbId);
        }
        
        return buildKbDto(kb);
    }

    /**
     * 刷新知识库统计信息
     * 重新计算所有知识库的文档数、总大小、Chunk数等统计数据
     * 用于手动触发统计数据更新，确保统计数据与实际文件状态一致
     *
     * @param username 用户名
     * @param orgTags 用户组织标签
     * @return 刷新后的统计信息
     */
    public Map<String, Object> refreshKnowledgeBaseStats(String username, String orgTags) {
        LogUtils.logBusiness("REFRESH_KB_STATS", username, "刷新知识库统计信息");
        
        // 清除缓存中的有效标签，强制重新计算
        orgTagCacheService.invalidateAllEffectiveTagsCache();
        
        // 重新获取统计信息（已自动计算最新数据）
        Map<String, Object> stats = getKnowledgeBaseStats(username, orgTags);
        stats.put("refreshedAt", LocalDateTime.now());
        
        LogUtils.logBusiness("REFRESH_KB_STATS", username, "知识库统计信息刷新完成: kbCount=%d, docCount=%d", 
            stats.get("knowledgeBaseCount"), stats.get("documentCount"));
        
        return stats;
    }

    /**
     * 获取指定知识库下的文档列表
     * 优先按kbId检索文档，如果没有kbId关联的文档则按orgTag检索
     *
     * @param kbId 知识库ID
     * @param username 用户名（用于权限校验）
     * @param orgTags 用户组织标签（用于权限校验）
     * @return 文档列表
     */
    public List<Map<String, Object>> getKnowledgeBaseDocuments(String kbId, String username, String orgTags) {
        LogUtils.logBusiness("GET_KB_DOCUMENTS", username, "获取知识库文档: kbId=%s", kbId);
        
        // 检查知识库是否存在
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByKbId(kbId);
        if (kbOpt.isEmpty()) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        
        KnowledgeBase kb = kbOpt.get();
        
        // 权限校验
        Set<String> userOrgTags = new HashSet<>();
        if (orgTags != null && !orgTags.isEmpty()) {
            userOrgTags.addAll(Arrays.asList(orgTags.split(",")));
        }
        List<String> effectiveTags = orgTagCacheService.getUserEffectiveOrgTags(username);
        if (effectiveTags != null) {
            userOrgTags.addAll(effectiveTags);
        }
        
        if (!isAccessible(kb, username, userOrgTags)) {
            throw new IllegalArgumentException("无权访问该知识库: " + kbId);
        }
        
        // 按kbId检索文档
        List<FileUpload> files = fileUploadRepository.findByKbId(kbId);
        
        return files.stream().map(file -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", file.getId());
            dto.put("fileMd5", file.getFileMd5());
            dto.put("fileName", file.getFileName());
            dto.put("totalSize", file.getTotalSize());
            dto.put("status", file.getStatus());
            dto.put("userId", file.getUserId());
            dto.put("isPublic", file.isPublic());
            dto.put("createdAt", file.getCreatedAt());
            dto.put("mergedAt", file.getMergedAt());
            dto.put("orgTag", file.getOrgTag());
            dto.put("kbId", file.getKbId());
            return dto;
        }).toList();
    }

    /**
     * 获取筛选选项数据
     * 返回可用的组织标签列表、图标列表等，用于前端筛选下拉框
     *
     * @param username 用户名
     * @param orgTags 用户组织标签
     * @return 筛选选项数据
     */
    public Map<String, Object> getFilterOptions(String username, String orgTags) {
        LogUtils.logBusiness("GET_KB_FILTER_OPTIONS", username, "获取知识库筛选选项");
        
        List<Map<String, Object>> kbList = getAccessibleKnowledgeBases(username, orgTags);
        
        // 提取所有组织标签（去重）
        List<String> orgTagOptions = kbList.stream()
            .map(kb -> (String) kb.get("orgTag"))
            .filter(tag -> tag != null && !tag.isEmpty())
            .distinct()
            .sorted()
            .toList();
        
        // 提取所有创建者（去重）
        List<String> creatorOptions = kbList.stream()
            .map(kb -> (String) kb.get("createdBy"))
            .filter(creator -> creator != null && !creator.isEmpty())
            .distinct()
            .sorted()
            .toList();
        
        // 提取所有图标类型（去重）
        List<String> iconOptions = kbList.stream()
            .map(kb -> (String) kb.get("icon"))
            .filter(icon -> icon != null && !icon.isEmpty())
            .distinct()
            .sorted()
            .toList();
        
        // 公开状态选项
        List<Map<String, Object>> publicOptions = List.of(
            Map.of("label", "公开", "value", true),
            Map.of("label", "私有", "value", false)
        );
        
        // 时间范围选项
        List<Map<String, Object>> timeRangeOptions = List.of(
            Map.of("label", "近7天", "value", LocalDateTime.now().minusDays(7).toString()),
            Map.of("label", "近30天", "value", LocalDateTime.now().minusDays(30).toString()),
            Map.of("label", "近90天", "value", LocalDateTime.now().minusDays(90).toString())
        );

        // 文件类型选项（基于知识库下文件扩展名统计，按 kbId 优先，其次 orgTag，避免 N+1 查询）
        Set<String> fileTypeOptions = new TreeSet<>();

        // 收集所有相关的 kbId 和 orgTag（用于批量查询）
        Set<String> kbIds = new HashSet<>();
        Set<String> orgTagsForQuery = new HashSet<>();
        for (Map<String, Object> kb : kbList) {
            Object kbIdObj = kb.get("kbId");
            if (kbIdObj != null) {
                kbIds.add((String) kbIdObj);
            }
            String kbOrgTag = (String) kb.get("orgTag");
            if (kbOrgTag != null && !kbOrgTag.isEmpty()) {
                orgTagsForQuery.add(kbOrgTag);
            }
        }

        // 批量查询文件：先按 kbId，再补充按 orgTag
        List<FileUpload> relatedFiles = new ArrayList<>();
        if (!kbIds.isEmpty()) {
            relatedFiles.addAll(fileUploadRepository.findByKbIdIn(kbIds));
        }
        if (!orgTagsForQuery.isEmpty()) {
            relatedFiles.addAll(fileUploadRepository.findByOrgTagIn(orgTagsForQuery));
        }

        // 统计所有相关文件的扩展名
        for (FileUpload file : relatedFiles) {
            String fileName = file.getFileName();
            if (fileName != null && fileName.contains(".")) {
                String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase();
                fileTypeOptions.add(ext);
            }
        }
        
        Map<String, Object> options = new HashMap<>();
        options.put("orgTags", orgTagOptions);
        options.put("creators", creatorOptions);
        options.put("icons", iconOptions);
        options.put("publicOptions", publicOptions);
        options.put("timeRangeOptions", timeRangeOptions);
        options.put("fileTypes", fileTypeOptions.stream().toList());
        
        return options;
    }
}