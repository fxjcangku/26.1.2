package com.example.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YiyiaddonWelcomeService {
    private static final String REPOSITORY_URL = "https://github.com/fxjcangku/26.1.2";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/fxjcangku/26.1.2/releases/latest";
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BODY_PATTERN = Pattern.compile("\"body\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RELEASE_URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    
    // 更新检查配置
    private static final long CHECK_INTERVAL_HOURS = 24; // 每天检查一次
    private static final Path LAST_CHECK_FILE = Paths.get(FabricLoader.getInstance().getConfigDir().toString(), "yiyiaddon-last-check.txt");
    private static final Path SKIP_VERSION_FILE = Paths.get(FabricLoader.getInstance().getConfigDir().toString(), "yiyiaddon-skip-version.txt");
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public static void register() {
        MeteorClient.EVENT_BUS.subscribe(YiyiaddonWelcomeService.class);
    }

    @EventHandler
    private static void onGameJoined(GameJoinedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 欢迎消息
        mc.player.sendSystemMessage(Component.literal("§c§l[yiyiaddon] §f本扩展已整合简体中文汉化跟汉化Baritone不用单独安装"));
        mc.player.sendSystemMessage(Component.literal("§c§l[yiyiaddon] §f本扩展免费 为爱发电"));

        // 检查是否需要更新检查（频率控制）
        if (!shouldCheckUpdate()) {
            return;
        }

        new Thread(() -> {
            try {
                String currentVersion = getCurrentVersion();
                ReleaseInfo latest = fetchLatestRelease();
                
                if (latest == null) return;
                
                // 检查是否跳过此版本
                String skipVersion = loadSkipVersion();
                if (skipVersion != null && skipVersion.equals(latest.version)) {
                    return;
                }

                int comparison = compareVersions(currentVersion, latest.version);
                
                if (comparison < 0) {
                    if (mc.player == null) return;
                    
                    // 更新提示（带预览）
                    mc.player.sendSystemMessage(Component.literal(
                        "§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    ));
                    mc.player.sendSystemMessage(Component.literal(
                        "§c§l[yiyiaddon] §f§l[更新提醒]"
                    ));
                    mc.player.sendSystemMessage(Component.literal(
                        "§f§l发现新版本 §a§l" + latest.version + "§r §7(当前 " + currentVersion + ")"
                    ));
                    
                    // 显示更新内容前 3 行
                    String preview = extractPreview(latest.body, 3);
                    if (!preview.isEmpty()) {
                        mc.player.sendSystemMessage(Component.literal(""));
                        mc.player.sendSystemMessage(Component.literal("§e§l更新内容："));
                        for (String line : preview.split("\n")) {
                            mc.player.sendSystemMessage(Component.literal("§f  " + line));
                        }
                    }
                    
                    mc.player.sendSystemMessage(Component.literal(""));
                    mc.player.sendSystemMessage(Component.literal(
                        "§b§l下载地址：§r§b§n" + latest.url
                    ));
                    mc.player.sendSystemMessage(Component.literal(
                        "§7提示：输入 §e§l.yiyiaddon skip §r§7跳过此版本"
                    ));
                    mc.player.sendSystemMessage(Component.literal(
                        "§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    ));
                }
                
                // 更新最后检查时间
                saveLastCheckTime();
                
            } catch (Exception e) {
                // 静默失败，不影响游戏体验
            }
        }, "yiyiaddon-update-checker").start();
    }

    private static boolean shouldCheckUpdate() {
        try {
            if (!Files.exists(LAST_CHECK_FILE)) {
                return true;
            }
            
            String content = Files.readString(LAST_CHECK_FILE);
            long lastCheck = Long.parseLong(content.trim());
            long now = Instant.now().getEpochSecond();
            long hoursSinceLastCheck = (now - lastCheck) / 3600;
            
            return hoursSinceLastCheck >= CHECK_INTERVAL_HOURS;
        } catch (Exception e) {
            return true; // 出错时允许检查
        }
    }

    private static void saveLastCheckTime() {
        try {
            Files.createDirectories(LAST_CHECK_FILE.getParent());
            Files.writeString(LAST_CHECK_FILE, String.valueOf(Instant.now().getEpochSecond()));
        } catch (IOException ignored) {
        }
    }

    public static void skipCurrentVersion(String version) {
        try {
            Files.createDirectories(SKIP_VERSION_FILE.getParent());
            Files.writeString(SKIP_VERSION_FILE, version);
        } catch (IOException ignored) {
        }
    }

    private static String loadSkipVersion() {
        try {
            if (!Files.exists(SKIP_VERSION_FILE)) {
                return null;
            }
            return Files.readString(SKIP_VERSION_FILE).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String extractPreview(String body, int maxLines) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        
        // 解码 JSON 转义
        body = body.replace("\\n", "\n")
                   .replace("\\r", "")
                   .replace("\\'", "'")
                   .replace("\\\"", "\"");
        
        // 提取前几行非空内容
        String[] lines = body.split("\n");
        StringBuilder preview = new StringBuilder();
        int count = 0;
        
        for (String line : lines) {
            line = line.trim();
            // 跳过 Markdown 标题、分隔线、空行
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("---") || line.startsWith("**")) {
                continue;
            }
            
            // 提取列表项或普通文本
            if (line.startsWith("-") || line.startsWith("*")) {
                line = line.substring(1).trim();
            }
            
            preview.append(line).append("\n");
            count++;
            
            if (count >= maxLines) {
                break;
            }
        }
        
        return preview.toString().trim();
    }

    private static String getCurrentVersion() {
        return FabricLoader.getInstance()
            .getModContainer("yiyiaddon")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    private static ReleaseInfo fetchLatestRelease() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LATEST_RELEASE_API))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) return null;

            String json = response.body();
            
            Matcher tagMatcher = TAG_PATTERN.matcher(json);
            Matcher urlMatcher = RELEASE_URL_PATTERN.matcher(json);
            Matcher bodyMatcher = BODY_PATTERN.matcher(json);
            
            if (!tagMatcher.find() || !urlMatcher.find()) return null;
            
            String version = normalizeVersion(tagMatcher.group(1));
            String url = urlMatcher.group(1).replace("\\/", "/");
            String body = bodyMatcher.find() ? bodyMatcher.group(1) : "";
            
            return new ReleaseInfo(version, url, body);
        } catch (Exception e) {
            return null;
        }
    }

    private static int compareVersions(String current, String latest) {
        String normCurrent = normalizeVersion(current);
        String normLatest = normalizeVersion(latest);
        
        String[] leftParts = normCurrent.split("\\.");
        String[] rightParts = normLatest.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        
        for (int i = 0; i < length; i++) {
            int leftPart = i < leftParts.length ? parseNumber(leftParts[i]) : 0;
            int rightPart = i < rightParts.length ? parseNumber(rightParts[i]) : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return 0;
    }

    private static String normalizeVersion(String version) {
        return version.replaceFirst("^[vV]", "").split("[-+]", 2)[0];
    }

    private static int parseNumber(String value) {
        Matcher matcher = Pattern.compile("^\\d+").matcher(value);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private record ReleaseInfo(String version, String url, String body) {
    }
}
