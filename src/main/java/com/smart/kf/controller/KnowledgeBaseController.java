package com.smart.kf.controller;

import com.smart.kf.service.KnowledgeBaseService;
import com.smart.kf.utils.pagination.PageQuery;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库控制器
 * 提供知识库的CRUD接口、统计接口、筛选接口和刷新接口
 * 知识库(KnowledgeBase)与组织标签(OrganizationTag)是独立的两个概念：
 * - 知识库：文档集合管理单元，用于分组管理文档
 * - 组织标签：权限控制单元，用于控制文档的访问权限
 * 知识库可以关联一个组织标签来实现权限控制
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 创建知识库
     */
    @PostMapping
    public ResponseEntity<?> createKnowledgeBase(
            @RequestHeader("Authorization") String token,
            @RequestBody CreateKbRequest request) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("CREATE_KB");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            LogUtils.logBusiness("CREATE_KB", username, "创建知识库请求: name=%s", request.name());
            
            var kb = knowledgeBaseService.createKnowledgeBase(
                request.name(),
                request.description(),
                request.orgTag(),
                request.isPublic(),
                request.icon(),
                username
            );
            
            monitor.end("知识库创建成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "知识库创建成功", "data", kb));
        } catch (IllegalArgumentException e) {
            LogUtils.logBusinessError("CREATE_KB", username, "创建知识库失败: %s", e, e.getMessage());
            monitor.end("创建失败: " + e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("CREATE_KB", username, "创建知识库异常", e);
            monitor.end("创建异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "创建知识库失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取用户可访问的知识库列表（支持筛选参数）
     * 筛选参数说明：
     * - keyword: 搜索关键字，匹配知识库名称和描述（模糊匹配）
     * - orgTag: 按组织标签筛选
     * - isPublic: 按公开状态筛选（true=仅公开，false=仅私有）
     * - createdBy: 按创建者筛选
     * - updatedAfter: 按更新时间筛选，格式为 ISO 8601（如 2025-01-01T00:00:00）
     */
    @GetMapping
    public ResponseEntity<?> getKnowledgeBases(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orgTag,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String updatedAfter,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_KB_LIST");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("GET_KB_LIST", username, "获取知识库列表: keyword=%s, orgTag=%s, isPublic=%s, createdBy=%s, updatedAfter=%s", 
                keyword, orgTag, isPublic, createdBy, updatedAfter);
            
            // 解析更新时间参数
            LocalDateTime filterUpdatedAfter = null;
            if (updatedAfter != null && !updatedAfter.isEmpty()) {
                try {
                    filterUpdatedAfter = LocalDateTime.parse(updatedAfter);
                } catch (Exception e) {
                    // 如果解析失败，尝试按天数解析（如 "7" 表示近7天）
                    try {
                        long days = Long.parseLong(updatedAfter);
                        filterUpdatedAfter = LocalDateTime.now().minusDays(days);
                    } catch (NumberFormatException nfe) {
                        LogUtils.logBusiness("GET_KB_LIST", username, "更新时间参数格式错误: %s", updatedAfter);
                    }
                }
            }
            
            var kbList = knowledgeBaseService.getAccessibleKnowledgeBasesPage(
                username, orgTags, keyword, orgTag, isPublic, createdBy, filterUpdatedAfter, PageQuery.of(page, size, cursor));
            
            monitor.end("获取知识库列表成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取知识库列表成功", "data", kbList));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_KB_LIST", username, "获取知识库列表异常", e);
            monitor.end("获取异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "获取知识库列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取知识库统计概览
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getKnowledgeBaseStats(
            @RequestHeader("Authorization") String token) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_KB_STATS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("GET_KB_STATS", username, "获取知识库统计");
            
            Map<String, Object> stats = knowledgeBaseService.getKnowledgeBaseStats(username, orgTags);
            
            monitor.end("获取知识库统计成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取知识库统计成功", "data", stats));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_KB_STATS", username, "获取知识库统计异常", e);
            monitor.end("获取异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "获取知识库统计失败: " + e.getMessage()));
        }
    }

    /**
     * 获取知识库筛选选项
     * 返回可用的组织标签列表、创建者列表、图标类型、公开状态选项等
     * 用于前端筛选下拉框的数据源
     */
    @GetMapping("/filter-options")
    public ResponseEntity<?> getFilterOptions(
            @RequestHeader("Authorization") String token) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_KB_FILTER_OPTIONS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("GET_KB_FILTER_OPTIONS", username, "获取知识库筛选选项");
            
            Map<String, Object> options = knowledgeBaseService.getFilterOptions(username, orgTags);
            
            monitor.end("获取知识库筛选选项成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取知识库筛选选项成功", "data", options));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_KB_FILTER_OPTIONS", username, "获取知识库筛选选项异常", e);
            monitor.end("获取异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "获取知识库筛选选项失败: " + e.getMessage()));
        }
    }

    /**
     * 刷新知识库统计信息
     * 手动触发统计数据的重新计算，确保统计数据与实际文件状态一致
     * 清除相关缓存并重新计算所有知识库的文档数、总大小、Chunk数等
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshKnowledgeBaseStats(
            @RequestHeader("Authorization") String token) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("REFRESH_KB_STATS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("REFRESH_KB_STATS", username, "刷新知识库统计信息");
            
            Map<String, Object> stats = knowledgeBaseService.refreshKnowledgeBaseStats(username, orgTags);
            
            monitor.end("刷新知识库统计成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "知识库统计信息已刷新", "data", stats));
        } catch (Exception e) {
            LogUtils.logBusinessError("REFRESH_KB_STATS", username, "刷新知识库统计异常", e);
            monitor.end("刷新异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "刷新知识库统计失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取知识库详情
     */
    @GetMapping("/{kbId}")
    public ResponseEntity<?> getKnowledgeBaseDetail(
            @RequestHeader("Authorization") String token,
            @PathVariable String kbId) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_KB_DETAIL");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("GET_KB_DETAIL", username, "获取知识库详情: kbId=%s", kbId);
            
            Map<String, Object> detail = knowledgeBaseService.getKnowledgeBaseDetail(kbId, username, orgTags);
            
            monitor.end("获取知识库详情成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取知识库详情成功", "data", detail));
        } catch (SecurityException e) {
            LogUtils.logBusinessError("GET_KB_DETAIL", username, "知识库访问权限不足: %s", e, e.getMessage());
            monitor.end("权限不足: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", 403, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            LogUtils.logBusinessError("GET_KB_DETAIL", username, "获取知识库详情失败: %s", e, e.getMessage());
            monitor.end("获取失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", 404, "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_KB_DETAIL", username, "获取知识库详情异常", e);
            monitor.end("获取异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "获取知识库详情失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新知识库
     */
    @PutMapping("/{kbId}")
    public ResponseEntity<?> updateKnowledgeBase(
            @RequestHeader("Authorization") String token,
            @PathVariable String kbId,
            @RequestBody UpdateKbRequest request) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("UPDATE_KB");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("UPDATE_KB", username, "更新知识库: kbId=%s", kbId);
            
            var kb = knowledgeBaseService.updateKnowledgeBase(
                kbId,
                request.name(),
                request.description(),
                request.orgTag(),
                request.isPublic(),
                request.icon(),
                username,
                orgTags
            );
            
            monitor.end("更新知识库成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "更新知识库成功", "data", kb));
        } catch (SecurityException e) {
            LogUtils.logBusinessError("UPDATE_KB", username, "知识库修改权限不足: %s", e, e.getMessage());
            monitor.end("权限不足: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", 403, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            LogUtils.logBusinessError("UPDATE_KB", username, "更新知识库失败: %s", e, e.getMessage());
            monitor.end("更新失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", 404, "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("UPDATE_KB", username, "更新知识库异常", e);
            monitor.end("更新异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "更新知识库失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除知识库
     */
    @DeleteMapping("/{kbId}")
    public ResponseEntity<?> deleteKnowledgeBase(
            @RequestHeader("Authorization") String token,
            @PathVariable String kbId) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DELETE_KB");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("DELETE_KB", username, "删除知识库: kbId=%s", kbId);
            
            knowledgeBaseService.deleteKnowledgeBase(kbId, username, orgTags);
            
            monitor.end("删除知识库成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "删除知识库成功"));
        } catch (SecurityException e) {
            LogUtils.logBusinessError("DELETE_KB", username, "知识库删除权限不足: %s", e, e.getMessage());
            monitor.end("权限不足: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", 403, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            LogUtils.logBusinessError("DELETE_KB", username, "删除知识库失败: %s", e, e.getMessage());
            monitor.end("删除失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", 404, "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("DELETE_KB", username, "删除知识库异常", e);
            monitor.end("删除异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "删除知识库失败: " + e.getMessage()));
        }
    }
    
    /** 创建知识库请求体 */
    public record CreateKbRequest(
        String name,
        String description,
        String orgTag,
        boolean isPublic,
        String icon
    ) {}
    
    /** 更新知识库请求体 */
    public record UpdateKbRequest(
        String name,
        String description,
        String orgTag,
        Boolean isPublic,
        String icon
    ) {}

    /**
     * 获取指定知识库下的文档列表
     * 根据知识库的kbId检索关联的文档
     *
     * @param token JWT token
     * @param kbId 知识库ID
     * @return 该知识库下的文档列表
     */
    @GetMapping("/{kbId}/documents")
    public ResponseEntity<?> getKnowledgeBaseDocuments(
            @RequestHeader("Authorization") String token,
            @PathVariable String kbId) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_KB_DOCUMENTS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("GET_KB_DOCUMENTS", username, "获取知识库文档: kbId=%s", kbId);
            
            List<Map<String, Object>> documents = knowledgeBaseService.getKnowledgeBaseDocuments(kbId, username, orgTags);
            
            monitor.end("获取知识库文档成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取知识库文档成功", "data", documents));
        } catch (IllegalArgumentException e) {
            LogUtils.logBusinessError("GET_KB_DOCUMENTS", username, "获取知识库文档失败: %s", e, e.getMessage());
            monitor.end("获取失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", 404, "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_KB_DOCUMENTS", username, "获取知识库文档异常", e);
            monitor.end("获取异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", 500, "message", "获取知识库文档失败: " + e.getMessage()));
        }
    }
}
