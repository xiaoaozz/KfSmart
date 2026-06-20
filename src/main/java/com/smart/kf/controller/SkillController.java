package com.smart.kf.controller;

import com.smart.kf.model.agent.SkillDefinition;
import com.smart.kf.service.agent.SkillService;
import com.smart.kf.utils.pagination.PageQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listSkills(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String cursor
    ) {
        return ok("获取技能列表成功", skillService.listSkills(keyword, category, status, PageQuery.of(page, size, cursor)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> skillStats() {
        return ok("获取技能统计成功", skillService.stats());
    }

    @GetMapping("/{skillId}")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> getSkill(@PathVariable String skillId) {
        return ok("获取技能详情成功", skillService.getSkill(skillId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> createSkill(@RequestBody SkillDefinition request) {
        return ok("保存技能成功", skillService.saveSkill(request, currentUsername()));
    }

    @PutMapping("/{skillId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> updateSkill(@PathVariable String skillId, @RequestBody SkillDefinition request) {
        request.setSkillId(skillId);
        return ok("保存技能成功", skillService.saveSkill(request, currentUsername()));
    }

    @PostMapping("/{skillId}/publish")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> publishSkill(@PathVariable String skillId) {
        return ok("发布技能成功", skillService.publishSkill(skillId));
    }

    @PutMapping("/{skillId}/toggle-status")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> toggleSkillStatus(@PathVariable String skillId) {
        return ok("切换技能状态成功", skillService.toggleSkillStatus(skillId));
    }

    @DeleteMapping("/{skillId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> deleteSkill(@PathVariable String skillId) {
        skillService.deleteSkill(skillId);
        return ok("删除技能成功", null);
    }

    @GetMapping("/{skillId}/histories")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listHistories(@PathVariable String skillId) {
        return ok("获取技能历史成功", skillService.getHistories(skillId));
    }

    @PostMapping("/{skillId}/rollback/{snapshotId}")
    @PreAuthorize("hasAuthority('agent:write')")
    public ResponseEntity<?> rollback(@PathVariable String skillId, @PathVariable Long snapshotId) {
        return ok("回滚技能成功", skillService.rollback(skillId, snapshotId, currentUsername()));
    }

    @PostMapping("/{skillId}/test")
    @PreAuthorize("hasAuthority('agent:run')")
    public ResponseEntity<?> testSkill(@PathVariable String skillId, @RequestBody(required = false) Map<String, Object> input) {
        return ok("技能试运行成功", skillService.testSkill(skillId, input == null ? Map.of() : input));
    }

    @GetMapping("/{skillId}/usages")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> listUsages(@PathVariable String skillId) {
        return ok("获取技能引用成功", skillService.listUsages(skillId));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private ResponseEntity<?> ok(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
