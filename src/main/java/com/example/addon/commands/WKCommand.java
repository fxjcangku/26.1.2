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
 * WK 指令 - 自动挖矿点位管理指令
 * 
 * 功能范围：
 * · 绑定点位：.wk set 矿物箱 / 食物箱 / 挂机修复点
 * · 删除点位：.wk remove <目标>
 * · 清空全部：.wk clear
 * · 查看状态：.wk status
 * · 检测假矿：.wk checkfake（委托给 AutoMinerModule）
 * 
 * 设计特性：
 * 1. 数据持久化：绑定信息存储在 .minecraft/config/yiyiaddon/wk_data.json
 * 2. 多维度支持：记录坐标、维度ID、维度名（主世界/下界/末地）
 * 3. 容器检测：矿物箱和食物箱必须对准容器方块，否则拦截
 * 4. 视角保存：挂机修复点记录玩家站位 + 俯仰角/偏航角
 * 5. 覆盖保护：已有绑定不允许覆盖，必须先删除再设置
 * 
 * 配合模块按钮：
 * AutoMinerModule 配置页面的卡片按钮会直接调用 setBinding(key) 静态方法，
 * 实现按钮点击设置 + 指令输入设置两种方式共存。
 * 
 * 作者：参考 YiyiaddonModule.java 的开发规范编写
 */
public class WKCommand extends Command {

    private static final Path DATA_FILE = Paths.get("config", "yiyiaddon", "wk_data.json");
    private static final Map<String, WKData> DATA_STORE = new HashMap<>();

    static {
        loadData();
    }

    public WKCommand() {
        super("wk", "挖矿坐标绑定（矿物箱、食物箱、挂机修复点）");
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
        set.then(literal("挂机修复点").executes(ctx -> bindAFKPoint()));

        builder.then(set);

        // .wk remove <目标>
        LiteralArgumentBuilder<ClientSuggestionProvider> remove = literal("remove");

        remove.then(literal("矿物箱").executes(ctx -> unbind("mineral")));
        remove.then(literal("食物箱").executes(ctx -> unbind("food")));
        remove.then(literal("挂机修复点").executes(ctx -> unbind("afk")));

        builder.then(remove);

        // .wk clear
        builder.then(literal("clear").executes(ctx -> clearAll()));

        // .wk checkfake - 检测假矿
        builder.then(literal("checkfake").executes(ctx -> checkFakeOres()));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  绑定操作
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 绑定矿物箱
     * 准星对准箱子 → 检测容器类型 → 检测覆盖保护 → 记录坐标和维度
     */
    private int bindMineralChest() {
        // 覆盖保护：已有绑定必须先删除
        if (DATA_STORE.containsKey("mineral")) {
            wkError("矿物箱已绑定，请先删除旧绑定再重新设置");
            wkInfo("§7提示：使用 §e.wk remove 矿物箱 §7删除");
            return SINGLE_SUCCESS;
        }

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

    /**
     * 绑定食物箱
     * 准星对准箱子 → 检测容器类型 → 检测覆盖保护 → 记录坐标和维度
     */
    private int bindFoodChest() {
        // 覆盖保护：已有绑定必须先删除
        if (DATA_STORE.containsKey("food")) {
            wkError("食物箱已绑定，请先删除旧绑定再重新设置");
            wkInfo("§7提示：使用 §e.wk remove 食物箱 §7删除");
            return SINGLE_SUCCESS;
        }

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

    /**
     * 绑定挂机修复点
     * 记录玩家当前站位 + 视角（俯仰角 Pitch、偏航角 Yaw）
     * 挂机修复点不需要对准方块，直接记录玩家位置和朝向
     */
    private int bindAFKPoint() {
        // 覆盖保护：已有绑定必须先删除
        if (DATA_STORE.containsKey("afk")) {
            wkError("挂机修复点已绑定，请先删除旧绑定再重新设置");
            wkInfo("§7提示：使用 §e.wk remove 挂机修复点 §7删除");
            return SINGLE_SUCCESS;
        }

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
        wkInfo("  §d挂机修复点 §8→ §a" + data.describe());
        wkInfo("  §7视角：Yaw=" + String.format("%.1f", yaw) + "° Pitch=" + String.format("%.1f", pitch) + "°");
        wkInfo("");

        return SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  解绑操作
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 删除指定绑定（内部方法，通过 key 删除）
     * 供 clearAll() 和 removeBinding(String target) 调用
     */
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
            case "afk" -> "挂机修复点";
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
        showBinding("挂机修复点", "afk", "§d");

        wkInfo("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 维度 ID 转中文名
     * overworld → 主世界，nether → 下界，end → 末地
     */
    private String getDimensionName(String dimension) {
        if (dimension.contains("overworld")) return "主世界";
        if (dimension.contains("nether")) return "下界";
        if (dimension.contains("end")) return "末地";
        return dimension;
    }

    /**
     * 显示单个绑定的详细信息
     * 包含坐标、维度名、视角（如果是挂机修复点）
     */
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
    //  辅助方法：目标检测与容器判定
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 获取准星对准的方块位置
     * @return 方块坐标，未对准任何方块时返回 null
     */
    private BlockPos getTargetBlock() {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        if (!(hit instanceof BlockHitResult blockHit)) return null;
        return blockHit.getBlockPos().immutable();
    }

    /**
     * 检测指定方块是否为容器
     * 容器包括：箱子、桶、潜影盒、漏斗等实现了 Container 接口的方块实体
     */
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

    /**
     * 输出错误信息（红色前缀）
     */
    private void wkError(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
            YiyiaddonModule.formatMessage("自动挖矿", "§6§l" + message)));
    }

    // ═══════════════════════════════════════════════════════════════════
    //  公开方法（供模块调用）
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 获取绑定状态
     * @param key "mineral" / "food" / "afk"
     * @return true = 已绑定，false = 未绑定
     */
    public static boolean hasBinding(String key) {
        return DATA_STORE.containsKey(key);
    }

    /**
     * 获取绑定数据
     * @param key "mineral" / "food" / "afk"
     * @return WKData 或 null
     */
    public static WKData getBinding(String key) {
        return DATA_STORE.get(key);
    }

    /**
     * 设置绑定（供模块按钮调用）
     * @param key "mineral" / "food" / "afk"
     * @return true = 设置成功，false = 设置失败（需关闭GUI）
     */
    public static boolean setBinding(String key) {
        WKCommand cmd = new WKCommand();
        
        if ("mineral".equals(key)) {
            // 检查目标方块
            BlockPos target = cmd.getTargetBlock();
            if (target == null) {
                cmd.wkError("准星未对准任何方块，请重新设置");
                return false;
            }
            if (!cmd.isContainer(target)) {
                cmd.wkError("目标方块不是容器（箱子/桶/潜影盒等），请重新设置");
                return false;
            }
            cmd.bindMineralChest();
            return true;
            
        } else if ("food".equals(key)) {
            BlockPos target = cmd.getTargetBlock();
            if (target == null) {
                cmd.wkError("准星未对准任何方块，请重新设置");
                return false;
            }
            if (!cmd.isContainer(target)) {
                cmd.wkError("目标方块不是容器（箱子/桶/潜影盒等），请重新设置");
                return false;
            }
            cmd.bindFoodChest();
            return true;
            
        } else if ("afk".equals(key)) {
            cmd.bindAFKPoint();
            return true;
        }
        
        return false;
    }

    /**
     * 删除绑定（供模块按钮调用）
     * @param key "mineral" / "food" / "afk"
     */
    public static void removeBinding(String key) {
        if (DATA_STORE.remove(key) != null) {
            saveData();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  假矿检测：委托给 AutoMinerModule
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 检测假矿指令处理
     * 委托给 AutoMinerModule 的 checkFakeOres() 方法
     */
    private int checkFakeOres() {
        com.example.addon.modules.AutoMinerModule module = 
            meteordevelopment.meteorclient.systems.modules.Modules.get()
                .get(com.example.addon.modules.AutoMinerModule.class);

        if (module == null) {
            wkError("自动挖矿模块未加载");
            return SINGLE_SUCCESS;
        }

        module.checkFakeOres();
        return SINGLE_SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  数据持久化：JSON 读写
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 保存绑定数据到 JSON 文件
     * 格式：{ "mineral": {...}, "food": {...}, "afk": {...} }
     */
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

    /**
     * 从 JSON 文件加载绑定数据
     * 启动时自动调用（static 初始化块）
     */
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
