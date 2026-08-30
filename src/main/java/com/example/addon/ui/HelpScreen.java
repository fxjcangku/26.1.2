package com.example.addon.ui;

import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.Minecraft;

/**
 * 独立的使用说明窗口基类
 * 
 * 用于显示模块的详细使用说明，采用黑客终端风格设计
 * 颜色方案：
 * - 主题色：青色
 * - 标题：亮白色
 * - 正文：灰白色
 * - 提示：深灰色
 * - 强调：金色/绿色
 */
public class HelpScreen extends WindowScreen {
    private final Module module;
    private final String[] helpContent;

    public HelpScreen(GuiTheme theme, Module module, String[] helpContent) {
        super(theme, module.title + " - 使用说明");
        this.module = module;
        this.helpContent = helpContent;
    }

    @Override
    public void initWidgets() {
        WVerticalList list = add(theme.verticalList()).expandX().widget();
        
        // 渲染说明内容
        for (String line : helpContent) {
            if (line == null || line.isEmpty()) {
                list.add(theme.horizontalSeparator()).expandX();
                continue;
            }
            
            // 使用WLabel显示带颜色的文本
            list.add(theme.label(line)).expandX();
        }
        
        // 底部：分隔线 + 6 色主题按钮排，横向铺满空白（对应 5.8 强调色体系，点击均关闭窗口）
        WTable buttonTable = list.add(theme.table()).expandX().widget();
        buttonTable.add(theme.horizontalSeparator()).expandX();
        buttonTable.row();

        // 六种强调色按钮横向铺满，避免底部只有一个「关闭」按钮显得空荡
        String[] themeColors = {"§a绿色", "§b青色", "§e黄色", "§6金色", "§d粉色", "§c红色"};
        for (String color : themeColors) {
            WButton btn = buttonTable.add(theme.button(color)).expandX().widget();
            btn.action = () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.setScreen(null);
                }
            };
        }
    }

    /**
     * 创建标准格式的使用说明内容
     * 
     * @param sections 多个章节，每个章节是一个字符串数组
     * @return 格式化后的完整说明文本
     */
    public static String[] buildHelpContent(HelpSection... sections) {
        int totalLines = 3; // 标题框占3行
        for (HelpSection section : sections) {
            totalLines += 2 + section.lines.length; // 章节标题+内容+空行
        }
        
        String[] content = new String[totalLines];
        int index = 0;
        
        // 标题框
        content[index++] = "§8┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓";
        content[index++] = "§8┃ §3§l> §b§l使用说明                                      §8┃";
        content[index++] = "§8┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛";
        
        // 各章节内容
        for (HelpSection section : sections) {
            content[index++] = "";
            content[index++] = "§3[§b#§3] §f" + section.title;
            for (String line : section.lines) {
                content[index++] = line;
            }
        }
        
        return content;
    }

    /**
     * 使用说明章节
     */
    public static class HelpSection {
        public final String title;
        public final String[] lines;

        public HelpSection(String title, String... lines) {
            this.title = title;
            this.lines = lines;
        }
    }
}
