package com.example.addon.itemid;

import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import net.minecraft.world.item.ItemStack;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * ID 配置管理模块（辅助体系 · 第二环）。
 *
 * <p>完整交互 GUI：识别物品、手动添加、删除、刷新、打开目录，并实时展示
 * 当前 ID 清单。底层数据源是 {@link ItemIdManager}（唯一数据管理层），
 * 与「ID 识别」「自动箱子」共享同一份数据，不另起炉灶。</p>
 *
 * <p>对应数据链：ID 文件 → ID 配置管理 → AutoChest 物品选择器。</p>
 */
public final class IdConfigModule extends YiyiaddonModule {

    private final ItemIdManager idManager;
    private final EntityIdManager entityIdManager;

    /** 当前面板主题与清单容器引用，供删除 / 刷新后局部重建清单 */
    private GuiTheme theme;
    private WTable itemListTable;

    public IdConfigModule(ItemIdManager idManager, EntityIdManager entityIdManager) {
        super(AddonTemplate.CATEGORY_ASSIST, "ID配置管理", "管理已识别的物品ID集合：识别/添加/删除/刷新/打开目录。点击开启查看。");
        this.idManager = idManager;
        this.entityIdManager = entityIdManager;
    }

    @Override
    public void onActivate() {
        idManager.reload();
        if (!idManager.hasAny()) {
            notifyError("当前没有任何 ID 配置，先用「识别物品」或「手动添加」");
            return;
        }
        notify("§a§l✓ 已加载 ID 配置 §8▸ 共 " + highlightNumber(String.valueOf(idManager.size())) + " 个物品");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        this.theme = theme;
        this.itemListTable = null;
        return buildInfoWidget(theme, this::buildHeader, buildSections());
    }

    // ── 面板头部：按钮区 + 清单 ──

    private void buildHeader(WTable table) {
        addUniformButton(theme, table, "识别物品（主手→副手）", this::identifyItem);
        table.row();
        addUniformButton(theme, table, "手动添加物品ID", this::openAddScreen);
        table.row();
        addUniformButton(theme, table, "刷新（重读磁盘）", this::refresh);
        table.row();
        addUniformButton(theme, table, "打开物品ID目录", () -> openDirectory(idManager.itemsDirectory()));
        table.row();
        addUniformButton(theme, table, "打开实体ID目录", () -> openDirectory(entityIdManager.entitiesDirectory()));
        table.row();
        addUniformButton(theme, table, "打开ID总目录", () -> openDirectory(autochestDir()));
        table.row();

        itemListTable = table.add(theme.table()).expandX().widget();
        table.row();
        rebuildItemList();
    }

    /** 局部重建 ID 清单（删除 / 刷新 / 识别后调用，不重建整个面板），按原版 / 自定义分类 */
    private void rebuildItemList() {
        if (itemListTable == null) return;
        itemListTable.clear();
        Set<ItemIdentity> all = idManager.all();

        itemListTable.add(theme.label("§b§l▌ 当前ID清单 §8▸ §e" + all.size() + " 个")).expandX();
        itemListTable.row();

        if (all.isEmpty()) {
            itemListTable.add(theme.label("§8暂无ID配置，点击上方「识别物品」或「手动添加物品ID」")).expandX();
            itemListTable.row();
            return;
        }

        // 原版物品分类
        itemListTable.add(theme.label("§a§l▌ 原版物品")).expandX();
        itemListTable.row();
        boolean hasVanilla = false;
        for (ItemIdentity id : all) {
            if (!id.isVanilla()) continue;
            hasVanilla = true;
            addItemRow(id);
        }
        if (!hasVanilla) {
            itemListTable.add(theme.label("  §8无")).expandX();
            itemListTable.row();
        }

        // 自定义物品分类
        itemListTable.add(theme.label("§d§l▌ 自定义物品")).expandX();
        itemListTable.row();
        boolean hasCustom = false;
        for (ItemIdentity id : all) {
            if (!id.isCustom()) continue;
            hasCustom = true;
            addItemRow(id);
        }
        if (!hasCustom) {
            itemListTable.add(theme.label("  §8无")).expandX();
            itemListTable.row();
        }
    }

    /** 添加一行 ID 条目（显示名 + 物品 ID + 删除按钮） */
    private void addItemRow(ItemIdentity id) {
        itemListTable.add(theme.label("§a" + id.displayName() + " §8▸ §7" + id.itemId())).expandX();
        WMinus del = itemListTable.add(theme.minus()).right().widget();
        del.action = () -> removeItem(id);
        itemListTable.row();
    }

    // ── 面板动作 ──

    /** 识别手持物品（主手优先，主手空读副手），直接调用 ItemIdentifier，不模拟玩家输入 */
    private void identifyItem() {
        if (mc.player == null) {
            notifyError("玩家未加载");
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) held = mc.player.getOffhandItem();
        if (held.isEmpty()) {
            notifyError("没有可识别物品：主手和副手都是空的");
            return;
        }

        ItemIdentity identity = ItemIdentifier.identifyItem(held);
        if (identity == null) {
            notifyError("识别失败");
            return;
        }
        if (idManager.add(identity) != null) {
            notify("§a§l✓ 已识别物品 §8▸ " + highlightText(identity.displayName()));
            rebuildItemList();
        } else {
            notifyError("该物品已在 ID 配置中");
        }
    }

    /** 打开手动添加物品 ID 屏幕（经 Registry 验证） */
    private void openAddScreen() {
        mc.setScreen(new IdAddScreen(theme, idManager));
    }

    /** 刷新：重新读取磁盘，运行时使用内存缓存 */
    private void refresh() {
        idManager.reload();
        notify("§a§l✓ 已刷新 ID 配置 §8▸ 共 " + highlightNumber(String.valueOf(idManager.size())) + " 个物品");
        rebuildItemList();
    }

    /** 删除单个 ID（内存 + 落盘，触发 AutoChest 选择器联动） */
    private void removeItem(ItemIdentity id) {
        if (idManager.remove(id)) {
            notify("§c§l✗ 已删除 ID §8▸ " + highlightText(id.displayName()));
        }
        rebuildItemList();
    }

    // ── 目录打开（26.1.2 已移除 net.minecraft.Util，改用 AWT Desktop + 独立线程） ──

    private Path autochestDir() {
        return mc.gameDirectory.toPath().resolve("AutoChest");
    }

    private void openDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            // 目录创建失败仍尝试打开
        }
        Thread opener = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(dir.toFile());
                } else {
                    // Windows 路径含空格用 /select 兜底
                    new ProcessBuilder("explorer.exe", "/select," + dir.toAbsolutePath()).start();
                }
            } catch (Exception e) {
                mc.execute(() -> notifyError("打开目录失败：" + e.getMessage()));
            }
        }, "yiyiaddon-OpenDir");
        opener.setDaemon(true);
        opener.start();
    }

    // ── 说明面板 ──

    private String[][] buildSections() {
        return new String[][]{
            {"§lID 配置管理 · 使用说明"},
            {"§e§l▌ 使用方法",
             "§f  · 识别物品：直接识别手持物品（主手→副手）写入ID",
             "§f  · 手动添加：输入物品ID经Registry验证后保存",
             "§f  · 删除：点击清单右侧「-」移除该ID",
             "§f  · 刷新：重新读取磁盘，运行时走内存缓存"},
            {"§a§l▌ 数据链",
             "§f  · ID识别 → ID配置管理 → AutoChest选择器",
             "§f  · 三处共享同一份 ItemIdManager，实时联动无需重启"}
        };
    }
}
