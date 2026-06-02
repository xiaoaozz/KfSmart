package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.KnowledgeBaseService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 知识库控制器
 * 提供知识库的CRUD接口和统计接口
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
     * 获取用户可访问的知识库列表
     */
    @GetMapping
    public ResponseEntity<?> getKnowledgeBases(
            @RequestHeader("Authorization") String token) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_KB_LIST");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            String orgTags = jwtUtils.extractOrgTagsFromToken(token.replace("Bearer ", ""));
            
            LogUtils.logBusiness("GET_KB_LIST", username, "获取知识库列表");
            
            List<Map<String, Object>> kbList = knowledgeBaseService.getAccessibleKnowledgeBases(username, orgTags);
            
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
            
            LogUtils.logBusiness("GET_KB_DETAIL", username, "获取知识库详情: kbId=%s", kbId);
            
            Map<String, Object> detail = knowledgeBaseService.getKnowledgeBaseDetail(kbId);
            
            monitor.end("获取知识库详情成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取知识库详情成功", "data", detail));
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
            
            LogUtils.logBusiness("UPDATE_KB", username, "更新知识库: kbId=%s", kbId);
            
            var kb = knowledgeBaseService.updateKnowledgeBase(
                kbId,
                request.name(),
                request.description(),
                request.orgTag(),
                request.isPublic(),
                request.icon(),
                username
            );
            
            monitor.end("更新知识库成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "更新知识库成功", "data", kb));
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
            
            LogUtils.logBusiness("DELETE_KB", username, "删除知识库: kbId=%s", kbId);
            
            knowledgeBaseService.deleteKnowledgeBase(kbId, username);
            
            monitor.end("删除知识库成功");
            return ResponseEntity.ok(Map.of("code", 200, "message", "删除知识库成功"));
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
}