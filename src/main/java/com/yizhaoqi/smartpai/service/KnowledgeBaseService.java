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

import java.util.*;

/**
 * 知识库服务
 * 提供知识库的创建、查询、更新、删除以及统计功能
 */
@Service
public class KnowledgeBaseService {
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;
    
    @Autowired
    private UserRepository userRepository;

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
     * 获取用户可访问的知识库列表
     * 包括：用户创建的、公开的、用户所属组织标签关联的
     */
    public List<Map<String, Object>> getAccessibleKnowledgeBases(String username, String orgTags) {
        LogUtils.logBusiness("GET_KB_LIST", username, "获取知识库列表: orgTags=%s", orgTags);
        
        List<KnowledgeBase> allKbs = knowledgeBaseRepository.findAll();
        
        // 解析用户的组织标签
        Set<String> userOrgTags = new HashSet<>();
        if (orgTags != null && !orgTags.isEmpty()) {
            userOrgTags.addAll(Arrays.asList(orgTags.split(",")));
        }
        
        // 过滤出用户可访问的知识库
        List<KnowledgeBase> accessibleKbs = allKbs.stream()
            .filter(kb -> isAccessible(kb, username, userOrgTags))
            .toList();
        
        // 构建返回数据（含统计信息）
        return accessibleKbs.stream().map(this::buildKbDto).toList();
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
        if (kb.getOrgTag() != null) {
            var files = fileUploadRepository.findByOrgTag(kb.getOrgTag());
            docCount = files.stream().filter(f -> f.getStatus() == 1).count();
            totalSize = files.stream().filter(f -> f.getStatus() == 1).mapToLong(FileUpload::getTotalSize).sum();
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
     * 更新知识库
     */
    @Transactional
    public KnowledgeBase updateKnowledgeBase(String kbId, String name, String description, 
                                              String orgTag, Boolean isPublic, String icon, 
                                              String operatorUsername) {
        LogUtils.logBusiness("UPDATE_KB", operatorUsername, "更新知识库: kbId=%s", kbId);
        
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByKbId(kbId);
        if (kbOpt.isEmpty()) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        
        KnowledgeBase kb = kbOpt.get();
        if (name != null) kb.setName(name);
        if (description != null) kb.setDescription(description);
        if (orgTag != null) kb.setOrgTag(orgTag);
        if (isPublic != null) kb.setPublic(isPublic);
        if (icon != null) kb.setIcon(icon);
        
        return knowledgeBaseRepository.save(kb);
    }
    
    /**
     * 删除知识库
     * 注意：删除知识库不会删除其中的文件，文件仍然保留在系统中
     */
    @Transactional
    public void deleteKnowledgeBase(String kbId, String operatorUsername) {
        LogUtils.logBusiness("DELETE_KB", operatorUsername, "删除知识库: kbId=%s", kbId);
        
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByKbId(kbId);
        if (kbOpt.isEmpty()) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        
        knowledgeBaseRepository.delete(kbOpt.get());
    }
    
    /**
     * 根据kbId获取知识库详情
     */
    public Map<String, Object> getKnowledgeBaseDetail(String kbId) {
        Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findByKbId(kbId);
        if (kbOpt.isEmpty()) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        
        return buildKbDto(kbOpt.get());
    }
}