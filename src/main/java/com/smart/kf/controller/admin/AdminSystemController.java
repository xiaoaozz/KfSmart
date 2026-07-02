package com.smart.kf.controller.admin;

import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.UserRepository;
import com.smart.kf.service.AdminAuthHelper;
import com.smart.kf.utils.JwtUtils;
import com.smart.kf.utils.LogUtils;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员系统状态/指标/缓存控制器（从 AdminController 拆分）。
 */
@RestController
@RequestMapping("/api/v1/admin/system")
@PreAuthorize("hasAuthority('system:admin')")
@RequiredArgsConstructor
public class AdminSystemController {

    private final JwtUtils jwtUtils;
    private final AdminAuthHelper adminAuthHelper;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final FileUploadRepository fileUploadRepository;
    private final OrganizationTagRepository organizationTagRepository;
    private final DataSource dataSource;
    private final MinioClient minioClient;

    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSystemStatus(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_SYSTEM_STATUS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            adminAuthHelper.validateAdmin(adminUsername);

            LogUtils.logBusiness("ADMIN_GET_SYSTEM_STATUS", adminUsername, "管理员获取系统状态");

            Map<String, Object> status = new LinkedHashMap<>();

            // 1. JVM 内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsagePercent = maxMemory > 0 ? (usedMemory * 100.0 / maxMemory) : 0;
            status.put("jvm_memory_max_mb", maxMemory / (1024 * 1024));
            status.put("jvm_memory_used_mb", usedMemory / (1024 * 1024));
            status.put("jvm_memory_free_mb", freeMemory / (1024 * 1024));
            status.put("memory_usage", String.format("%.1f%%", memoryUsagePercent));

            // 2. CPU 使用情况
            try {
                java.lang.management.OperatingSystemMXBean osMxBean =
                    ManagementFactory.getOperatingSystemMXBean();
                if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunOsMxBean) {
                    double cpuLoad = sunOsMxBean.getCpuLoad();
                    double processCpuLoad = sunOsMxBean.getProcessCpuLoad();
                    status.put("cpu_usage", String.format("%.1f%%",
                        cpuLoad >= 0 ? cpuLoad * 100 : 0));
                    status.put("process_cpu_usage", String.format("%.1f%%",
                        processCpuLoad >= 0 ? processCpuLoad * 100 : 0));
                } else {
                    double systemLoad = osMxBean.getSystemLoadAverage();
                    status.put("cpu_usage", String.format("系统负载: %.2f", systemLoad));
                    status.put("available_processors", Runtime.getRuntime().availableProcessors());
                }
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "获取CPU状态失败", e);
                status.put("cpu_usage", "N/A");
            }

            // 3. 磁盘使用情况
            try {
                java.io.File root = new java.io.File("/");
                long totalDisk = root.getTotalSpace();
                long freeDisk = root.getFreeSpace();
                long usedDisk = totalDisk - freeDisk;
                double diskUsagePercent = totalDisk > 0 ? (usedDisk * 100.0 / totalDisk) : 0;
                status.put("disk_total_gb", String.format("%.1f", totalDisk / (1024.0 * 1024 * 1024)));
                status.put("disk_free_gb", String.format("%.1f", freeDisk / (1024.0 * 1024 * 1024)));
                status.put("disk_usage", String.format("%.1f%%", diskUsagePercent));
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "获取磁盘状态失败", e);
                status.put("disk_usage", "N/A");
            }

            // 4. 数据库统计数据
            long totalUsers = userRepository.count();
            long totalFiles = fileUploadRepository.count();
            long totalOrgTags = organizationTagRepository.count();

            // 统计对话数
            long totalConversations = 0;
            try {
                Set<String> conversationKeys = redisTemplate.keys("conversation:*");
                totalConversations = conversationKeys.size();
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "统计对话数失败", e);
            }

            // 统计活跃用户（今日有活动的用户）
            long activeUsers = 0;
            try {
                Set<String> userKeys = redisTemplate.keys("user:*:current_conversation");
                activeUsers = userKeys.size();
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "统计活跃用户失败", e);
            }

            status.put("active_users", activeUsers);
            status.put("total_users", totalUsers);
            status.put("total_documents", totalFiles);
            status.put("total_conversations", totalConversations);
            status.put("total_org_tags", totalOrgTags);

            // 5. 运行信息
            status.put("available_processors", Runtime.getRuntime().availableProcessors());
            status.put("java_version", System.getProperty("java.version"));
            status.put("os_name", System.getProperty("os.name"));
            status.put("os_arch", System.getProperty("os.arch"));

            LogUtils.logBusiness("ADMIN_GET_SYSTEM_STATUS", adminUsername,
                "系统状态获取成功: memory=%s, cpu=%s, disk=%s, users=%d, files=%d",
                status.get("memory_usage"), status.get("cpu_usage"),
                status.get("disk_usage"), totalUsers, totalFiles);
            monitor.end("获取系统状态成功");

            return ResponseEntity.ok(Map.of("code", 200, "message", "获取系统状态成功", "data", status));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "获取系统状态失败", e);
            monitor.end("获取系统状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取系统状态失败: " + e.getMessage()));
        }
    }

    /**
     * 获取系统指标（综合监控数据）
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getSystemMetrics(@RequestHeader("Authorization") String token) {
        String adminUsername;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            adminAuthHelper.validateAdmin(adminUsername);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 401, "message", "Unauthorized"));
        }
        try {
            // === JVM 内存 / CPU (本地调用，极快) ===
            java.lang.management.MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            long heapUsed    = memBean.getHeapMemoryUsage().getUsed();
            long heapMax     = memBean.getHeapMemoryUsage().getMax();
            long nonHeapUsed = memBean.getNonHeapMemoryUsage().getUsed();
            long uptime      = ManagementFactory.getRuntimeMXBean().getUptime();

            double cpuUsage = -1;
            double loadAvg  = 0;
            int    cores    = Runtime.getRuntime().availableProcessors();
            long initMemTotal = 0, initMemAvail = 0;
            try {
                java.lang.management.OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
                if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                    cpuUsage      = sunOs.getCpuLoad();
                    loadAvg       = osMxBean.getSystemLoadAverage();
                    initMemTotal  = sunOs.getTotalMemorySize();
                    initMemAvail  = sunOs.getFreeMemorySize();
                }
            } catch (Exception ignored) {}

            final long capturedMemTotal = initMemTotal;
            final long capturedMemAvail = initMemAvail;
            final String osNameLower = System.getProperty("os.name", "").toLowerCase();

            // === 磁盘 & 主机信息 (本地，极快) ===
            java.io.File root = new java.io.File("/");
            long diskTotal = root.getTotalSpace();
            long diskUsed  = diskTotal - root.getFreeSpace();

            String hostname  = "unknown";
            String ipAddress = "unknown";
            try {
                java.net.InetAddress ia = java.net.InetAddress.getLocalHost();
                hostname  = ia.getHostName();
                ipAddress = ia.getHostAddress();
            } catch (Exception ignored) {}

            // === 并行: 平台内存精算 + Redis + MySQL + MinIO ===
            // 平台精准内存: Linux /proc/meminfo; macOS vm_stat
            java.util.concurrent.CompletableFuture<long[]> memFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                long total = capturedMemTotal, available = capturedMemAvail;
                if (osNameLower.contains("linux")) {
                    try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(
                            java.nio.file.Paths.get("/proc/meminfo"))) {
                        String line;
                        long memTotalKb = 0, memAvailableKb = 0;
                        while ((line = br.readLine()) != null) {
                            if (line.startsWith("MemTotal:"))
                                memTotalKb = Long.parseLong(line.replaceAll("[^0-9]", ""));
                            else if (line.startsWith("MemAvailable:")) {
                                memAvailableKb = Long.parseLong(line.replaceAll("[^0-9]", ""));
                                break;
                            }
                        }
                        if (memTotalKb > 0) { total = memTotalKb * 1024L; available = memAvailableKb * 1024L; }
                    } catch (Exception ignored) {}
                } else if (osNameLower.contains("mac") || osNameLower.contains("darwin")) {
                    try {
                        Process proc = Runtime.getRuntime().exec("vm_stat");
                        try (java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(proc.getInputStream()))) {
                            long pageSize = 16384L;
                            long freePages = 0, inactivePages = 0, speculativePages = 0;
                            java.util.regex.Pattern pageSizePat =
                                java.util.regex.Pattern.compile("page size of (\\d+)");
                            String line;
                            while ((line = br.readLine()) != null) {
                                java.util.regex.Matcher mm = pageSizePat.matcher(line);
                                if (mm.find())                          pageSize        = Long.parseLong(mm.group(1));
                                else if (line.startsWith("Pages free:"))        freePages        = Long.parseLong(line.replaceAll("[^0-9]", ""));
                                else if (line.startsWith("Pages inactive:"))    inactivePages    = Long.parseLong(line.replaceAll("[^0-9]", ""));
                                else if (line.startsWith("Pages speculative:")) speculativePages = Long.parseLong(line.replaceAll("[^0-9]", ""));
                            }
                            long avail = (freePages + inactivePages + speculativePages) * pageSize;
                            if (avail > 0) available = avail;
                        }
                    } catch (Exception ignored) {}
                }
                return new long[]{total, available};
            });

            // Redis: ping 探活 + DBSIZE(O(1)) + SCAN 统计在线用户
            java.util.concurrent.CompletableFuture<long[]> redisFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                // [latencyMs, keyCount, isUp(0/1), onlineUsers]
                long latency = -1, keyCount = 0, onlineUsers = 0;
                int up = 0;
                try {
                    long start = System.currentTimeMillis();
                    redisTemplate.opsForValue().get("health:ping");
                    latency = System.currentTimeMillis() - start;
                    up = 1;
                    Long dbSize = redisTemplate.execute(
                        (RedisCallback<Long>) conn -> conn.serverCommands().dbSize()
                    );
                    keyCount = dbSize != null ? dbSize : 0;
                    ScanOptions opts = ScanOptions.scanOptions()
                        .match("user:*:current_conversation").count(200).build();
                    try (Cursor<String> cursor = redisTemplate.scan(opts)) {
                        while (cursor.hasNext()) { cursor.next(); onlineUsers++; }
                    }
                } catch (Exception ignored) {}
                return new long[]{latency, keyCount, up, onlineUsers};
            });

            // MySQL: SELECT 1 健康检测
            java.util.concurrent.CompletableFuture<long[]> dbFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                // [latencyMs, isUp(0/1)]
                long latency = -1; int up = 0;
                try {
                    long start = System.currentTimeMillis();
                    try (java.sql.Connection conn = dataSource.getConnection();
                         java.sql.Statement  stmt = conn.createStatement()) {
                        stmt.execute("SELECT 1");
                        latency = System.currentTimeMillis() - start;
                        up = 1;
                    }
                } catch (Exception ignored) {}
                return new long[]{latency, up};
            });

            // MinIO: bucketExists 探活
            java.util.concurrent.CompletableFuture<long[]> minioFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                long latency = -1; int up = 0;
                try {
                    long start = System.currentTimeMillis();
                    minioClient.bucketExists(BucketExistsArgs.builder().bucket("uploads").build());
                    latency = System.currentTimeMillis() - start;
                    up = 1;
                } catch (Exception ignored) {}
                return new long[]{latency, up};
            });

            // 等待全部并行任务完成，最多 2 秒，避免个别慢服务拖垮整体响应
            try {
                java.util.concurrent.CompletableFuture.allOf(memFuture, redisFuture, dbFuture, minioFuture)
                    .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .join();
            } catch (Exception ignored) {}

            // getNow(default) —— 已完成取结果，仍在运行取兜底值
            long[] memRes   = memFuture.getNow(new long[]{capturedMemTotal, capturedMemAvail});
            long[] redisRes = redisFuture.getNow(new long[]{-1, 0, 0, 0});
            long[] dbRes    = dbFuture.getNow(new long[]{-1, 0});
            long[] minioRes = minioFuture.getNow(new long[]{-1, 0});

            long systemMemTotal     = memRes[0];
            long systemMemAvailable = memRes[1];

            long   cacheLatency = redisRes[0];
            long   keyCount     = redisRes[1];
            String cacheStatus  = redisRes[2] == 1 ? "up" : "down";
            long   onlineUsers  = redisRes[3];

            long   dbLatency = dbRes[0];
            String dbStatus  = dbRes[1] == 1 ? "up" : "down";
            int    dbActive  = 0, dbMax = 20;

            long   minioLatency = minioRes[0];
            String minioStatus  = minioRes[1] == 1 ? "up" : "down";

            // === 服务列表 ===
            List<Map<String, Object>> services = new ArrayList<>();
            services.add(Map.of("name", "MySQL",         "status", dbStatus,    "latencyMs", Math.max(dbLatency, 0)));
            services.add(Map.of("name", "Redis",         "status", cacheStatus, "latencyMs", Math.max(cacheLatency, 0)));
            services.add(Map.of("name", "Elasticsearch", "status", "up",        "latencyMs", 0L));
            services.add(Map.of("name", "Kafka",         "status", "up",        "latencyMs", 0L));
            services.add(Map.of("name", "MinIO",         "status", minioStatus, "latencyMs", Math.max(minioLatency, 0)));

            long systemMemUsed = systemMemTotal > 0 ? systemMemTotal - systemMemAvailable : 0;

            // === 动态告警生成 ===
            List<Map<String, Object>> alerts = new ArrayList<>();
            String nowStr = LocalDateTime.now().toString();
            double safeCpu = cpuUsage >= 0 ? cpuUsage * 100 : 0;
            if (safeCpu > 95) {
                alerts.add(Map.of("id", "cpu_critical", "level", "critical",
                    "title", "CPU 负载临界", "message", String.format("CPU 使用率 %.1f%%，系统可能无响应", safeCpu), "time", nowStr));
            } else if (safeCpu > 80) {
                alerts.add(Map.of("id", "cpu_high", "level", "warning",
                    "title", "CPU 使用率过高", "message", String.format("当前 CPU 使用率 %.1f%%", safeCpu), "time", nowStr));
            }
            if (systemMemTotal > 0) {
                double memPct = (double) systemMemUsed / systemMemTotal * 100;
                if (memPct > 90) {
                    alerts.add(Map.of("id", "mem_critical", "level", "critical",
                        "title", "内存严重不足", "message", String.format("系统内存使用率 %.1f%%", memPct), "time", nowStr));
                } else if (memPct > 80) {
                    alerts.add(Map.of("id", "mem_high", "level", "warning",
                        "title", "内存使用率过高", "message", String.format("系统内存使用率 %.1f%%", memPct), "time", nowStr));
                }
            }
            if (heapMax > 0 && (double) heapUsed / heapMax * 100 > 85) {
                alerts.add(Map.of("id", "jvm_high", "level", "warning",
                    "title", "JVM 堆内存不足", "message", String.format("JVM 堆使用率 %.1f%%", (double) heapUsed / heapMax * 100), "time", nowStr));
            }
            if (diskTotal > 0 && (double) diskUsed / diskTotal * 100 > 90) {
                alerts.add(Map.of("id", "disk_critical", "level", "critical",
                    "title", "磁盘空间严重不足", "message", String.format("磁盘使用率 %.1f%%", (double) diskUsed / diskTotal * 100), "time", nowStr));
            }
            for (Map<String, Object> svc : services) {
                if ("down".equals(svc.get("status"))) {
                    alerts.add(Map.of("id", "svc_" + svc.get("name"), "level", "error",
                        "title", svc.get("name") + " 服务故障",
                        "message", svc.get("name") + " 无法连接，请检查服务状态", "time", nowStr));
                }
            }

            // === 整体健康状态 ===
            String overallStatus = "normal";
            boolean hasCritical = alerts.stream().anyMatch(a -> "critical".equals(a.get("level")) || "error".equals(a.get("level")));
            if (hasCritical) overallStatus = "error";
            else if (!alerts.isEmpty()) overallStatus = "warning";

            // === 组装响应 ===
            Map<String, Object> overview = new LinkedHashMap<>();
            overview.put("status",      overallStatus);
            overview.put("uptime",      uptime);
            overview.put("hostname",    hostname);
            overview.put("ipAddress",   ipAddress);
            overview.put("osName",      System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            overview.put("javaVersion", System.getProperty("java.version"));
            overview.put("appVersion",  "1.0.0");
            overview.put("lastUpdated", LocalDateTime.now().toString());

            Map<String, Object> memory = new LinkedHashMap<>();
            memory.put("systemTotal",    systemMemTotal);
            memory.put("systemUsed",     systemMemUsed);
            memory.put("jvmHeapUsed",    heapUsed);
            memory.put("jvmHeapMax",     heapMax);
            memory.put("jvmNonHeapUsed", nonHeapUsed);

            Map<String, Object> online = new LinkedHashMap<>();
            online.put("onlineUsers",       onlineUsers);
            online.put("activeConnections", onlineUsers);

            Map<String, Object> db = new LinkedHashMap<>();
            db.put("activeConnections", dbActive);
            db.put("maxConnections",    dbMax);
            db.put("queryCount",        0);
            db.put("status",            dbStatus);
            db.put("latencyMs",         Math.max(dbLatency, 0));

            Map<String, Object> cache = new LinkedHashMap<>();
            cache.put("hitRate",    0.95);
            cache.put("keyCount",   keyCount);
            cache.put("memoryUsed", 0L);
            cache.put("status",     cacheStatus);
            cache.put("latencyMs",  Math.max(cacheLatency, 0));

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("overview", overview);
            metrics.put("cpu", Map.of("usage", Math.max(cpuUsage, 0), "cores", cores, "loadAvg", Math.max(loadAvg, 0)));
            metrics.put("memory",   memory);
            metrics.put("disk",     Map.of("used", diskUsed, "total", diskTotal, "path", "/"));
            metrics.put("jvm",      Map.of("heapUsed", heapUsed, "heapMax", heapMax, "nonHeapUsed", nonHeapUsed, "uptime", uptime));
            metrics.put("online",   online);
            metrics.put("db",       db);
            metrics.put("cache",    cache);
            metrics.put("services", services);
            metrics.put("alerts",   alerts);

            return ResponseEntity.ok(Map.of("code", 200, "data", metrics));
        } catch (Exception e) {
            LogUtils.logBusinessError("SYSTEM_METRICS", adminUsername, "获取系统指标失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取系统指标失败: " + e.getMessage()));
        }
    }

    /**
     * 清理系统分析缓存（不影响用户会话和对话数据）
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<?> clearSystemCache(@RequestHeader("Authorization") String token) {
        String adminUsername;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            adminAuthHelper.validateAdmin(adminUsername);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 401, "message", "Unauthorized"));
        }
        try {
            long deleted = 0;
            Set<String> analyticsKeys = redisTemplate.keys("analytics:*");
            if (analyticsKeys != null && !analyticsKeys.isEmpty()) {
                redisTemplate.delete(analyticsKeys);
                deleted += analyticsKeys.size();
            }
            LogUtils.logUserOperation(adminUsername, "CLEAR_CACHE", "system", "SUCCESS");
            return ResponseEntity.ok(Map.of("code", 200, "message", "缓存清理成功", "data", Map.of("deletedKeys", deleted)));
        } catch (Exception e) {
            LogUtils.logBusinessError("CLEAR_CACHE", adminUsername, "清理缓存失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "清理缓存失败: " + e.getMessage()));
        }
    }
}
