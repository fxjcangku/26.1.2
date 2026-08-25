package com.example.addon.commands;

import com.example.addon.core.YiyiaddonModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * WK 指令 - 纯客户端本地控制台
 * 
 * 功能：
 * · .wk set 矿物箱 / 食物箱 / 挂机点
 * · .wk remove <目标> / .wk clear
 * · .wk status
 * 
 * 数据持久化到 JSON：
 * .minecraft/config/yiyiaddon/wk_data.json
 */
public class WKCommand extends Command {

    private static final Path DATA_FILE = Paths.get("config", "yiyiaddon", "wk_data.json");
    private static final Map<String, WKData> DATA_STORE = new HashMap<>();

    static {
        loadData();
    }

    public WKCommand() {
        super("wk", "挖矿坐标绑定（矿物箱、食物箱、挂机点）");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // .wk status - 显示当前绑定状态
        builder.executes(ctx -> {
            showStatus();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("status").executes(ctx -> {
            showStatus();
            return SINGLE_SUCCESS;
        }));

        // .wk set <目标>
        LiteralArgumentBuilder<ClientSuggestionProvider> set = literal("set");

        set.then(literal("矿物箱").executes(ctx -> bindMineralChest()));
        set.then(literal("食物箱").executes(ctx -> bindFoodChest()));
        set.then(literal("挂机点").executes(ctx -> bindAFKPoint()));

        builder.then(set);

        // .wk remove <目标>
        LiteralArgumentBuilder<ClientSuggestionProvider> remove = literal("remove");

        remove.then(literal("矿物箱").executes(ctx -> unbind("mineral")));
        remove.then(literal("食物箱").executes(ctx -> unbind("food")));
        remove.then(literal("挂机点").executes(ctx -> unbind("afk")));

        builder.then(remove);

        // .wk clear
        builder.then(literal("clear").executes(ctx -> clearAll()));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  绑定操作
    // ═══════════════════════════════════════════════════════════════════

    private int bindMineralChest() {
        BlockPos target = getTargetBlock();
        if (target == null) {
            wkError("准星未对准任何方块");
            return SINGLE_SUCCESS;
        }

        if (!isContainer(target)) {
            wkError("目标方块不是容器（箱子/桶/潜影盒等）");
            return SINGLE_SUCCESS;
        }

        WKData data = WKData.here(target);
        if (data == null) {
            wkError("无法获取当前维度信息");
            return SINGLE_SUCCESS;
        }

        DATA_STORE.put("mineral", data);
        saveData();

        wkInfo("");
        wkInfo("§a§l✓ 绑定成功");
        wkInfo("  §6矿物箱 §8→ §a" + data.describe());
        wkInfo("");

        return SINGLE_SUCCESS;
    }

    private int bindFoodChest() {
        BlockPos target = getTargetBlock();
        if (target == null) {
            wkError("准星未对准任何方块");
            return SINGLE_SUCCESS;
        }

        if (!isContainer(target)) {
            wkError("目标方块不是容器（箱子/桶/潜影盒等）");
            return SINGLE_SUCCESS;
        }

        WKData data = WKData.here(target);
        if (data == null) {
            wkError("无法获取当前维度信息");
            return SINGLE_SUCCESS;
        }

        DATA_STORE.put("food", data);
        saveData();

        wkInfo("");
        wkInfo("§a§l✓ 绑定成功");
        wkInfo("  §2食物箱 §8→ §a" + data.describe());
        wkInfo("");

        return SINGLE_SUCCESS;
    }

    private int bindAFKPoint() {
        if (mc.player == null) {
            wkError("玩家不存在");
            return SINGLE_SUCCESS;
        }

        BlockPos pos = mc.player.blockPosition();
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        WKData data = WKData.hereWithView(pos, yaw, pitch);
        if (data == null) {
            wkError("无法获取当前维度信息");
            return SINGLE_SUCCESS;
        }

        DATA_STORE.put("afk", data);
        saveData();

        wkInfo("");
        wkInfo("§a§l✓ 绑定成功");
        wkInfo("  §d挂机点 §8→ §a" + data.describe());
        wkInfo("  §7视角：Yaw=" + String.format("%.1f", yaw) + "° Pitch=" + String.format("%.1f", pitch) + "°");
        wkInfo("");

        return SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  解绑操作
    // ═══════════════════════════════════════════════════════════════════

    private int unbind(String key) {
        if (!DATA_STORE.containsKey(key)) {
            wkError("该坐标本来就没有绑定");
            return SINGLE_SUCCESS;
        }

        DATA_STORE.remove(key);
        saveData();

        String name = switch (key) {
            case "mineral" -> "矿物箱";
            case "food" -> "食物箱";
            case "afk" -> "挂机点";
            default -> key;
        };

        wkInfo("§e已解绑 " + name);
        return SINGLE_SUCCESS;
    }

    private int clearAll() {
        int count = DATA_STORE.size();
        if (count == 0) {
            wkError("当前没有任何绑定");
            return SINGLE_SUCCESS;
        }

        DATA_STORE.clear();
        saveData();

        wkInfo("§e已清空全部绑定（共 " + count + " 个）");
        return SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  状态显示
    // ═══════════════════════════════════════════════════════════════════

    private void showStatus() {
        wkInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        wkInfo("§b§l         WK 坐标绑定状态");
        wkInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        wkInfo("");

        // 服务器信息
        String serverInfo = "§7单人世界";
        if (mc.getCurrentServer() != null) {
            serverInfo = "§f" + mc.getCurrentServer().ip;
        }
        wkInfo("  §7服务器: " + serverInfo);

        // 当前维度
        String currentDim = "§7未知";
        if (mc.level != null) {
            String dimId = mc.level.dimension().toString();
            currentDim = "§b" + getDimensionName(dimId);
        }
        wkInfo("  §7当前维度: " + currentDim);
        wkInfo("");

        showBinding("矿物箱", "mineral", "§6");
        showBinding("食物箱", "food", "§2");
        showBinding("挂机点", "afk", "§d");

        wkInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private String getDimensionName(String dimension) {
        if (dimension.contains("overworld")) return "主世界";
        if (dimension.contains("nether")) return "下界";
        if (dimension.contains("end")) return "末地";
        return dimension;
    }

    private void showBinding(String name, String key, String color) {
        WKData data = DATA_STORE.get(key);

        if (data == null) {
            wkInfo("  " + color + "■ §f§l" + name);
            wkInfo("    §8└─ §c未绑定");
        } else {
            wkInfo("  " + color + "■ §f§l" + name);
            wkInfo("    §8├─ §7坐标: §a" + data.pos.getX() + ", " + data.pos.getY() + ", " + data.pos.getZ());
            wkInfo("    §8├─ §7维度: §b" + data.dimensionName());
            if (data.yaw != 0 || data.pitch != 0) {
                wkInfo("    §8└─ §7视角: §eYaw=" + String.format("%.1f", data.yaw) + "° Pitch=" + String.format("%.1f", data.pitch) + "°");
            } else {
                wkInfo("    §8└─ §7类型: §e容器方块");
            }
        }
        wkInfo("");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════════

    private BlockPos getTargetBlock() {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!(hit instanceof BlockHitResult blockHit)) return null;
        return blockHit.getBlockPos().immutable();
    }

    private boolean isContainer(BlockPos pos) {
        if (mc.level == null) return false;
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        return blockEntity instanceof Container;
    }

    private void wkInfo(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
            YiyiaddonModule.formatMessage("自动挖矿", message)));
    }

    private void wkError(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
            YiyiaddonModule.formatMessage("自动挖矿", "§6§l" + message)));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  数据持久化
    // ═══════════════════════════════════════════════════════════════════

    private static void saveData() {
        try {
            Files.createDirectories(DATA_FILE.getParent());

            StringBuilder json = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<String, WKData> entry : DATA_STORE.entrySet()) {
                WKData data = entry.getValue();
                json.append("  \"").append(entry.getKey()).append("\": {\n");
                json.append("    \"x\": ").append(data.pos.getX()).append(",\n");
                json.append("    \"y\": ").append(data.pos.getY()).append(",\n");
                json.append("    \"z\": ").append(data.pos.getZ()).append(",\n");
                json.append("    \"dimension\": \"").append(data.dimension).append("\",\n");
                json.append("    \"yaw\": ").append(data.yaw).append(",\n");
                json.append("    \"pitch\": ").append(data.pitch).append("\n");
                json.append("  }");
                if (++i < DATA_STORE.size()) json.append(",");
                json.append("\n");
            }
            json.append("}");

            Files.writeString(DATA_FILE, json.toString());

        } catch (IOException e) {
            System.err.println("[WK] 保存数据失败: " + e.getMessage());
        }
    }

    private static void loadData() {
        if (!Files.exists(DATA_FILE)) return;

        try {
            String json = Files.readString(DATA_FILE);
            parseJson(json);

        } catch (IOException e) {
            System.err.println("[WK] 加载数据失败: " + e.getMessage());
        }
    }

    private static void parseJson(String json) {
        // 简化的 JSON 解析（生产环境建议用 Gson）
        json = json.replace("{", "").replace("}", "").replace("\"", "").trim();
        String[] entries = json.split(",(?=\\s*\\w+:)");

        String currentKey = null;
        int x = 0, y = 0, z = 0;
        String dimension = "";
        float yaw = 0, pitch = 0;

        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            String[] parts = entry.split(":", 2);
            if (parts.length != 2) continue;

            String key = parts[0].trim();
            String value = parts[1].trim();

            if (key.equals("mineral") || key.equals("food") || key.equals("afk")) {
                currentKey = key;
            } else if (currentKey != null) {
                switch (key) {
                    case "x" -> x = Integer.parseInt(value);
                    case "y" -> y = Integer.parseInt(value);
                    case "z" -> z = Integer.parseInt(value);
                    case "dimension" -> dimension = value;
                    case "yaw" -> yaw = Float.parseFloat(value);
                    case "pitch" -> {
                        pitch = Float.parseFloat(value);
                        // 读取完一组数据，存储
                        BlockPos pos = new BlockPos(x, y, z);
                        WKData data = new WKData(pos, dimension, yaw, pitch);
                        DATA_STORE.put(currentKey, data);
                        currentKey = null;
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  公共访问器
    // ═══════════════════════════════════════════════════════════════════

    public static WKData getMineralChest() {
        return DATA_STORE.get("mineral");
    }

    public static WKData getFoodChest() {
        return DATA_STORE.get("food");
    }

    public static WKData getAFKPoint() {
        return DATA_STORE.get("afk");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  WKData 数据类
    // ═══════════════════════════════════════════════════════════════════

    public static class WKData {
        public final BlockPos pos;
        public final String dimension;
        public final float yaw;
        public final float pitch;

        public WKData(BlockPos pos, String dimension, float yaw, float pitch) {
            this.pos = pos;
            this.dimension = dimension;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public static WKData here(BlockPos pos) {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return null;

            String dimension = mc.level.dimension().toString();
            return new WKData(pos, dimension, 0, 0);
        }

        public static WKData hereWithView(BlockPos pos, float yaw, float pitch) {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return null;

            String dimension = mc.level.dimension().toString();
            return new WKData(pos, dimension, yaw, pitch);
        }

        public boolean inCurrentDimension() {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return false;

            String currentDim = mc.level.dimension().toString();
            return currentDim.equals(dimension);
        }

        public String dimensionName() {
            if (dimension.contains("overworld")) return "主世界";
            if (dimension.contains("nether")) return "下界";
            if (dimension.contains("end")) return "末地";
            return dimension;
        }

        public String describe() {
            return String.format("(%d, %d, %d) @ %s",
                pos.getX(), pos.getY(), pos.getZ(), dimensionName());
        }
    }
}
