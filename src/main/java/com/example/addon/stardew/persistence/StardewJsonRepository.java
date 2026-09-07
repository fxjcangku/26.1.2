package com.example.addon.stardew.persistence;

import com.example.addon.stardew.config.StardewConfig;
import com.example.addon.stardew.model.StardewServerProfile;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 星露谷服务器档案持久化仓库。
 *
 * <p>复用现有 Gson + 文件的持久化机制（与 ID 三件套同源），不另造 JSON 保存机制。
 * 每个服务器档案落盘为 {@code <客户端数据目录>/stardew/{档案ID}.json}，不同服务器互不串档。</p>
 */
public final class StardewJsonRepository {

    private final Minecraft mc;
    private StardewServerProfile current;

    public StardewJsonRepository() {
        this.mc = Minecraft.getInstance();
    }

    /** 档案根目录：{@code <客户端数据目录>/stardew} */
    private Path dir() {
        return mc.gameDirectory.toPath().resolve(StardewConfig.PROFILE_DIR);
    }

    public Path directory() {
        return dir();
    }

    /** 当前档案（内存缓存），未加载时返回 null */
    public StardewServerProfile current() {
        return current;
    }

    /** 列出已保存的档案 ID */
    public List<String> listProfiles() {
        List<String> result = new ArrayList<>();
        Path dir = dir();
        if (!Files.isDirectory(dir)) return result;
        try (var stream = Files.list(dir)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".json")) result.add(name.substring(0, name.length() - ".json".length()));
            }
        } catch (Exception ignored) {
            // 目录读取失败视为无档案
        }
        return result;
    }

    /** 按 ID 加载档案到内存并返回；文件不存在或损坏返回 null */
    public StardewServerProfile load(String profileId) {
        if (profileId == null || profileId.isBlank()) return null;
        Path file = dir().resolve(profileId + ".json");
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file);
            JsonObject obj = StardewJsonUtil.parseObject(json);
            StardewServerProfile profile = StardewServerProfile.fromJsonObject(obj);
            if (profile != null) current = profile;
            return profile;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 加载默认档案；不存在则返回 null */
    public StardewServerProfile loadDefault() {
        return load(StardewConfig.DEFAULT_PROFILE_ID);
    }

    /** 保存档案（同时更新内存引用），返回是否成功 */
    public boolean save(StardewServerProfile profile) {
        if (profile == null) return false;
        try {
            Files.createDirectories(dir());
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(profile.toJsonObject());
            Files.writeString(dir().resolve(profile.profileId() + ".json"), json);
            current = profile;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
