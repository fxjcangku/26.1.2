package com.example.addon.itemid;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 物品 ID 配置管理核心：管理「已识别 / 手动添加」的物品身份集合。
 *
 * <p>这是「辅助」三功能里的第二环——ID 配置管理。本阶段改为「一物一文件」：
 * 每个 {@link ItemIdentity} 序列化为独立 JSON，写进客户端数据目录
 * {@code AutoChest/items/{中文名}.json}（重名自动追加 _2、_3），中文名只做
 * 文件名与显示，真正的身份判定以 itemId + 组件 + 附魔为准。</p>
 *
 * <p>关键约束：AutoChest 只「消费」这里的 ID 配置，绝不自己建立第二套物品数据库。</p>
 */
public final class ItemIdManager {

    private final Minecraft mc;
    private final Set<ItemIdentity> identities = new LinkedHashSet<>();

    /** 数据变更监听器：增删/清空/重载后通知（供 AutoChest 选择器实时联动） */
    private final List<Runnable> listeners = new ArrayList<>();

    public ItemIdManager() {
        this.mc = Minecraft.getInstance();
    }

    /** 物品身份根目录：{@code <客户端数据目录>/AutoChest/items} */
    private Path itemsDir() {
        return mc.gameDirectory.toPath().resolve("AutoChest").resolve("items");
    }

    /** 物品身份根目录（公开，供「打开物品ID目录」按钮使用） */
    public Path itemsDirectory() {
        return itemsDir();
    }

    /** 注册数据变更监听器（增删/清空/重载时触发） */
    public void addListener(Runnable listener) {
        if (listener != null) listeners.add(listener);
    }

    /** 按身份键查找完整 ItemIdentity（选择器数据源唯一，无副本） */
    public ItemIdentity findByKey(String key) {
        if (key == null) return null;
        for (ItemIdentity id : identities) {
            if (id.identityKey().equals(key)) return id;
        }
        return null;
    }

    /** 加载全部物品身份（切服 / 启动时调用） */
    public void reload() {
        identities.clear();
        Path dir = itemsDir();
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            for (Path file : stream.toList()) {
                if (!file.getFileName().toString().endsWith(".json")) continue;
                try {
                    JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    ItemIdentity id = ItemIdentity.fromJsonObject(obj);
                    if (id != null) identities.add(id);
                } catch (Exception ignored) {
                    // 单个文件损坏不阻塞整体加载
                }
            }
        } catch (Exception ignored) {
            // 目录读取失败视为无配置
        }
        notifyChanged();
    }

    /** 新增一个物品身份（内存去重 + 落盘为新文件），成功返回文件名（如 钻石.json），重复/无效返回 null */
    public String add(ItemIdentity identity) {
        if (identity == null) return null;
        if (!identities.add(identity)) return null;
        Path path = uniquePath(identity);
        writeFile(identity, path);
        notifyChanged();
        return path.getFileName().toString();
    }

    /** 删除一个物品身份（从内存移除后重写全部文件保持同步） */
    public boolean remove(ItemIdentity identity) {
        if (identity == null) return false;
        if (!identities.remove(identity)) return false;
        syncFiles();
        notifyChanged();
        return true;
    }

    /** 清空全部物品身份 */
    public void clear() {
        identities.clear();
        syncFiles();
        notifyChanged();
    }

    /** 全部身份的只读快照 */
    public Set<ItemIdentity> all() {
        return new LinkedHashSet<>(identities);
    }

    /** 是否存在任何身份 */
    public boolean hasAny() {
        return !identities.isEmpty();
    }

    public int size() {
        return identities.size();
    }

    // ── 持久化：一物一文件，中文文件名 + 重名追加 _N ──

    /** 生成不与现有文件冲突的保存路径（钻石.json → 钻石_2.json → 钻石_3.json） */
    private Path uniquePath(ItemIdentity identity) {
        String base = ItemIdentity.sanitizeFileName(identity.displayName(), identity.itemId());
        Path dir = itemsDir();
        Path candidate = dir.resolve(base + ".json");
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(base + "_" + n + ".json");
            n++;
        }
        return candidate;
    }

    private void writeFile(ItemIdentity identity, Path path) {
        try {
            Files.createDirectories(path.getParent());
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(identity.toJsonObject());
            Files.writeString(path, json);
        } catch (Exception ignored) {
            // 写失败不影响运行
        }
    }

    /** 删除目录下全部 json 后按当前内存集合重写，保证文件与内存一致 */
    private void syncFiles() {
        Path dir = itemsDir();
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
        for (ItemIdentity id : identities) {
            writeFile(id, uniquePath(id));
        }
    }

    /** 广播数据变更，触发 AutoChest 选择器等监听者实时联动（无需重启） */
    private void notifyChanged() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
                // 单个监听器异常不阻断其余监听器
            }
        }
    }
}
