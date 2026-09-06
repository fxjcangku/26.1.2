package com.example.addon.enchant.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 原版装备附魔 · 合成报告记录器（静态工具类）。
 *
 * <p>把「附魔台随机附魔 → 砂轮剔除 → 铁砧装备+装备合并」的完整流水账记录成一份
 * 彩色排版的中文 HTML 报告（不同信息用不同颜色区分，方便阅读），落盘到
 * {@code .minecraft/yiyiaddon-reports/装备附魔合成/} 目录，供配置界面
 * 「合成附魔详细」按钮一键打开、「删除合成记录」按钮一键清空。</p>
 *
 * <p>报告重点回答两个问题：合成一把极品装备到底消耗了多少经验、浪费了多少把相同工具。
 * 经验按「附魔台 30 级/次 + 铁砧实际费用（读 AnvilMenu.getCost）」累计；浪费工具数按
 * 「附魔次数 − 成品数」计算（每附魔一次消耗一把裸装备，成品之外全部被砂轮磨掉或铁砧合并消耗）。</p>
 */
public final class GearCraftReport {

    private static final DateTimeFormatter 时间格式 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter 文件名时间 = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 附魔台每次附魔所需经验等级（与 XpPlanner.ENCHANT_TABLE_LEVEL 一致） */
    private static final int 附魔台等级 = 30;

    /** 对比汇总 JSON 序列化器（跨策略多轮结果持久化，供对比表读取） */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean 已开始 = false;
    private static String 装备名 = "";
    private static String 方案名 = "";
    private static String 目标词条 = "";
    private static String 合成策略 = "";
    private static String 当前文件 = null;

    private static final List<String> 事件 = new ArrayList<>();
    private static int 附魔次数 = 0;
    private static int 砂轮次数 = 0;
    private static int 铁砧次数 = 0;
    private static int 铁砧经验合计 = 0;
    private static int 成品数 = 0;

    private GearCraftReport() {
    }

    /** 开始一次合成记录：重置全部运行态，生成本次报告文件名（按当前工具名+品质命名） */
    public static void begin(String 装备, String 方案, String 目标词条摘要, String 策略) {
        已开始 = true;
        装备名 = 安全文件名(装备);
        方案名 = 方案 == null ? "" : 方案;
        目标词条 = 目标词条摘要 == null ? "" : 目标词条摘要;
        合成策略 = 策略 == null ? "" : 策略;
        当前文件 = "合成报告-" + 装备名 + "-" + (合成策略.isEmpty() ? "默认" : 合成策略)
            + "-" + LocalDateTime.now().format(文件名时间) + ".html";
        事件.clear();
        附魔次数 = 0;
        砂轮次数 = 0;
        铁砧次数 = 0;
        铁砧经验合计 = 0;
        成品数 = 0;
    }

    /** 关闭本次记录（记录开关关闭时调用，清除残留运行态，使后续 record 全部失效） */
    public static void disable() {
        已开始 = false;
        事件.clear();
        附魔次数 = 0;
        砂轮次数 = 0;
        铁砧次数 = 0;
        铁砧经验合计 = 0;
        成品数 = 0;
        当前文件 = null;
        合成策略 = "";
    }

    /** 记录一次附魔台附魔（词条为中文名+等级摘要，空表示未获得任何附魔） */
    public static void recordEnchant(String 词条) {
        if (!已开始) return;
        附魔次数++;
        事件.add(badge("附魔", "enchant") + "裸装备 → " + 附魔(词条));
    }

    /** 记录一次砂轮清除（磨前词条为被磨掉的附魔，通常是一把垃圾装备） */
    public static void recordGrind(String 磨前词条) {
        if (!已开始) return;
        砂轮次数++;
        事件.add(badge("砂轮", "grind") + "磨掉 " + 附魔(磨前词条) + "（浪费 1 把装备）");
    }

    /** 记录一次铁砧「装备 + 装备」合并（费用为读取 AnvilMenu 的实际等级） */
    public static void recordAnvil(String 主装备, String 材料, String 产物, int 费用) {
        if (!已开始) return;
        铁砧次数++;
        铁砧经验合计 += Math.max(0, 费用);
        事件.add(badge("铁砧", "anvil") + "主[" + 附魔(主装备) + "] + 材料[" + 附魔(材料)
            + "] → 产物[" + 附魔(产物) + "]，费用 <span class=\"cost\">" + 费用 + " 级</span>");
    }

    /** 记录铁砧「太昂贵」处理（保留必需成品 或 送砂轮清零重来） */
    public static void recordTooExpensive(String 主装备, boolean 保留成品) {
        if (!已开始) return;
        if (保留成品) {
            事件.add(badge("太昂贵", "warn") + "保留必需附魔成品[" + 附魔(主装备) + "]，放弃剩余可选附魔");
        } else {
            事件.add(badge("太昂贵", "warn") + "装备[" + 附魔(主装备) + "] 送砂轮清零重来");
        }
    }

    /** 记录一件极品成品产出（最终词条为达标后的完整附魔） */
    public static void recordComplete(String 最终词条) {
        if (!已开始) return;
        成品数++;
        事件.add(badge("成品", "done") + 附魔(最终词条) + " 达标，存入成品箱");
    }

    /** 记录一件装备处理失败被跳过 */
    public static void recordSkip(String 原因) {
        if (!已开始) return;
        事件.add(badge("跳过", "skip") + "装备处理失败（" + html(原因) + "）");
    }

    /** 落盘当前完整报告（运行中每次产出成品后调用，模块停止时也调用一次） */
    public static void flush() {
        if (!已开始 || 事件.isEmpty()) return;
        Path dir = 报告目录();
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            // 单轮流水账（文件名含策略，避免不同策略混在一起）
            Files.writeString(dir.resolve(当前文件), render(), StandardCharsets.UTF_8);
            // 更新同装备各策略的对比汇总表（跨策略联动对比经验消耗）
            更新对比汇总(dir);
        } catch (Exception ignored) {
            // 写盘失败不阻断附魔主流程
        }
    }

    /** 打开报告目录（跨平台：AWT Desktop 优先，失败按 Windows / macOS / Linux 分别回退） */
    public static void openFolder() {
        Path dir = 报告目录();
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        File folder = dir.toFile();
        Thread opener = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(folder);
                    return;
                }
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    new ProcessBuilder("explorer.exe", folder.getAbsolutePath()).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", folder.getAbsolutePath()).start();
                } else {
                    new ProcessBuilder("xdg-open", folder.getAbsolutePath()).start();
                }
            } catch (Exception ignored) {
            }
        }, "yiyiaddon-OpenCraftReport");
        opener.setDaemon(true);
        opener.start();
    }

    /** 一键删除报告目录下的全部合成报告文件，返回删除的文件数 */
    public static int deleteAll() {
        Path dir = 报告目录();
        if (dir == null) return 0;
        int deleted = 0;
        try {
            if (!Files.isDirectory(dir)) return 0;
            try (var stream = Files.list(dir)) {
                for (Path p : stream.toList()) {
                    try {
                        if (Files.deleteIfExists(p)) deleted++;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return deleted;
    }

    /** 报告目录：.minecraft/yiyiaddon-reports/装备附魔合成/ */
    private static Path 报告目录() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return null;
        return mc.gameDirectory.toPath().resolve("yiyiaddon-reports").resolve("装备附魔合成");
    }

    /** 本轮统计快照（用于写入对比汇总，跨策略覆盖最近一轮） */
    private static 轮次统计 当前轮次() {
        轮次统计 r = new 轮次统计();
        r.策略 = 合成策略.isEmpty() ? "默认" : 合成策略;
        r.方案名 = 方案名;
        r.时间 = LocalDateTime.now().format(时间格式);
        r.附魔次数 = 附魔次数;
        r.砂轮次数 = 砂轮次数;
        r.铁砧次数 = 铁砧次数;
        r.附魔台经验 = 附魔次数 * 附魔台等级;
        r.铁砧经验 = 铁砧经验合计;
        r.总经验 = r.附魔台经验 + r.铁砧经验;
        r.消耗工具 = 附魔次数;
        r.浪费工具 = Math.max(0, 附魔次数 - 成品数);
        r.成品数 = 成品数;
        return r;
    }

    /** 更新同装备 + 同方案下各策略的对比汇总：当前策略覆盖最近一轮，其余策略保留 */
    private static void 更新对比汇总(Path dir) {
        String 分组 = 装备名 + " · " + (方案名.isEmpty() ? "默认" : 方案名);
        Map<String, Map<String, 轮次统计>> 全部 = 读对比数据(dir);
        Map<String, 轮次统计> 组 = 全部.computeIfAbsent(分组, k -> new LinkedHashMap<>());
        组.put(合成策略.isEmpty() ? "默认" : 合成策略, 当前轮次());
        写对比数据(dir, 全部);
        try {
            Files.writeString(dir.resolve(对比汇总文件名(装备名, 方案名)),
                渲染对比汇总(组, 装备名, 方案名), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    /** 读持久化的对比数据（文件缺失或损坏返回空表，不阻断主流程） */
    private static Map<String, Map<String, 轮次统计>> 读对比数据(Path dir) {
        Path f = dir.resolve("对比汇总.json");
        if (!Files.exists(f)) return new LinkedHashMap<>();
        try {
            Map<String, Map<String, 轮次统计>> m = GSON.fromJson(
                Files.readString(f, StandardCharsets.UTF_8),
                new TypeToken<Map<String, Map<String, 轮次统计>>>() {}.getType());
            return m == null ? new LinkedHashMap<>() : m;
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    /** 写回对比数据 JSON（失败静默，不阻断附魔主流程） */
    private static void 写对比数据(Path dir, Map<String, Map<String, 轮次统计>> 全部) {
        try {
            Files.writeString(dir.resolve("对比汇总.json"), GSON.toJson(全部), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    /** 对比汇总 HTML 文件名（按装备 + 方案区分，同装备同方案共用一份对比表） */
    private static String 对比汇总文件名(String 装备, String 方案) {
        return "对比汇总-" + 安全文件名(装备) + "-" + 安全文件名(方案 == null || 方案.isEmpty() ? "默认" : 方案) + ".html";
    }

    /** 渲染同装备同方案下各策略的对比表，总经验最低的一行高亮为最优 */
    private static String 渲染对比汇总(Map<String, 轮次统计> 组, String 装备, String 方案) {
        int 最优经验 = Integer.MAX_VALUE;
        for (轮次统计 r : 组.values()) 最优经验 = Math.min(最优经验, r.总经验);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>对比汇总 - ").append(html(装备)).append("</title>");
        sb.append("<style>").append(css()).append(css对比()).append("</style></head><body>");
        sb.append("<h1>合成策略 · 对比汇总</h1>");
        sb.append("<p class=\"sub\">").append(html(装备)).append(" · ").append(html(方案))
            .append(" · ").append(LocalDateTime.now().format(时间格式)).append("</p>");
        sb.append("<table class=\"cmp\"><thead><tr>");
        sb.append("<th>策略</th><th>成品</th><th>附魔台</th><th>砂轮</th><th>铁砧</th>")
            .append("<th>附魔台经验</th><th>铁砧经验</th><th class=\"c\">总经验</th>")
            .append("<th>消耗工具</th><th>浪费工具</th><th>更新时间</th></tr></thead><tbody>");
        for (轮次统计 r : 组.values()) {
            boolean 最优 = r.总经验 == 最优经验;
            sb.append("<tr").append(最优 ? " class=\"best\"" : "").append(">")
                .append("<td>").append(最优 ? "★ " : "").append(html(r.策略)).append("</td>")
                .append("<td>").append(r.成品数).append(" 件</td>")
                .append("<td>").append(r.附魔次数).append(" 次</td>")
                .append("<td>").append(r.砂轮次数).append(" 次</td>")
                .append("<td>").append(r.铁砧次数).append(" 次</td>")
                .append("<td class=\"c\">").append(r.附魔台经验).append(" 级</td>")
                .append("<td class=\"c\">").append(r.铁砧经验).append(" 级</td>")
                .append("<td class=\"c\">").append(r.总经验).append(" 级</td>")
                .append("<td>").append(r.消耗工具).append(" 把</td>")
                .append("<td>").append(r.浪费工具).append(" 把</td>")
                .append("<td class=\"t\">").append(html(r.时间)).append("</td>")
                .append("</tr>");
        }
        sb.append("</tbody></table>");
        sb.append("<p class=\"hint\">★ 表示总经验最低的策略；切到不同「合成策略」各跑一轮即可在此对比。</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /** 生成完整 HTML 报告文本（彩色排版） */
    private static String render() {
        int 消耗工具 = 附魔次数;
        int 浪费工具 = Math.max(0, 附魔次数 - 成品数);
        int 附魔台经验 = 附魔次数 * 附魔台等级;
        int 总经验 = 附魔台经验 + 铁砧经验合计;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        sb.append("<title>合成报告 - ").append(html(装备名)).append("</title>");
        sb.append("<style>").append(css()).append("</style></head><body>");

        sb.append("<h1>原版装备附魔 · 合成报告</h1>");
        sb.append("<p class=\"sub\">").append(html(装备名)).append(" · ").append(html(方案名))
            .append(" · ").append(LocalDateTime.now().format(时间格式)).append("</p>");

        sb.append("<div class=\"cards\">");
        sb.append(card("目标装备", html(装备名), "gear"));
        sb.append(card("极品方案", html(方案名), "profile"));
        sb.append(card("合成策略", html(合成策略.isEmpty() ? "默认" : 合成策略), "profile"));
        sb.append(card("目标词条", html(目标词条), "ench"));
        sb.append(card("产出成品", 成品数 + " 件", "done"));
        sb.append(card("附魔次数", 附魔次数 + " 次", ""));
        sb.append(card("砂轮次数", 砂轮次数 + " 次", "warn"));
        sb.append(card("铁砧次数", 铁砧次数 + " 次", ""));
        sb.append(card("消耗工具", 消耗工具 + " 把", ""));
        sb.append(card("浪费工具", 浪费工具 + " 把", "warn"));
        sb.append(card("附魔台经验", 附魔台经验 + " 级", "cost"));
        sb.append(card("铁砧经验", 铁砧经验合计 + " 级", "cost"));
        sb.append(card("经验消耗合计", 总经验 + " 级", "cost"));
        sb.append("</div>");

        sb.append("<h2>合成顺序</h2>");
        sb.append("<ol class=\"timeline\">");
        if (事件.isEmpty()) {
            sb.append("<li class=\"empty\">（暂无操作记录）</li>");
        } else {
            for (String e : 事件) {
                sb.append("<li>").append(e).append("</li>");
            }
        }
        sb.append("</ol>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /** 概览卡片（标签 + 彩色数值） */
    private static String card(String label, String value, String cls) {
        return "<div class=\"card\"><div class=\"label\">" + label + "</div><div class=\"value " + cls + "\">"
            + value + "</div></div>";
    }

    /** 事件分类彩色徽标 */
    private static String badge(String text, String cls) {
        return "<span class=\"badge badge-" + cls + "\">" + text + "</span>";
    }

    /** 附魔词条绿色高亮（空显示「无」） */
    private static String 附魔(String 词条) {
        return "<span class=\"ench\">" + html(词条.isEmpty() ? "无" : 词条) + "</span>";
    }

    /** HTML 特殊字符转义 */
    private static String html(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 内联样式表：浅色卡片式排版，重要信息分色区分 */
    private static String css() {
        return "*{box-sizing:border-box}"
            + "body{margin:0;padding:24px;font-family:\"Microsoft YaHei\",\"PingFang SC\",\"Segoe UI\",sans-serif;"
            + "background:#f5f7fa;color:#1f2937;line-height:1.6}"
            + "h1{font-size:26px;color:#0f766e;margin:0 0 4px}"
            + ".sub{color:#6b7280;font-size:13px;margin:0 0 20px}"
            + "h2{font-size:18px;color:#0f766e;border-left:4px solid #14b8a6;padding-left:10px;margin:24px 0 12px}"
            + ".cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:12px}"
            + ".card{background:#fff;border-radius:10px;padding:14px 16px;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
            + ".card .label{font-size:12px;color:#6b7280;margin-bottom:4px}"
            + ".card .value{font-size:20px;font-weight:700;color:#111827;word-break:break-all}"
            + ".card .value.gear{color:#1d4ed8}"
            + ".card .value.profile{color:#0891b2}"
            + ".card .value.ench{color:#16a34a}"
            + ".card .value.done{color:#d97706}"
            + ".card .value.warn{color:#dc2626}"
            + ".card .value.cost{color:#f97316}"
            + "ol.timeline{list-style:none;counter-reset:step;padding:0;margin:0}"
            + "ol.timeline li{position:relative;padding:10px 12px 10px 46px;background:#fff;border-radius:8px;"
            + "margin-bottom:8px;box-shadow:0 1px 2px rgba(0,0,0,.06);font-size:14px}"
            + "ol.timeline li::before{counter-increment:step;content:counter(step);position:absolute;left:12px;top:11px;"
            + "width:22px;height:22px;line-height:22px;text-align:center;background:#e2e8f0;color:#475569;"
            + "border-radius:50%;font-size:12px;font-weight:700}"
            + "ol.timeline li.empty{color:#9ca3af;padding-left:16px}"
            + "ol.timeline li.empty::before{display:none}"
            + ".badge{display:inline-block;padding:1px 8px;border-radius:4px;color:#fff;font-size:12px;font-weight:600;margin-right:6px}"
            + ".badge-enchant{background:#16a34a}"
            + ".badge-grind{background:#dc2626}"
            + ".badge-anvil{background:#f97316}"
            + ".badge-done{background:#d97706}"
            + ".badge-warn{background:#e11d48}"
            + ".badge-skip{background:#9ca3af}"
            + ".ench{color:#16a34a;font-weight:600}"
            + ".cost{color:#f97316;font-weight:700}";
    }

    /** 对比汇总表专用样式（表格 + 最优行高亮 + 提示） */
    private static String css对比() {
        return "table.cmp{width:100%;border-collapse:collapse;background:#fff;border-radius:10px;"
            + "overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.08);font-size:14px}"
            + "table.cmp th{background:#0f766e;color:#fff;padding:10px 8px;text-align:left;white-space:nowrap}"
            + "table.cmp th.c{text-align:center}"
            + "table.cmp td{padding:10px 8px;border-top:1px solid #eef2f7;white-space:nowrap}"
            + "table.cmp td.c{text-align:center;font-weight:700;color:#f97316}"
            + "table.cmp td.t{color:#9ca3af;font-size:12px}"
            + "table.cmp tr.best{background:#ecfdf5}"
            + "table.cmp tr.best td:first-child{color:#059669;font-weight:700}"
            + ".hint{color:#6b7280;font-size:13px;margin-top:12px}";
    }

    /** 清理 Windows 非法文件名字符，避免装备名含特殊字符导致写盘失败 */
    private static String 安全文件名(String name) {
        if (name == null || name.isEmpty()) return "装备";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** 单轮统计快照（Gson 反射序列化到对比汇总.json，字段须公开） */
    public static final class 轮次统计 {
        public String 策略;
        public String 方案名;
        public String 时间;
        public int 附魔次数;
        public int 砂轮次数;
        public int 铁砧次数;
        public int 附魔台经验;
        public int 铁砧经验;
        public int 总经验;
        public int 消耗工具;
        public int 浪费工具;
        public int 成品数;
    }
}
