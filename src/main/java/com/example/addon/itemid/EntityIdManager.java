package com.example.addon.itemid;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 实体 ID 配置管理核心：管理「已识别」的实体身份集合。
 *
 * <p>对应 {@code .id 实体} 识别功能，与物品身份对称，一实体一文件写进
 * {@code AutoChest/entities/{中文名}.json}（重名自动追加 _2、_3）。
 * 本阶段实体身份只做识别与归档，暂不参与自动箱子取物匹配。</p>
 */
public final class EntityIdManager {

    private final Minecraft mc;
    private final Set<EntityIdentity> identities = new LinkedHashSet<>();

    public EntityIdManager() {
        this.mc = Minecraft.getInstance();
    }

    /** 实体身份根目录：{@code <客户端数据目录>/AutoChest/entities} */
    private Path entitiesDir() {
        return mc.gameDirectory.toPath().resolve("AutoChest").resolve("entities");
    }

    /** 实体身份根目录（公开，供「打开实体ID目录」按钮使用） */
    public Path entitiesDirectory() {
        return entitiesDir();
    }

    /** 加载全部实体身份（启动时调用） */
    public void reload() {
        identities.clear();
        Path dir = entitiesDir();
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            for (Path file : stream.toList()) {
                if (!file.getFileName().toString().endsWith(".json")) continue;
                try {
                    JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    EntityIdentity id = EntityIdentity.fromJsonObject(obj);
                    if (id != null) identities.add(id);
                } catch (Exception ignored) {
                    // 单个文件损坏不阻塞整体加载
                }
            }
        } catch (Exception ignored) {
            // 目录读取失败视为无记录
        }
    }

    /** 新增一个实体身份（内存去重 + 落盘为新文件），成功返回文件名，重复/无效返回 null */
    public String add(EntityIdentity identity) {
        if (identity == null) return null;
        if (!identities.add(identity)) return null;
        Path path = uniquePath(identity);
        writeFile(identity, path);
        return path.getFileName().toString();
    }

    /** 清空全部实体身份 */
    public void clear() {
        identities.clear();
        syncFiles();
    }

    /** 全部实体身份的只读快照 */
    public Set<EntityIdentity> all() {
        return new LinkedHashSet<>(identities);
    }

    public boolean hasAny() {
        return !identities.isEmpty();
    }

    public int size() {
        return identities.size();
    }

    // ── 持久化：一实体一文件，中文文件名 + 重名追加 _N ──

    private Path uniquePath(EntityIdentity identity) {
        String base = ItemIdentity.sanitizeFileName(identity.displayName(), identity.entityId());
        Path dir = entitiesDir();
        Path candidate = dir.resolve(base + ".json");
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(base + "_" + n + ".json");
            n++;
        }
        return candidate;
    }

    private void writeFile(EntityIdentity identity, Path path) {
        try {
            Files.createDirectories(path.getParent());
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(identity.toJsonObject());
            Files.writeString(path, json);
        } catch (Exception ignored) {
            // 写失败不影响运行
        }
    }

    private void syncFiles() {
        Path dir = entitiesDir();
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    for (Path file : stream.toList()) {
                        if (file.getFileName().toString().endsWith(".json")) Files.deleteIfExists(file);
                    }
                }
            }
        } catch (Exception ignored) {
            // 删除失败不影响后续写入
        }
        for (EntityIdentity id : identities) {
            writeFile(id, uniquePath(id));
        }
    }
}
