package com.example.addon.core;

// 开发规范全文见 convention/YiyiaddonConvention.java（唯一权威正本，动手前必读）。

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/**
 * yiyiaddon 模块基类
 *
 * 统一消息格式、颜色规范、说明面板生成
 *
 * @author yiyijia
 * @see YiyiaddonWatermark
 */
public abstract class YiyiaddonModule extends Module {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  构造函数
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    protected YiyiaddonModule(Category category, String name, String description) {
        super(category, name, description);
        toggleOnBindRelease = false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  配置反序列化 - 清理历史残留
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Meteor 的 fromTag 会把 modules.nbt 里的 toggleOnKeyRelease 读回内存。
     * 早期版本误设过 true，该值会导致 Modules.onOpenScreen 在关闭 GUI 时
     * 把模块一并关掉（表现为「刚开就自动关」）。
     * yiyiaddon 所有模块都不需要这个行为，读档后一律压回 false。
     */
    @Override
    public Module fromTag(CompoundTag tag) {
        Module result = super.fromTag(tag);
        toggleOnBindRelease = false;
        return result;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  模块开关覆写 - 统一输出格式
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void toggle() {
        super.toggle();
        // Meteor 的 GUI 点击路径不会调用 sendToggledMsg()
        // 在这里统一输出，sendToggledMsg() 置空防止按键绑定双重提示
        if (mc.player != null && chatFeedback) {
            // 确保使用翻译后的标题（如果翻译已启用，title 字段已经被翻译过了）
            String status = isActive() ? "§a§l已开启" : "§c§l已关闭";
            // 直接发送，不经过 notify()，这样单人世界也能看到
            mc.player.sendSystemMessage(Component.literal(formatMessage(title, status)));
        }
    }

    @Override
    public void sendToggledMsg() {
        // 空实现：提示已在 toggle() 中输出
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Meteor 原生消息拦截与中文化
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void info(String message, Object... args) {
        // 拦截按键绑定消息并中文化
        if ("Removed bind.".equals(message)) {
            notify("已移除按键绑定");
            return;
        }
        if (message != null && message.startsWith("Bound to")) {
            String expanded = formatArgs(message, args);
            String clean = expanded.replaceAll("\\(highlight\\)|\\(default\\)", "").trim();
            String key = clean.replace("Bound to", "").replace(".", "").trim();
            notify("已绑定按键：" + key);
            return;
        }
        notify(formatArgs(message, args));
    }

    @Override
    public void info(Component message) {
        notify(message.getString());
    }

    @Override
    public void warning(String message, Object... args) {
        notify("§e§l" + formatArgs(message, args));
    }

    @Override
    public void error(String message, Object... args) {
        notify("§c§l" + formatArgs(message, args));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  消息输出方法 - 子类使用
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 普通消息（白色）
     * 格式：§c§l[yiyiaddon]§r§f§l[模块名]§r§f消息
     */
    protected void notify(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(formatMessage(title, "§f" + message)));
    }

    /**
     * 错误消息（橙色加粗）
     * 格式：§c§l[yiyiaddon]§r§f§l[模块名]§r§6§l错误消息
     */
    protected void notifyError(String message) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(formatMessage(title, "§6§l" + message)));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  启动自检 - 需要前置配置的模块使用
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 报告自检缺项并关闭模块
     *
     * 一次列出所有缺失项，用户配好一项下次启动就少一条，不用挤牙膏式反复试。
     * 只报第一个错会让用户来回开关模块，配一项试一次，体验很差。
     *
     * 关闭走 mc.execute 延后到下一帧：Module.toggle() 是先 addActive 再调
     * onActivate，在 onActivate 里直接 toggle() 会造成状态机重入。
     *
     * @param missing 缺项清单，为空表示自检通过
     * @return true 表示自检通过可以继续启动，false 表示已中止
     */
    protected boolean reportSelfCheck(java.util.List<String> missing) {
        if (missing.isEmpty()) return true;

        chatFeedback = false;
        mc.execute(() -> {
            if (isActive()) toggle();
            chatFeedback = true;
        });

        notifyError("还差 " + missing.size() + " 项没配好，配完再开：");
        for (int i = 0; i < missing.size(); i++) {
            notify("§6  " + (i + 1) + ". §f" + missing.get(i));
        }
        return false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  高亮工具方法 - 强调色转换体系（不同类别用不同颜色，一眼区分）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 物品/文本高亮（亮绿色粗体） - 用于物品名、目标矿物、成功值 */
    protected String highlightText(String text) {
        return "§a§l" + text + "§r§f§l";
    }

    /** 功能/模式高亮（亮青色粗体） - 用于功能名、模式名、状态名 */
    protected String highlightFunction(String text) {
        return "§b§l" + text + "§r§f§l";
    }

    /** 数值/阈值高亮（黄色粗体） - 用于数量、阈值、百分比等数字 */
    protected String highlightNumber(String text) {
        return "§e§l" + text + "§r§f§l";
    }

    /** 服务器高亮（金色粗体） - 用于服务器名 */
    protected String highlightServer(String text) {
        return "§6§l" + text + "§r§f§l";
    }

    /** 位置高亮（紫粉色粗体） - 用于地点坐标 */
    protected String highlightLocation(String text) {
        return "§d§l" + text + "§r§f§l";
    }

    /** 指令高亮（黄色粗体） - 用于指令示例 */
    protected String highlightCommand(String text) {
        return "§e§l" + text + "§r§f§l";
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  说明面板构建 - 子类覆写 getWidget()
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 标准说明面板构建
     *
     * @param theme Meteor GUI 主题
     * @param sections 章节数组，第一个是标题，后续是段落
     * @return WWidget 面板控件
     */
    protected WWidget buildInfoWidget(GuiTheme theme, String[]... sections) {
        WTable t = theme.table();
        boolean firstSection = true;

        for (String[] section : sections) {
            if (section == null || section.length == 0) continue;

            // 段落间空行（第一段标题行前不加）
            if (!firstSection) {
                t.add(theme.label(" ")).expandX();
                t.row();
            }

            // 添加段落内容
            for (String line : section) {
                t.add(theme.label(line)).expandX();
                t.row();
            }

            firstSection = false;
        }

        return t;
    }

    /** 说明面板按钮的统一最小宽度，保证同一面板内所有按钮等宽 */
    protected static final double BUTTON_MIN_WIDTH = 90;

    /**
     * 添加等宽按钮到说明面板
     *
     * WTable 的列宽取该列内容的最大值，若用 expandX() 只有第一列会吃掉剩余空间，
     * 导致同一行的按钮宽度不一致（第一列很长、后面按文字长度收缩）。
     * 这里改用 minWidth 统一列宽 + expandWidgetX 让按钮填满单元格，
     * 三列布局才会真正等宽。
     *
     * @param theme  Meteor GUI 主题
     * @param table  面板表格
     * @param title  按钮文字
     * @param action 点击回调
     */
    protected void addUniformButton(GuiTheme theme, WTable table, String title, Runnable action) {
        WButton button = theme.button(title);
        button.action = action;
        table.add(button).minWidth(BUTTON_MIN_WIDTH).expandWidgetX();
    }

    /**
     * 带自定义头部的说明面板构建
     *
     * @param theme Meteor GUI 主题
     * @param headerWidgets 头部控件构建器（放置按钮等交互控件）
     * @param sections 章节数组
     * @return WWidget 面板控件
     */
    protected WWidget buildInfoWidget(GuiTheme theme, Consumer<WTable> headerWidgets, String[]... sections) {
        WTable t = theme.table();

        // 先添加头部控件
        headerWidgets.accept(t);
        t.add(theme.label(" ")).expandX();
        t.row();

        // 再添加说明文本
        boolean firstSection = true;
        for (String[] section : sections) {
            if (section == null || section.length == 0) continue;

            if (!firstSection) {
                t.add(theme.label(" ")).expandX();
                t.row();
            }

            for (String line : section) {
                t.add(theme.label(line)).expandX();
                t.row();
            }

            firstSection = false;
        }

        return t;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  消息格式化内部方法
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 格式化模块消息
     * 格式：§c§l[yiyiaddon]§r§f§l[模块名]§r内容
     */
    public static String formatMessage(String moduleName, String message) {
        String cleanModuleName = stripColorCodes(stripPrefix(moduleName));
        String cleanMessage = stripPrefix(message);
        return "§c§l[yiyiaddon]§r§f§l[" + cleanModuleName + "]§r" + cleanMessage;
    }

    /**
     * 格式化坐标显示（个人习惯）
     * 格式：§7X§f38 §7Y§f-60 §7Z§f59
     * XYZ标签灰色，坐标数值白色
     */
    public static String formatCoords(int x, int y, int z) {
        return "§7X§f" + x + " §7Y§f" + y + " §7Z§f" + z;
    }

    /**
     * 格式化坐标与维度显示（个人习惯）
     * 格式：§7X§f38 §7Y§f-60 §7Z§f59 §8▸ §f主世界
     * 使用深灰色箭头分隔坐标和维度，末尾自动重置颜色代码防止污染后续文本
     */
    public static String formatCoordsWithDimension(int x, int y, int z, String dimension) {
        return formatCoords(x, y, z) + " §8▸ §f" + dimension + "§r";
    }

    /**
     * 格式化坐标与维度显示（带自定义维度颜色）
     * 格式：§7X§f38 §7Y§f-60 §7Z§f59 §8▸ §6主世界
     * 维度颜色可自定义，末尾自动重置颜色代码防止污染后续文本
     */
    public static String formatCoordsWithDimension(int x, int y, int z, String dimension, String dimColor) {
        return formatCoords(x, y, z) + " §8▸ " + dimColor + dimension + "§r";
    }

    /** 去除 [yiyiaddon] 前缀 */
    private static String stripPrefix(String value) {
        return value.replace("[yiyiaddon]", "").trim();
    }

    /** 去除 Minecraft 颜色代码 */
    private static String stripColorCodes(String value) {
        return value.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    /** 格式化参数 */
    private static String formatArgs(String message, Object... args) {
        if (args == null || args.length == 0) return message;
        try {
            return String.format(message, args);
        } catch (Exception ignored) {
            return message;
        }
    }
}