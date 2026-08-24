package com.example.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YiyiaddonWelcomeService {
    private static final String REPOSITORY_URL = "https://github.com/fxjcangku/26.1.2";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/fxjcangku/26.1.2/releases/latest";
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern RELEASE_URL_PATTERN = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final Minecraft mc = Minecraft.getInstance();
    private volatile ReleaseInfo latestRelease;
    private volatile boolean updateCheckStarted;

    public static void register() {
        MeteorClient.EVENT_BUS.subscribe(new YiyiaddonWelcomeService());
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (mc.player == null) return;

        mc.player.sendSystemMessage(Component.literal("本扩展已整合简体中文汉化跟汉化Baritone不用单独安装"));
        mc.player.sendSystemMessage(Component.literal("本扩展免费 为爱发电"));

        ReleaseInfo release = latestRelease;
        if (release != null) notifyUpdateIfNeeded(release);
        if (!updateCheckStarted) checkForUpdates();
    }

    private void checkForUpdates() {
        updateCheckStarted = true;
        HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "yiyiaddon-Update-Checker")
            .GET()
            .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                if (response.statusCode() != 200) return;
                ReleaseInfo release = parseRelease(response.body());
                if (release == null) return;
                latestRelease = release;
                mc.execute(() -> notifyUpdateIfNeeded(release));
            })
            .exceptionally(error -> null);
    }

    private void notifyUpdateIfNeeded(ReleaseInfo release) {
        if (mc.player == null) return;
        String currentVersion = FabricLoader.getInstance()
            .getModContainer("yiyiaddon")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("0");
        if (compareVersions(release.version(), currentVersion) <= 0) return;

        mc.player.sendSystemMessage(Component.literal(YiyiaddonModule.formatMessage(
            "更新提醒",
            "§e发现新版本 §a" + release.version() + " §7(当前 " + currentVersion + ") §f下载：§b" + release.url()
        )));
    }

    private static ReleaseInfo parseRelease(String json) {
        Matcher tagMatcher = TAG_PATTERN.matcher(json);
        Matcher urlMatcher = RELEASE_URL_PATTERN.matcher(json);
        if (!tagMatcher.find() || !urlMatcher.find()) return null;
        return new ReleaseInfo(tagMatcher.group(1), urlMatcher.group(1).replace("\\/", "/"));
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
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

    private record ReleaseInfo(String version, String url) {
    }
}
