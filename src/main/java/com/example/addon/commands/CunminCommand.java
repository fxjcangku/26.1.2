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
 * Cunmin 指令 - 自动村民交易点位管理
 * 
 * 功能：
 * · .cunmin set 绿宝石箱   准星指向箱子绑定
 * · .cunmin set 成品交易箱     准星指向箱子绑定
 * · .cunmin remove <目标>  删除绑定
 * · .cunmin clear          清空全部
 * · .cunmin status         查看状态
 * 
 * 数据持久化：.minecraft/config/yiyiaddon/cunmin/<服务器>.json
 * 按服务器分文件存储，支持多服务器切换。
 */
public class CunminCommand extends Command {

    private static final Path CONFIG_DIR = Paths.get("config", "yiyiaddon", "cunmin");
    private static final Map<String, CunminData> DATA_STORE = new HashMap<>();
    
    static {
        loadData();
    }

    public CunminCommand() {
        super("cunmin", "村民交易点位绑定（绿宝石箱、成品交易箱）");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        // .cunmin 或 .cunmin status - 显示当前绑定状态
        builder.executes(ctx -> {
            showStatus();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("status").executes(ctx -> {
            showStatus();
            return SINGLE_SUCCESS;
        }));

        // .cunmin set <目标>
        LiteralArgumentBuilder<ClientSuggestionProvider> set = literal("set");

        set.then(literal("绿宝石箱").executes(ctx -> bindEmeraldChest()));
        set.then(literal("成品交易箱").executes(ctx -> bindUnloadChest()));

        builder.then(set);

        // .cunmin remove <目标>
        LiteralArgumentBuilder<ClientSuggestionProvider> remove = literal("remove");

        remove.then(literal("绿宝石箱").executes(ctx -> removeBindingCommand("绿宝石箱")));
        remove.then(literal("成品交易箱").executes(ctx -> removeBindingCommand("成品交易箱")));

        builder.then(remove);

        // .cunmin clear - 清空全部绑定
        builder.then(literal("clear").executes(ctx -> {
            clearAllBindings();
            return SINGLE_SUCCESS;
        }));
    }

    /**
     * 绑定绿宝石箱
     */
    private int bindEmeraldChest() {
        return bindContainer("绿宝石箱", "emerald_chest");
    }

    /**
     * 绑定成品交易箱
     */
    private int bindUnloadChest() {
        return bindContainer("成品交易箱", "unload_chest");
    }

    /**
     * 通用容器绑定逻辑
     */
    private int bindContainer(String displayName, String key) {
        if (mc.player == null || mc.level == null) {
            error("§c玩家或世界无效");
            return SINGLE_SUCCESS;
        }

        // 获取准星指向方块
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            error("§c请将准星对准容器方块");
            return SINGLE_SUCCESS;
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);

        // 验证是否为有效容器
        if (!(blockEntity instanceof Container)) {
            error("§c目标方块不是有效的容器（箱子/木桶/潜影盒）");
            return SINGLE_SUCCESS;
        }

        // 保存绑定
        CunminData data = getData();
        ResourceKey<Level> dimension = mc.level.dimension();
        
        data.setBinding(key, pos, dimension);
        saveData();

        // 成功提示
        String dimName = getDimensionName(dimension);
        info("§a§l✓ 绑定成功");
        String color = key.equals("emerald_chest") ? "§a" : "§6";
        info(String.format("  %s%s §8▸ §7X§f%d §7Y§f%d §7Z§f%d §8▸ %s%s§r",
            color, displayName, pos.getX(), pos.getY(), pos.getZ(), color, dimName));

        return SINGLE_SUCCESS;
    }

    /**
     * 删除绑定（私有方法，给指令用）
     */
    private int removeBindingCommand(String displayName) {
        String key = displayName.equals("绿宝石箱") ? "emerald_chest" : "unload_chest";
        
        CunminData data = getData();
        if (!data.hasBinding(key)) {
            error(String.format("%s 未绑定", displayName));
            return SINGLE_SUCCESS;
        }

        data.removeBinding(key);
        saveData();

        info(String.format("§c§l✗ %s 已删除", displayName));
        return SINGLE_SUCCESS;
    }

    /**
     * 清空全部绑定
     */
    private void clearAllBindings() {
        CunminData data = getData();
        data.clear();
        saveData();

        info("§c§l✗ 已清空全部绑定");
    }

    /**
     * 显示当前绑定状态
     */
    private void showStatus() {
        CunminData data = getData();

        info("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        info("§b§l       自动村民交易 ▸ 坐标绑定");
        info("§b§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        info("");

        showBindingStatus(data, "绿宝石箱", "emerald_chest", "§a");
        showBindingStatus(data, "成品交易箱", "unload_chest", "§6");

        if (!data.hasAnyBinding()) {
            info("§7暂无绑定点位");
            info("§7使用 §e.cunmin set <目标> §7进行绑定");
        }
    }

    /**
     * 显示单个绑定状态
     */
    private void showBindingStatus(CunminData data, String displayName, String key, String color) {
        if (!data.hasBinding(key)) {
            info(String.format("  %s■ §f§l%s §8▸ §c未绑定", color, displayName));
            return;
        }

        BlockPos pos = data.getPos(key);
        ResourceKey<Level> dimension = data.getDimension(key);
        String dimName = getDimensionName(dimension);

        info(String.format("  %s■ §f§l%s", color, displayName));
        info(String.format("    §8├─ §7坐标 ▸ §7X§f%d §7Y§f%d §7Z§f%d",
            pos.getX(), pos.getY(), pos.getZ()));
        info(String.format("    §8└─ §7维度 ▸ §b%s", dimName));
    }

    /**
     * 获取维度显示名称
     */
    private static String getDimensionName(ResourceKey<Level> dimension) {
        if (dimension == null) return "§7未知";
        
        String id = dimension.toString();
        return switch (id) {
            case "ResourceKey[minecraft:dimension / minecraft:overworld]" -> "主世界";
            case "ResourceKey[minecraft:dimension / minecraft:the_nether]" -> "下界";
            case "ResourceKey[minecraft:dimension / minecraft:the_end]" -> "末地";
            default -> id;
        };
    }

    /**
     * 获取当前服务器标识符
     */
    private static String getServerIdentifier() {
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        return "singleplayer";
    }

    /**
     * 获取当前服务器数据文件路径
     */
    private static Path getDataFile() {
        return CONFIG_DIR.resolve(getServerIdentifier() + ".json");
    }

    /**
     * 获取当前数据
     */
    private static CunminData getData() {
        String server = getServerIdentifier();
        return DATA_STORE.computeIfAbsent(server, k -> new CunminData());
    }

    /**
     * 加载数据
     */
    private static void loadData() {
        Path file = getDataFile();
        if (!Files.exists(file)) return;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            // 简单的 JSON 手动解析（避免引入额外依赖）
            CunminData data = new CunminData();
            String line;
            String currentKey = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                if (line.contains("\"emerald_chest\"") || line.contains("\"unload_chest\"")) {
                    currentKey = line.contains("emerald_chest") ? "emerald_chest" : "unload_chest";
                } else if (currentKey != null && line.contains("\"pos\"")) {
                    // 解析坐标
                    String posStr = line.split(":")[1].replaceAll("[\"\\[\\],]", "").trim();
                    String[] parts = posStr.split("\\s+");
                    if (parts.length == 3) {
                        BlockPos pos = new BlockPos(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                        );
                        data.positions.put(currentKey, pos);
                    }
                } else if (currentKey != null && line.contains("\"dimension\"")) {
                    // 维度值可能是 minecraft:the_end，也可能是 ResourceKey[...] 字符串。
                    // 只提取标准维度 ID，避免按冒号截断导致末地被误判为主世界。
                    String dimStr = line.substring(line.indexOf(':') + 1)
                        .replaceAll("[\"{},\\s]", "");
                    if (dimStr.contains("minecraft:the_nether")) {
                        dimStr = "minecraft:the_nether";
                    } else if (dimStr.contains("minecraft:the_end")) {
                        dimStr = "minecraft:the_end";
                    } else {
                        dimStr = "minecraft:overworld";
                    }
                    data.dimensions.put(currentKey, dimStr);
                }
            }

            DATA_STORE.put(getServerIdentifier(), data);

        } catch (IOException e) {
            // 静默失败
        }
    }

    /**
     * 保存数据
     */
    private static void saveData() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Path file = getDataFile();
            
            CunminData data = getData();
            
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                writer.write("{\n");
                
                boolean first = true;
                for (String key : new String[]{"emerald_chest", "unload_chest"}) {
                    if (!data.hasBinding(key)) continue;
                    
                    if (!first) writer.write(",\n");
                    first = false;
                    
                    BlockPos pos = data.getPos(key);
                    String dim = data.dimensions.getOrDefault(key, "minecraft:overworld");
                    
                    writer.write(String.format("  \"%s\": {\n", key));
                    writer.write(String.format("    \"pos\": \"%d %d %d\",\n", pos.getX(), pos.getY(), pos.getZ()));
                    writer.write(String.format("    \"dimension\": \"%s\"\n", dim));
                    writer.write("  }");
                }
                
                writer.write("\n}\n");
            }

        } catch (IOException e) {
            error("保存失败：" + e.getMessage());
        }
    }

    /**
     * 输出消息
     */
    private static void info(String message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                YiyiaddonModule.formatMessage("自动村民交易", message)
            ));
        }
    }

    /**
     * 输出错误
     */
    private static void error(String message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                YiyiaddonModule.formatMessage("自动村民交易", "§6§l" + message)
            ));
        }
    }

    /**
     * 容器绑定包装类（供外部模块使用）
     */
    public static class ContainerBinding {
        private final CunminData data;

        ContainerBinding(CunminData data) {
            this.data = data;
        }

        /**
         * 获取绿宝石箱坐标
         */
        public BlockPos getEmeraldBox() {
            return data.positions.get("emerald_chest");
        }

        /**
         * 获取成品交易箱坐标
         */
        public BlockPos getUnloadBox() {
            return data.positions.get("unload_chest");
        }

        /**
         * 获取绿宝石箱维度
         */
        public String getEmeraldBoxDimension() {
            return data.dimensions.get("emerald_chest");
        }

        /**
         * 获取成品交易箱维度
         */
        public String getUnloadBoxDimension() {
            return data.dimensions.get("unload_chest");
        }
    }

    /**
     * 数据存储类
     */
    private static class CunminData {
        private final Map<String, BlockPos> positions = new HashMap<>();
        private final Map<String, String> dimensions = new HashMap<>();

        public void setBinding(String key, BlockPos pos, ResourceKey<Level> dimension) {
            positions.put(key, pos);
            dimensions.put(key, toDimensionId(dimension));
        }

        /**
         * 把维度 ResourceKey 转成稳定裸 ID（minecraft:overworld/the_nether/the_end），
         * 与 loadData 的规范化格式保持一致，避免首次绑定后 getDimension 解析成错误维度。
         */
        private static String toDimensionId(ResourceKey<Level> dimension) {
            if (dimension == null) return "minecraft:overworld";
            String id = dimension.toString();
            if (id.contains("the_nether")) return "minecraft:the_nether";
            if (id.contains("the_end")) return "minecraft:the_end";
            return "minecraft:overworld";
        }

        public void removeBinding(String key) {
            positions.remove(key);
            dimensions.remove(key);
        }

        public void clear() {
            positions.clear();
            dimensions.clear();
        }

        public boolean hasBinding(String key) {
            return positions.containsKey(key);
        }

        public boolean hasAnyBinding() {
            return !positions.isEmpty();
        }

        public BlockPos getPos(String key) {
            return positions.get(key);
        }

        public ResourceKey<Level> getDimension(String key) {
            String dimStr = dimensions.get(key);
            if (dimStr == null) return Level.OVERWORLD;
            
            // 简化处理
            return switch (dimStr) {
                case "minecraft:the_nether" -> Level.NETHER;
                case "minecraft:the_end" -> Level.END;
                default -> Level.OVERWORLD;
            };
        }
    }

    /**
     * 获取当前绑定配置（静态方法，供其他模块调用）
     */
    public static ContainerBinding getBinding() {
        CunminData data = getData();
        return new ContainerBinding(data);
    }

    /**
     * 公共 API：供模块调用获取绑定点位
     */
    public static BlockPos getEmeraldChestPos() {
        CunminData data = getData();
        return data.hasBinding("emerald_chest") ? data.getPos("emerald_chest") : null;
    }

    public static BlockPos getUnloadChestPos() {
        CunminData data = getData();
        return data.hasBinding("unload_chest") ? data.getPos("unload_chest") : null;
    }

    public static ResourceKey<Level> getEmeraldChestDimension() {
        CunminData data = getData();
        return data.hasBinding("emerald_chest") ? data.getDimension("emerald_chest") : null;
    }

    public static ResourceKey<Level> getUnloadChestDimension() {
        CunminData data = getData();
        return data.hasBinding("unload_chest") ? data.getDimension("unload_chest") : null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  供模块配置页面的按钮调用（参考 WKCommand）
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 设置绑定（供模块按钮调用）
     * @param key "emerald_chest" 或 "unload_chest"
     * @return true = 设置成功，false = 设置失败
     */
    public static boolean setBinding(String key) {
        CunminCommand cmd = new CunminCommand();
        
        // 检查准星是否对准方块
        BlockPos target = cmd.getTargetBlock();
        if (target == null) {
            cmd.error("§c准星未对准任何方块，请重新设置");
            return false;
        }
        
        // 检查是否为容器
        if (!cmd.isContainer(target)) {
            cmd.error("§c目标方块不是容器（箱子/桶/潜影盒等），请重新设置");
            return false;
        }
        
        // 调用绑定逻辑
        String displayName = key.equals("emerald_chest") ? "绿宝石箱" : "成品交易箱";
        cmd.bindContainer(displayName, key);
        return true;
    }

    /**
     * 删除绑定（供模块按钮调用）
     * @param key "emerald_chest" 或 "unload_chest"
     */
    public static void removeBinding(String key) {
        CunminCommand cmd = new CunminCommand();
        CunminData data = getData();
        
        if (data.hasBinding(key)) {
            data.removeBinding(key);
            saveData();
            
            String name = key.equals("emerald_chest") ? "绿宝石箱" : "成品交易箱";
            cmd.info("§c§l✗ 已删除 " + name + " 绑定");
        } else {
            cmd.error("§c该坐标本来就没有绑定");
        }
    }

    /**
     * 获取准星对准的方块（内部方法）
     */
    private BlockPos getTargetBlock() {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return ((BlockHitResult) mc.hitResult).getBlockPos();
    }

    /**
     * 检查方块是否为容器（内部方法）
     */
    private boolean isContainer(BlockPos pos) {
        if (mc.level == null) return false;
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        return blockEntity instanceof Container;
    }
}
