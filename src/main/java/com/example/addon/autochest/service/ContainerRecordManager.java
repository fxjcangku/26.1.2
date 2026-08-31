package com.example.addon.autochest.service;

import com.example.addon.autochest.WorldIdentity;
import com.example.addon.autochest.model.ContainerRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 已处理记录管理器：记录哪些容器坐标已经处理过，避免重复开箱。
 *
 * <p>数据持久化到客户端目录 {@code config/yiyiaddon/autochest/{server}.json}，
 * 每个玩家各自一份，不打包进 JAR。按服务器隔离，切服自动重载；单条记录内
 * 再以「维度 + 坐标 + 容器类型」做身份判定，保证跨维度 / 换类型不误判。</p>
 */
public final class ContainerRecordManager {

    private static final Path CONFIG_DIR = Paths.get("config", "yiyiaddon", "autochest");

    private final Minecraft mc;
    private final List<ContainerRecord> records = new ArrayList<>();

    public ContainerRecordManager(Minecraft mc) {
        this.mc = mc;
    }

    /** 当前服务器标识（单人世界为 singleplayer，多人取 IP 净化后字符串） */
    private String serverIdentifier() {
        return WorldIdentity.server(mc);
    }

    private Path dataFile() {
        return CONFIG_DIR.resolve(serverIdentifier() + ".json");
    }

    /** 切换服务器或启动时调用，加载对应服务器的记录 */
    public void reload() {
        records.clear();
        Path file = dataFile();
        if (!Files.exists(file)) return;
        try {
            parse(Files.readString(file));
        } catch (Exception ignored) {
            // 读取失败不阻塞模块，视为无记录
        }
    }

    /**
     * 某容器坐标是否已处理（且未过期）。
     *
     * <p>服务器隔离由文件路径保证；维度 + 坐标 + 容器类型在此逐一比对。
     * 若记录存在但容器类型不一致（原容器被换成另一种容器），视为未处理并失效旧记录。</p>
     */
    public boolean isProcessed(BlockPos pos, String dimension, String containerType, long expireMs) {
        ContainerRecord record = find(pos, dimension);
        if (record == null) return false;
        if (record.isExpired(expireMs)) {
            remove(pos, dimension);
            return false;
        }
        if (!record.sameType(containerType)) {
            remove(pos, dimension);
            return false;
        }
        return true;
    }

    /** 记录一个已处理容器并立即落盘 */
    public void markProcessed(BlockPos pos, String dimension, String containerType) {
        // 同一身份重复标记时先移除旧记录，避免列表内堆积
        remove(pos, dimension);
        records.add(new ContainerRecord(serverIdentifier(), pos, dimension, containerType));
        save();
    }

    /** 查找给定维度 + 坐标的记录（用于查看信息与失效判断），无则返回 null */
    public ContainerRecord find(BlockPos pos, String dimension) {
        for (ContainerRecord record : records) {
            if (record.pos().equals(pos) && record.dimension().equals(dimension)) {
                return record;
            }
        }
        return null;
    }

    /** 使某容器记录失效（容器被破坏 / 被换成其它类型时调用） */
    public boolean invalidate(BlockPos pos, String dimension) {
        return remove(pos, dimension);
    }

    private boolean remove(BlockPos pos, String dimension) {
        boolean removed = records.removeIf(r -> r.pos().equals(pos) && r.dimension().equals(dimension));
        if (removed) save();
        return removed;
    }

    /** 清空当前服务器的记录 */
    public void clear() {
        records.clear();
        save();
    }

    /** 清空当前服务器下指定维度的记录（维度 = {@code minecraft:overworld} 等） */
    public int clearDimension(String dimension) {
        int removed = records.size();
        records.removeIf(r -> r.dimension().equals(dimension));
        removed -= records.size();
        if (removed > 0) save();
        return removed;
    }

    /** 清空全部服务器的处理记录（删除记录目录下所有 json 文件） */
    public int clearAllServers() {
        int files = 0;
        try {
            if (Files.isDirectory(CONFIG_DIR)) {
                try (var stream = Files.list(CONFIG_DIR)) {
                    for (Path file : stream.toList()) {
                        if (file.getFileName().toString().endsWith(".json")) {
                            Files.deleteIfExists(file);
                            files++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 删除失败不影响后续
        }
        records.clear();
        return files;
    }

    public int size() {
        return records.size();
    }

    // ── 持久化：极简 JSON 手写，避免引入额外序列化依赖 ──

    private void save() {
        try {
            Path file = dataFile();
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < records.size(); i++) {
                ContainerRecord r = records.get(i);
                sb.append("  {\"x\":").append(r.pos().getX())
                  .append(",\"y\":").append(r.pos().getY())
                  .append(",\"z\":").append(r.pos().getZ())
                  .append(",\"dim\":\"").append(esc(r.dimension()))
                  .append("\",\"type\":\"").append(esc(r.containerType()))
                  .append("\",\"t\":").append(r.processedAt())
                  .append(",\"ver\":").append(r.dataVersion())
                  .append('}');
                if (i < records.size() - 1) sb.append(',');
                sb.append('\n');
            }
            sb.append("]");
            Files.writeString(file, sb.toString());
        } catch (Exception ignored) {
            // 写失败不影响运行
        }
    }

    private void parse(String json) {
        String server = serverIdentifier();
        Pattern obj = Pattern.compile("\\{([^}]*)\\}");
        Pattern field = Pattern.compile("\"(x|y|z|dim|type|t|ver)\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+))");
        Matcher m = obj.matcher(json);
        while (m.find()) {
            int x = 0, y = 0, z = 0, ver = 0;
            String dim = "";
            String type = "";
            long t = 0;
            Matcher fm = field.matcher(m.group(1));
            while (fm.find()) {
                String key = fm.group(1);
                String v = fm.group(2) != null ? fm.group(2) : fm.group(3);
                switch (key) {
                    case "x" -> x = Integer.parseInt(v);
                    case "y" -> y = Integer.parseInt(v);
                    case "z" -> z = Integer.parseInt(v);
                    case "dim" -> dim = v;
                    case "type" -> type = v;
                    case "t" -> t = Long.parseLong(v);
                    case "ver" -> ver = Integer.parseInt(v);
                }
            }
            records.add(new ContainerRecord(server, new BlockPos(x, y, z), dim, type,
                ContainerRecord.Status.PROCESSED, t, ver));
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
