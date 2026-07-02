package com.smart.kf.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已拆分为 {@code com.smart.kf.controller.admin} 包下的 8 个域子控制器
 * （AdminUserController / AdminOrgTagController / AdminRoleController /
 * AdminKnowledgeController / AdminAnalyticsController / AdminSystemController /
 * AdminI18nController / AdminMigrationController）。
 * 保留此空壳类一个会话作为安全网，待全部端点迁移验证通过后删除。
 */
@RestController
@RequestMapping("/api/v1/admin-legacy-stub")
@PreAuthorize("hasAuthority('system:admin')")
public class AdminController {
}
