package com.example.addon.autochest.service;

import com.example.addon.autochest.model.WorldIdentity;
import com.example.addon.autochest.model.ChestTarget;
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
 * 标点管理器：管理用户手动保存的容器点位（标点模式专用）。
 *
 * <p>标点模式只处理这里保存的点位，不会因附近扫描到箱子而自动处理。
 * 每个点位保存服务器 / 维度 / 坐标 / 容器类型，内存与磁盘同步（增删清立即落盘）。
 * 持久化到 {@code config/yiyiaddon/autochest/points/{server}.json}，按服务器隔离。</p>
 */
public final class ChestPointManager {

    private static final Path CONFIG_DIR = Paths.get("config", "yiyiaddon", "autochest", "points");

    private final Minecraft mc;
    private final List<ChestTarget> points = new ArrayList<>();

    public ChestPointManager(Minecraft mc) {
        this.mc = mc;
    }

    private String serverIdentifier() {
        return WorldIdentity.server(mc);
    }

    private Path dataFile() {
        return CONFIG_DIR.resolve(serverIdentifier() + ".json");
    }

    /** 加载标点（切服 / 启动时调用） */
    public void reload() {
        points.clear();
        Path file = dataFile();
        if (!Files.exists(file)) return;
        try {
            parse(Files.readString(file));
        } catch (Exception ignored) {
            // 读取失败视为无标点
        }
    }

    /** 新增一个标点（含服务器 + 容器类型），已存在返回 false */
    public boolean add(BlockPos pos, String dimension, String containerType) {
        if (pos == null || dimension == null) return false;
        for (ChestTarget p : points) {
            if (p.pos().equals(pos) && p.dimension().equals(dimension)) return false; // 已存在
        }
        points.add(new ChestTarget(pos, dimension, serverIdentifier(), containerType));
        save();
        return true;
    }

    /** 删除一个标点（内存 + 磁盘同步） */
    public boolean remove(BlockPos pos, String dimension) {
        boolean removed = points.removeIf(p -> p.pos().equals(pos) && p.dimension().equals(dimension));
        if (removed) save();
        return removed;
    }

    /** 清空全部标点 */
    public void clear() {
        points.clear();
        save();
    }

    /** 查找指定维度 + 坐标的标点，无则返回 null */
    public ChestTarget find(BlockPos pos, String dimension) {
        for (ChestTarget p : points) {
            if (p.pos().equals(pos) && p.dimension().equals(dimension)) return p;
        }
        return null;
    }

    /** 当前维度的标点列表（只读快照） */
    public List<ChestTarget> pointsInCurrentDimension() {
        List<ChestTarget> result = new ArrayList<>();
        String currentDim = WorldIdentity.dimension(mc);
        if (currentDim.isEmpty()) return result;
        for (ChestTarget p : points) {
            if (p.dimension().equals(currentDim)) result.add(p);
        }
        return result;
    }

    public boolean hasPoints() {
        return !points.isEmpty();
    }

    public int size() {
        return points.size();
    }

    // ── 持久化：极简 JSON 手写 ──

    private void save() {
        try {
            Path file = dataFile();
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < points.size(); i++) {
                ChestTarget p = points.get(i);
                sb.append("  {\"x\":").append(p.pos().getX())
                  .append(",\"y\":").append(p.pos().getY())
                  .append(",\"z\":").append(p.pos().getZ())
                  .append(",\"dim\":\"").append(esc(p.dimension()))
                  .append("\",\"type\":\"").append(esc(p.containerType()))
                  .append("\"}");
                if (i < points.size() - 1) sb.append(',');
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
        Pattern field = Pattern.compile("\"(x|y|z|dim|type)\"\\s*:\\s*(?:\"([^\"]*)\"|(-?\\d+))");
        Matcher m = obj.matcher(json);
        while (m.find()) {
            int x = 0, y = 0, z = 0;
            String dim = "";
            String type = "";
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
                }
            }
            points.add(new ChestTarget(new BlockPos(x, y, z), dim, server, type));
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
