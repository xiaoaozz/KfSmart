package com.smart.kf.controller;

import com.smart.kf.service.runtime.RuntimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/runtime")
public class RuntimeController {

    private final RuntimeService runtimeService;

    public RuntimeController(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('agent:read')")
    public ResponseEntity<?> getCatalog() {
        return ok("获取运行目录成功", runtimeService.getCatalog());
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('agent:run')")
    public ResponseEntity<?> execute(@RequestBody Map<String, Object> request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        return ok("运行成功", runtimeService.execute(username, request));
    }

    private ResponseEntity<?> ok(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
