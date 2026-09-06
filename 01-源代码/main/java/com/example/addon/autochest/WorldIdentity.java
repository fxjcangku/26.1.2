package com.example.addon.autochest;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

/**
 * 世界 / 服务器身份工具：统一「服务器 / 世界标识、维度标识、数据版本」的获取与中文显示。
 *
 * <p>服务器 / 维度隔离的核心判据都收敛在此，避免扫描器、记录管理器、标点管理器
 * 各写一套导致隔离失效。维度用 {@code minecraft:overworld} 这种稳定标识符，
 * 而不是 {@code ResourceKey.toString()} 的包装格式。</p>
 */
public final class WorldIdentity {

    private WorldIdentity() {
        // 工具类，禁止实例化
    }

    /** 当前服务器标识（多人取 IP 净化后字符串，单人世界统一为 singleplayer），用于文件隔离与去重 */
    public static String server(Minecraft mc) {
        if (mc.getCurrentServer() != null) {
            String ip = mc.getCurrentServer().ip;
            return ip == null ? "unknown" : ip.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        return "singleplayer";
    }

    /** 当前服务器显示名（多人取服务器名，单人世界显示「单人世界」），用于玩家可读提示 */
    public static String serverDisplayName(Minecraft mc) {
        if (mc.getCurrentServer() != null) {
            String name = mc.getCurrentServer().name;
            return (name == null || name.isBlank()) ? mc.getCurrentServer().ip : name;
        }
        return "单人世界";
    }

    /** 当前维度标识（如 {@code minecraft:overworld}）；世界未加载返回空串 */
    public static String dimension(Minecraft mc) {
        if (mc.level == null) return "";
        return mc.level.dimension().identifier().toString();
    }

    /**
     * 维度中文名：overworld → 主世界，nether → 下界，end → 末地，其余回退为原始标识。
     *
     * <p>用 contains 而非 equals，兼容历史上可能落盘的 {@code ResourceKey[...]} 包装格式。</p>
     */
    public static String dimensionDisplayName(String dim) {
        if (dim == null) return "未知维度";
        if (dim.contains("overworld")) return "主世界";
        if (dim.contains("nether")) return "下界";
        if (dim.contains("end")) return "末地";
        return dim;
    }

    /** 当前 Minecraft 数据版本（用于记录数据版本字段）；获取失败返回 0 */
    public static int dataVersion() {
        try {
            return SharedConstants.getCurrentVersion().dataVersion().version();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
