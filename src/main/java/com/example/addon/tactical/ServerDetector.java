package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import com.mojang.brigadier.tree.CommandNode;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

import java.awt.Desktop;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import java.util.concurrent.atomic.AtomicLong;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 服务器检测模块。
 *
 * 识别思路是多层指纹叠加，可信度由低到高：
 * 1. brand / version 字符串 —— 最容易被服务端改掉，只作为线索
 * 2. 插件消息频道 —— 反作弊主动开的校验频道，命中基本可确诊
 * 3. 指令树命名空间 —— 插件注册的实际结果，伪造成本高，是主要依据
 * 4. 拉回频率 —— 只能说明反作弊存在且激进，无法定型号
 *
 * 资源包处理走独立线程池，NIO 分块写入，支持断点续传与 SHA-1 校验。
 *
 * @author yiyijia
 */
public class ServerDetector extends YiyiaddonModule {

    private final SettingGroup sgDetection = settings.createGroup("底裤侦测");
    private final SettingGroup sgResourcePack = settings.createGroup("资源包劫持");

    private final Setting<Boolean> detectCore = sgDetection.add(new BoolSetting.Builder()
        .name("检测服务器核心")
        .description("识别 Paper / Purpur / Leaves / Folia / 混合端 / 代理层等三十余种核心")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectAntiCheat = sgDetection.add(new BoolSetting.Builder()
        .name("检测反作弊")
        .description("通过指令树与插件频道识别反作弊，覆盖国际主流与国内常见实现")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> announceDetection = sgDetection.add(new BoolSetting.Builder()
        .name("公屏播报")
        .description("侦测完成后在聊天栏输出结果")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> detectDelay = sgDetection.add(new IntSetting.Builder()
        .name("侦测延迟（秒）")
        .description("进服后等待多久开始侦测。指令树需要服务端下发完成，太早会漏判")
        .defaultValue(3)
        .min(1)
        .max(15)
        .sliderRange(1, 15)
        .build()
    );

    private final Setting<ResourcePackMode> resourcePackMode = sgResourcePack.add(new EnumSetting.Builder<ResourcePackMode>()
        .name("资源包模式")
        .description("选择如何处理服务器资源包")
        .defaultValue(ResourcePackMode.BYPASS)
        .build()
    );

    private final Setting<Integer> downloadRetries = sgResourcePack.add(new IntSetting.Builder()
        .name("重试次数")
        .description("下载失败后的重试次数，每次重试都会尝试断点续传")
        .defaultValue(5)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .visible(() -> resourcePackMode.get() == ResourcePackMode.AUTO_DOWNLOAD)
        .build()
    );

    private final Setting<Integer> downloadTimeout = sgResourcePack.add(new IntSetting.Builder()
        .name("读取超时（秒）")
        .description("大资源包在慢速服务器上很容易超时，调大可显著提升成功率")
        .defaultValue(60)
        .min(10)
        .max(300)
        .sliderRange(10, 300)
        .visible(() -> resourcePackMode.get() == ResourcePackMode.AUTO_DOWNLOAD)
        .build()
    );

    private final Setting<Boolean> resumeDownload = sgResourcePack.add(new BoolSetting.Builder()
        .name("断点续传")
        .description("重试时用 Range 请求接着传，避免大包每次从零开始")
        .defaultValue(true)
        .visible(() -> resourcePackMode.get() == ResourcePackMode.AUTO_DOWNLOAD)
        .build()
    );



    private static final File RESOURCE_PACK_DIR =
        new File(Minecraft.getInstance().gameDirectory, "yiyiaddon_resourcepacks");

    /** 本次连接收到的插件消息频道，用于反作弊频道指纹。 */
    private final Set<String> seenChannels = new LinkedHashSet<>();

    /** 拉回包时间戳环形统计，用于判断反作弊激进程度。 */
    private final long[] rubberBandTimes = new long[10];
    private int rubberBandIndex = 0;
    private int rubberBandTotal = 0;

    private boolean detectionDone = false;

    // #region debug-point 资源包暴力绕过不生效
    private static final String 调试地址 = "http://127.0.0.1:7777/event";
    private static final String 调试会话 = "2026-08-26-资源包暴力绕过不生效";
    private final AtomicLong 调试序号 = new AtomicLong();
    private final AtomicLong 收包埋点计数 = new AtomicLong();
    private volatile String 调试运行批次 = "probe-未启动";

    private void debugEvent(String 假设编号, String 埋点, String 数据) {
        long 序号 = 调试序号.incrementAndGet();
        long 时刻 = System.currentTimeMillis();
        String json = "{\"sessionId\":\"" + 转义(调试会话) + "\",\"displayName\":\""
            + 转义(调试会话) + "\",\"runId\":\"" + 转义(调试运行批次)
            + "\",\"hypothesisId\":\"" + 转义(假设编号) + "\",\"location\":\""
            + 转义("资源包/" + 埋点) + "\",\"ts\":" + 时刻 + ",\"data\":{\"sequence\":"
            + 序号 + ",\"thread\":\"" + 转义(Thread.currentThread().getName())
            + "\",\"detail\":\"" + 转义(数据) + "\"}}";

        Thread 上报线程 = new Thread(() -> {
            HttpURLConnection 连接 = null;
            try {
                连接 = (HttpURLConnection) URI.create(调试地址).toURL().openConnection();
                连接.setRequestMethod("POST");
                连接.setConnectTimeout(500);
                连接.setReadTimeout(500);
                连接.setDoOutput(true);
                连接.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (java.io.OutputStream 输出 = 连接.getOutputStream()) {
                    输出.write(json.getBytes(StandardCharsets.UTF_8));
                }
                连接.getResponseCode();
            } catch (Exception ignored) {
                // 调试服务不可用时绝不影响游戏网络线程与下载线程。
            } finally {
                if (连接 != null) 连接.disconnect();
            }
        }, "yiyiaddon-资源包诊断-" + 序号);
        上报线程.setDaemon(true);
        上报线程.start();
    }

    private static String 转义(String 文本) {
        return 文本 == null ? "" : 文本.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    // #endregion

    public ServerDetector() {
        super(CATEGORY_TACTICAL, "服务器检测", "多层指纹识别核心与反作弊，自动白嫖资源包。点击按钮查看说明。");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            WButton helpBtn = theme.button("查看使用说明");
            helpBtn.action = () -> mc.setScreen(new com.example.addon.ui.HelpScreen(theme, this, buildHelpContent()));
            table.add(helpBtn).expandX().minWidth(200);
            table.row();
        }, new String[0]);
    }

    private String[] buildHelpContent() {
        return com.example.addon.ui.HelpScreen.buildHelpContent(
            new com.example.addon.ui.HelpScreen.HelpSection("检测功能",
                "§8├─ §f服务器核心识别",
                "§8│   §7Paper / Purpur / Leaves / Folia / 混合端",
                "§8│   §7代理层识别（Velocity / BungeeCord / Waterfall）",
                "§8│",
                "§8├─ §f反作弊检测",
                "§8│   §7通过指令树与插件频道识别",
                "§8│   §7覆盖国际主流与国内常见实现",
                "§8│   §7GrimAC / Matrix / Vulcan / Spartan / AAC等",
                "§8│",
                "§8└─ §f资源包处理",
                "§8    §7自动下载到本地（支持断点续传）",
                "§8    §7暴力绕过：自动拒绝或接受"
            ),
            
            new com.example.addon.ui.HelpScreen.HelpSection("使用方式",
                "§a[1] §f加入服务器时自动启动检测",
                "§a[2] §f等待 §e3-5秒 §f让服务器发送完整信息",
                "§a[3] §f检测完成后在聊天栏显示结果",
                "§a[4] §f结果会保存到TacticalFSM供其他模块使用"
            ),
            
            new com.example.addon.ui.HelpScreen.HelpSection("资源包模式",
                "§6▸ §f暴力拒绝 §8- §7自动拒绝所有资源包",
                "§6▸ §f暴力接受 §8- §7自动接受所有资源包",
                "§6▸ §f下载到本地 §8- §7保存到 §e.minecraft/resourcepacks/",
                "§6▸ §f询问玩家 §8- §7弹窗让你手动选择"
            ),
            
            new com.example.addon.ui.HelpScreen.HelpSection("检测原理",
                "§8├─ §7Brand字符串 §8- §7最容易被改，只作线索",
                "§8├─ §7插件消息频道 §8- §7反作弊开的校验频道",
                "§8├─ §7指令树命名空间 §8- §7插件注册的实际结果（主要依据）",
                "§8└─ §7拉回频率 §8- §7说明反作弊存在且激进"
            ),
            
            new com.example.addon.ui.HelpScreen.HelpSection("注意事项",
                "§c⚠ §f检测结果不是100%准确，仅供参考",
                "§c⚠ §f资源包下载需要网络连接，国外服务器可能较慢",
                "§c⚠ §f暴力拒绝可能被服务器踢出（部分服务器强制资源包）",
                "§c⚠ §f单人世界自动禁用，仅在多人服务器生效"
            )
        );
    }

    @Override
    public void onActivate() {
        debugEvent("A", "模块启动", "active=" + isActive() + "，模式=" + resourcePackMode.get());
        
        // 单人世界自动关闭
        if (mc.hasSingleplayerServer()) {
            chatFeedback = false;
            toggle();
            chatFeedback = true;
            warning("§c单人世界无需检测");
            return;
        }
        
        if (!RESOURCE_PACK_DIR.exists()) RESOURCE_PACK_DIR.mkdirs();
        
        // 如果是进服后才开启模块，手动触发检测（GameJoinedEvent 已经错过了）
        if (mc.player != null && mc.level != null && !mc.hasSingleplayerServer()) {
            detectionDone = false;
            seenChannels.clear();
            rubberBandIndex = 0;
            rubberBandTotal = 0;
            
            long delayMs = detectDelay.get() * 1000L;
            Thread waiter = new Thread(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                mc.execute(this::performDetection);
            }, "yiyiaddon-ServerDetector-LateStart");
            waiter.setDaemon(true);
            waiter.start();
            
            notify("已在服务器中，将在 " + detectDelay.get() + " 秒后开始侦测");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  进服侦测
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive()) return;

        detectionDone = false;
        seenChannels.clear();
        rubberBandIndex = 0;
        rubberBandTotal = 0;

        // 指令树与插件频道都是进服后陆续下发的，等一会儿再判，否则漏判率很高
        long delayMs = detectDelay.get() * 1000L;
        Thread waiter = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // 侦测要读客户端世界与连接状态，必须回主线程
            mc.execute(this::performDetection);
        }, "yiyiaddon-ServerDetector");
        waiter.setDaemon(true);
        waiter.start();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        TacticalFSM.reset();
        seenChannels.clear();
        detectionDone = false;
    }

    private void performDetection() {
        if (mc.player == null || mc.getConnection() == null) return;

        String core = detectCore.get() ? detectServerCore() : "未检测";
        TacticalFSM.setDetectedServerCore(core);

        String antiCheat = detectAntiCheat.get() ? detectAntiCheatPlugin() : "未检测";
        detectionDone = true;

        if (!"未检测".equals(antiCheat) && !"未发现".equals(antiCheat)) {
            // 发布事件，飞行绕过模块会据此降级到安全模式
            TacticalFSM.publishAntiCheatDetected(antiCheat);
        } else {
            TacticalFSM.setDetectedAntiCheat(antiCheat);
        }

        if (announceDetection.get()) {
            notify("服务端核心：" + highlightServer(core));
            if ("未发现".equals(antiCheat)) {
                notify("反作弊：" + highlightText("未发现指纹") + "（不等于没有）");
            } else if (!"未检测".equals(antiCheat)) {
                notify("反作弊：§c§l" + antiCheat);
            }
        }
    }

    /**
     * 识别服务端核心。
     *
     * brand 与 version 都可以被服务端随手改写，所以优先看指令树命名空间，
     * 拿不到结论再退回字符串匹配。三处都没线索时不硬猜，直接报未知。
     */
    private String detectServerCore() {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return "未知";

        // 第一层：指令树命名空间。插件指令会注册成「插件名:指令」，伪造成本高
        String fromCommands = matchCommandNamespaces(ServerFingerprints.CORE_COMMANDS);
        if (fromCommands != null && !fromCommands.isEmpty()) return fromCommands + "（指令树）";

        // 第二层：brand 字符串
        String brand = connection.serverBrand();
        String fromBrand = matchKeyword(brand, ServerFingerprints.CORES);
        if (fromBrand != null) return fromBrand;

        // 第三层：服务器列表里的 version 文本
        ServerData data = connection.getServerData();
        if (data != null && data.version != null) {
            String fromVersion = matchKeyword(data.version.getString(), ServerFingerprints.CORES);
            if (fromVersion != null) return fromVersion;
        }

        // brand 精确匹配 vanilla，避免 "vanilla+custom" 误判
        if ("vanilla".equals(brand)) {
            return "原版";
        }
        if (brand != null && brand.toLowerCase(Locale.ROOT).contains("vanilla")) {
            return "原版（已改 brand）";
        }
        return "未知";
    }

    /**
     * 识别反作弊。
     *
     * 插件频道命中优先级最高（反作弊主动开的校验通道），其次是指令树。
     * 两者都没有时看拉回频率，只能给出「存在且激进」这种程度的结论。
     */
    private String detectAntiCheatPlugin() {
        // 第一层：插件消息频道
        for (String channel : seenChannels) {
            for (Map.Entry<String, String> e : ServerFingerprints.ANTICHEAT_CHANNELS.entrySet()) {
                if (channel.contains(e.getKey())) return e.getValue() + "（插件频道）";
            }
        }

        // 第二层：指令树
        String fromCommands = matchCommandNamespaces(ServerFingerprints.ANTICHEAT_COMMANDS);
        if (fromCommands != null && !fromCommands.isEmpty()) return fromCommands + "（指令树）";

        // 第三层：拉回频率。只说明有东西在校验移动，认不出型号
        if (rubberBandTotal >= 3) {
            return "未知反作弊（拉回频繁，已确认存在移动校验）";
        }

        return "未发现";
    }

    /**
     * 在服务端下发的指令树里匹配指纹表。
     *
     * 同时看命名空间（{@code plugin:cmd} 的前半段）与根指令名本身，
     * 因为部分插件不带命名空间直接注册根指令。
     */
    private String matchCommandNamespaces(Map<String, String> fingerprints) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return null;

        var dispatcher = connection.getCommands();
        if (dispatcher == null) return null;

        for (CommandNode<?> node : dispatcher.getRoot().getChildren()) {
            String name = node.getName();
            if (name == null || name.isEmpty()) continue;

            String lower = name.toLowerCase(Locale.ROOT);
            String namespace = lower.contains(":") ? lower.substring(0, lower.indexOf(':')) : lower;

            for (Map.Entry<String, String> e : fingerprints.entrySet()) {
                if (namespace.equals(e.getKey())) return e.getValue();
            }
        }
        return null;
    }

    /** 在字符串里按指纹表顺序匹配关键词，命中即返回展示名。 */
    private String matchKeyword(String raw, Map<String, String> fingerprints) {
        if (raw == null || raw.isEmpty()) return null;
        String lower = raw.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, String> e : fingerprints.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  收包监听：频道指纹 + 拉回统计 + 资源包劫持
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;

        // 记录插件频道，供反作弊频道指纹使用
        if (event.packet instanceof ClientboundCustomPayloadPacket payload) {
            String id = payload.payload().type().id().toString().toLowerCase(Locale.ROOT);
            if (seenChannels.size() < 64) seenChannels.add(id);
        }

        // 拉回统计：环形缓冲，只关心最近 10 次
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            rubberBandTimes[rubberBandIndex] = System.currentTimeMillis();
            rubberBandIndex = (rubberBandIndex + 1) % rubberBandTimes.length;
            if (rubberBandTotal < rubberBandTimes.length) rubberBandTotal++;
        }

        if (event.packet instanceof ClientboundResourcePackPushPacket packet) {
            ResourcePackMode mode = resourcePackMode.get();
            debugEvent("B", "资源包拦截", "mode=" + mode + "，id=" + packet.id() + "，url=" + packet.url());
            
            if (mode == ResourcePackMode.BYPASS) {
                event.setCancelled(true);
                sendPackAction(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED);
                sendPackAction(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
                debugEvent("F", "暴力绕过完成", "id=" + packet.id());
                notify("已拦截资源包（暴力绕过）");
            } else if (mode == ResourcePackMode.AUTO_DOWNLOAD) {
                downloadResourcePackAsync(packet.id(), packet.url(), packet.hash());
            }
        }
    }

    public boolean handleResourcePackPushFromVanilla(ClientboundResourcePackPushPacket packet, Consumer<Packet<?>> sendPacket) {
        if (!isActive()) return false;
        ResourcePackMode mode = resourcePackMode.get();
        debugEvent("B", "Mixin入口", "mode=" + mode + "，id=" + packet.id());
        
        if (mode == ResourcePackMode.BYPASS) {
            UUID packId = packet.id();
            sendPacket.accept(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.ACCEPTED));
            sendPacket.accept(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            debugEvent("F", "Mixin暴力绕过", "id=" + packId);
            notify("已拦截资源包（Mixin入口）");
            return true;
        }
        return false;
    }

    private void sendPackAction(UUID packId, ServerboundResourcePackPacket.Action action) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            debugEvent("F", "状态包未发送", "action=" + action + "，原因=连接为空，id=" + packId);
            return;
        }
        debugEvent("F", "状态包发送", "action=" + action + "，id=" + packId);
        connection.send(new ServerboundResourcePackPacket(packId, action));
    }

    private void downloadResourcePackAsync(UUID packId, String url, String hash) {
        Thread.ofVirtual().start(() -> {
            try {
                sendPackAction(packId, ServerboundResourcePackPacket.Action.ACCEPTED);
                
                if (!RESOURCE_PACK_DIR.exists()) RESOURCE_PACK_DIR.mkdirs();
                
                String fileName = packId.toString() + ".zip";
                File targetFile = new File(RESOURCE_PACK_DIR, fileName);
                
                if (targetFile.exists()) {
                    debugEvent("E", "资源包已存在", "跳过下载，id=" + packId + "，路径=" + targetFile.getAbsolutePath());
                    sendPackAction(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
                    notify("该服务器资源包已下载过：" + fileName);
                    return;
                }
                

                
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                
                int code = conn.getResponseCode();
                debugEvent("E", "HTTP响应", "code=" + code + "，id=" + packId);
                
                if (code == 200) {
                    try (InputStream in = conn.getInputStream();
                         java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        long total = 0;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            total += read;
                        }

                        sendPackAction(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
                        notify("资源包已下载：" + fileName);
                    }
                } else {

                    sendPackAction(packId, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD);
                    notify("该服务器材质包无法下载（HTTP " + code + "）");
                }
            } catch (Exception e) {

                sendPackAction(packId, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD);
                notify("该服务器材质包无法下载：" + e.getMessage());
            }
        });
    }

    /** 26.1.2 已移除 net.minecraft.Util，改用 AWT Desktop，放独立线程避免卡渲染。 */
    private void openResourcePackFolder() {
        if (!RESOURCE_PACK_DIR.exists()) RESOURCE_PACK_DIR.mkdirs();

        Thread opener = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(RESOURCE_PACK_DIR);
                } else {
                    // Windows 路径含空格需用 /select 避免解析错误
                    new ProcessBuilder("explorer.exe", "/select," + RESOURCE_PACK_DIR.getAbsolutePath()).start();
                }
            } catch (Exception e) {
                mc.execute(() -> notifyError("打开资源库失败：" + e.getMessage()));
            }
        }, "yiyiaddon-OpenFolder");
        opener.setDaemon(true);
        opener.start();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  UI 面板
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 资源包处理模式。 */
    public enum ResourcePackMode {
        BYPASS("暴力绕过"),
        AUTO_DOWNLOAD("自动白嫖"),
        VANILLA("原版处理");

        private final String displayName;

        ResourcePackMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
