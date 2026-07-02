package com.smart.kf.controller.admin;

import com.smart.kf.exception.CustomException;
import com.smart.kf.model.OrganizationTag;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.service.AdminAuthHelper;
import com.smart.kf.service.UserService;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 管理员组织标签管理控制器（从 AdminController 拆分而来）。
 * 原前缀 /api/v1/admin/org-tags 保持不变，各端点路径已相对新前缀调整，最终解析 URL 与原始一致。
 */
@RestController
@RequestMapping("/api/v1/admin/org-tags")
@PreAuthorize("hasAuthority('system:admin')")
@RequiredArgsConstructor
public class AdminOrgTagController {

    private final OrganizationTagRepository organizationTagRepository;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final AdminAuthHelper adminAuthHelper;

    /**
     * 创建组织标签
     */
    @PostMapping
    public ResponseEntity<?> createOrganizationTag(
            @RequestHeader("Authorization") String token,
            @RequestBody OrgTagRequest request) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);

        try {
            OrganizationTag tag = userService.createOrganizationTag(
                request.tagId(),
                request.name(),
                request.description(),
                request.parentTag(),
                adminUsername
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "组织标签创建成功", "data", tag));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", adminUsername, "创建组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", adminUsername, "创建组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建组织标签失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有组织标签
     */
    @GetMapping
    public ResponseEntity<?> getAllOrganizationTags(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);

        try {
            List<OrganizationTag> tags = organizationTagRepository.findAll().stream()
                .sorted(Comparator.comparing(OrganizationTag::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取组织标签成功",
                "data", PageResult.fromList(tags, PageQuery.of(page, size, cursor))
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAGS", adminUsername, "获取组织标签失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签失败: " + e.getMessage()));
        }
    }

    /**
     * 获取组织标签树结构
     */
    @GetMapping("/tree")
    public ResponseEntity<?> getOrganizationTagTree(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);

        try {
            List<Map<String, Object>> tagTree = userService.getOrganizationTagTree();
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取组织标签树成功",
                "data", tagTree
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAG_TREE", adminUsername, "获取组织标签树失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签树失败: " + e.getMessage()));
        }
    }

    /**
     * 更新组织标签
     */
    @PutMapping("/{tagId}")
    public ResponseEntity<?> updateOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId,
            @RequestBody OrgTagUpdateRequest request) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);

        try {
            OrganizationTag updatedTag = userService.updateOrganizationTag(
                tagId,
                request.name(),
                request.description(),
                request.parentTag(),
                adminUsername
            );
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "组织标签更新成功",
                "data", updatedTag
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", adminUsername, "更新组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", adminUsername, "更新组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新组织标签失败: " + e.getMessage()));
        }
    }

    /**
     * 删除组织标签
     */
    @DeleteMapping("/{tagId}")
    public ResponseEntity<?> deleteOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);

        try {
            Map<String, Object> result = userService.deleteOrganizationTag(tagId, adminUsername);
            int affectedUsers = (int) result.getOrDefault("affectedUserCount", 0);
            int affectedDocs = (int) result.getOrDefault("affectedDocumentCount", 0);
            int reassignedChildren = (int) result.getOrDefault("reassignedChildrenCount", 0);

            String message = "组织标签删除成功";
            if (affectedUsers > 0 || affectedDocs > 0 || reassignedChildren > 0) {
                message = String.format("组织标签删除成功（已自动处理：%d个子标签重新分配、%d个用户移除标签、%d个文档重新归属）",
                    reassignedChildren, affectedUsers, affectedDocs);
            }

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", message,
                "data", result
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", adminUsername, "删除组织标签失败: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", adminUsername, "删除组织标签异常: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除组织标签失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定组织标签的所有翻译
     */
    @GetMapping("/{tagId}/i18n")
    public ResponseEntity<?> getOrgTagI18n(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            return ResponseEntity.ok(Map.of("code", 200, "message", "ok", "data", userService.getOrganizationTagI18n(tagId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取翻译失败: " + e.getMessage()));
        }
    }

    /**
     * 保存或更新组织标签的某一语言翻译
     */
    @PutMapping("/{tagId}/i18n")
    public ResponseEntity<?> upsertOrgTagI18n(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId,
            @RequestBody OrgTagI18nRequest request) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        adminAuthHelper.validateAdmin(adminUsername);
        try {
            var saved = userService.upsertOrganizationTagI18n(tagId, request.lang(), request.name(), request.description());
            return ResponseEntity.ok(Map.of("code", 200, "message", "翻译已保存", "data", saved));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "保存翻译失败: " + e.getMessage()));
        }
    }

    /** 组织标签请求体 */
    public record OrgTagRequest(String tagId, String name, String description, String parentTag) {}

    /** 组织标签更新请求记录类 */
    public record OrgTagUpdateRequest(String name, String description, String parentTag) {}

    /** 组织标签 i18n 写入请求体 */
    public record OrgTagI18nRequest(String lang, String name, String description) {}
}
