package com.example.addon.utils;

import com.example.addon.core.AddonTemplate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 遥测服务：崩溃监控 + 远程配置热更新 + 异常行为检测
 *
 * 1. 崩溃监控：安装全局未捕获异常钩子，按指纹聚合上报 /api/crash/report
 * 2. 远程配置：每分钟轮询 /api/config，后台配置实时生效（功能开关）
 * 3. 异常行为：每秒采样移动，检测高速移动 / 瞬移并上报 /api/anomaly/report
 */
public final class YiyiaddonTelemetryService {
    private static final String CONFIG_ENDPOINT = AddonTemplate.STATS_API_URL + "/api/config";
    private static final String CRASH_ENDPOINT = AddonTemplate.STATS_API_URL + "/api/crash/report";
    private static final String ANOMALY_ENDPOINT = AddonTemplate.STATS_API_URL + "/api/anomaly/report";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    // 远程配置缓存（key -> value）
    private static final Map<String, String> CONFIG = new ConcurrentHashMap<>();

    // 上报限流：同一指纹最小间隔（避免刷屏）
    private static final Map<String, Long> LAST_CRASH_REPORT = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_ANOMALY_REPORT = new ConcurrentHashMap<>();
    private static final long THROTTLE_MS = 5 * 60 * 1000L;

    private static volatile boolean started = false;

    // 移动采样状态
    private static double lastX, lastY, lastZ;
    private static long lastSampleAt;
    private static boolean hasLastPos = false;

    private YiyiaddonTelemetryService() {
    }

    public static void register() {
        if (started) return;
        started = true;
        installCrashHook();
        startBackgroundLoop();
        // 预热：启动后立即拉取一次配置，保证刚进世界时开关已生效
        new Thread(YiyiaddonTelemetryService::pollConfig, "yiyiaddon-config-init").start();
    }

    // ── 远程配置读取（供其他模块使用） ──

    /** 读取布尔开关，缺省视为开启 */
    public static boolean configEnabled(String key) {
        String v = CONFIG.get(key);
        return v == null || !"false".equalsIgnoreCase(v.trim());
    }

    /** 读取字符串配置，缺省返回 defaultValue */
    public static String configString(String key, String defaultValue) {
        String v = CONFIG.get(key);
        return v == null || v.isEmpty() ? defaultValue : v;
    }

    // ── 崩溃监控 ──

    private static void installCrashHook() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // 忽略本服务自身线程，避免上报循环
            if (thread != null && thread.getName().startsWith("yiyiaddon")) return;
            reportCrash(throwable);
        });
    }

    private static void reportCrash(Throwable t) {
        if (t == null) return;
        String message = t.getClass().getName() + ": " + (t.getMessage() == null ? "" : t.getMessage());
        long now = System.currentTimeMillis();
        Long last = LAST_CRASH_REPORT.get(message);
        if (last != null && now - last < THROTTLE_MS) return;
        LAST_CRASH_REPORT.put(message, now);

        String stack = stackTraceToString(t);
        String body = "{\"message\":\"" + jsonEscape(message) + "\",\"stack_trace\":\"" + jsonEscape(stack)
            + "\",\"version\":\"" + getAddonVersion() + "\",\"minecraft_version\":\"" + getMcVersion() + "\"}";
        sendPost(CRASH_ENDPOINT, body, "crash");
    }

    // ── 远程配置轮询 ──

    private static void pollConfig() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CONFIG_ENDPOINT))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            // 解析 {"config":{"key":"value",...}}
            String body = response.body();
            int start = body.indexOf("\"config\"");
            if (start < 0) return;
            int brace = body.indexOf('{', start);
            if (brace < 0) return;
            int end = body.indexOf('}', brace);
            if (end < 0) return;
            String configBlock = body.substring(brace + 1, end);

            // 逐项解析 "key":"value"
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(configBlock);
            while (m.find()) {
                CONFIG.put(m.group(1), unescapeJson(m.group(2)));
            }
        } catch (Exception ignored) {
        }
    }

    // ── 异常行为检测 ──

    private static void detectMovementAnomalies(Minecraft mc) {
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        long now = System.currentTimeMillis();
        if (!hasLastPos) {
            lastX = x; lastY = y; lastZ = z; lastSampleAt = now; hasLastPos = true;
            return;
        }
        double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
        double dt = Math.max(0.05, (now - lastSampleAt) / 1000.0);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double speed = horizontal / dt;
        double total = Math.sqrt(dx * dx + dy * dy + dz * dz);

        lastX = x; lastY = y; lastZ = z; lastSampleAt = now;

        if (speed > 50.0) {
            reportAnomaly("high_speed", "high",
                "高速移动 " + String.format("%.1f", speed) + " m/s",
                "horizontal=" + String.format("%.1f", horizontal) + ", dt=" + String.format("%.2f", dt));
        }
        if (total > 200.0) {
            reportAnomaly("teleport", "medium",
                "疑似瞬移 " + String.format("%.1f", total) + " 格",
                "dx=" + String.format("%.1f", dx) + ", dy=" + String.format("%.1f", dy) + ", dz=" + String.format("%.1f", dz));
        }
    }

    private static void reportAnomaly(String type, String severity, String message, String data) {
        long now = System.currentTimeMillis();
        Long last = LAST_ANOMALY_REPORT.get(type);
        if (last != null && now - last < THROTTLE_MS) return;
        LAST_ANOMALY_REPORT.put(type, now);

        Minecraft mc = Minecraft.getInstance();
        String uuid = mc.player != null ? mc.player.getUUID().toString() : null;
        String name = null;
        try { name = (mc.getUser() != null && mc.player != null) ? mc.getUser().getName() : null; } catch (Exception ignored) {}

        String body = "{\"type\":\"" + type + "\",\"severity\":\"" + severity + "\",\"message\":\"" + jsonEscape(message)
            + "\",\"data\":\"" + jsonEscape(data) + "\",\"uuid\":\"" + (uuid == null ? "" : uuid) + "\",\"name\":\"" + jsonEscape(name)
            + "\",\"version\":\"" + getAddonVersion() + "\",\"minecraft_version\":\"" + getMcVersion() + "\"}";
        sendPost(ANOMALY_ENDPOINT, body, "anomaly");
    }

    // ── 后台主循环 ──

    private static void startBackgroundLoop() {
        Thread t = new Thread(() -> {
            int counter = 0;
            while (true) {
                try {
                    Thread.sleep(1000);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) {
                        hasLastPos = false;
                        counter = 0;
                        continue;
                    }
                    detectMovementAnomalies(mc);
                    counter++;
                    if (counter >= 60) {
                        counter = 0;
                        pollConfig();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "yiyiaddon-telemetry");
        t.setDaemon(true);
        t.start();
    }

    // ── 工具 ──

    private static void sendPost(String endpoint, String body, String tag) {
        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                System.err.println("[YiyiaddonTelemetryService] " + tag + " 上报失败: " + e.getMessage());
            }
        }, "yiyiaddon-" + tag + "-report").start();
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private static String getAddonVersion() {
        try {
            return FabricLoader.getInstance()
                .getModContainer("yiyiaddon")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String getMcVersion() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.getVersionType();
        } catch (Exception e) {
            return "unknown";
        }
    }
}