package com.smart.kf.controller.admin;

import com.smart.kf.service.I18nTranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员 i18n 同步相关接口。从原 AdminController 拆分而来，逻辑保持一致。
 */
@RestController
@RequestMapping("/api/v1/admin/i18n")
@PreAuthorize("hasAuthority('system:admin')")
@RequiredArgsConstructor
public class AdminI18nController {

    private final I18nTranslationService i18nTranslationService;

    /**
     * Trigger background translation of all KB / Agent / OrgTag entries that
     * have no i18n record yet.  Returns immediately; translation happens async.
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('system:admin')")
    public ResponseEntity<?> syncI18n() {
        i18nTranslationService.syncAllI18n();
        return ResponseEntity.ok(Map.of("code", 200, "message", "i18n sync started in background"));
    }
}
