package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import com.mojang.brigadier.tree.CommandNode;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

import java.awt.Desktop;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        .defaultValue(ResourcePackMode.AUTO_DOWNLOAD)
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

    /** 下载线程池。守护线程，退出游戏时不阻塞进程。 */
    private static final ExecutorService DOWNLOAD_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "yiyiaddon-ResourcePackDownloader");
        t.setDaemon(true);
        return t;
    });

    private static final File RESOURCE_PACK_DIR =
        new File(Minecraft.getInstance().gameDirectory, "yiyiaddon_resourcepacks");

    /** 本次连接收到的插件消息频道，用于反作弊频道指纹。 */
    private final Set<String> seenChannels = new LinkedHashSet<>();

    /** 拉回包时间戳环形统计，用于判断反作弊激进程度。 */
    private final long[] rubberBandTimes = new long[10];
    private int rubberBandIndex = 0;
    private int rubberBandTotal = 0;

    private boolean detectionDone = false;

    public ServerDetector() {
        super(CATEGORY_TACTICAL, "服务器检测", "多层指纹识别核心与反作弊，自动白嫖资源包。");
        this.toggleOnBindRelease = true;
    }

    @Override
    public void onActivate() {
        // 单人世界自动关闭
        if (mc.hasSingleplayerServer()) {
            warning("§c单人世界无需检测，已自动关闭");
            toggle();
            return;
        }
        
        if (!RESOURCE_PACK_DIR.exists()) RESOURCE_PACK_DIR.mkdirs();
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
            handleResourcePackRequest(event, packet);
        }
    }

    private void handleResourcePackRequest(PacketEvent.Receive event, ClientboundResourcePackPushPacket packet) {
        switch (resourcePackMode.get()) {
            case BYPASS -> {
                // 拦掉原版处理，直接回「已接受 + 加载成功」，服务端不会踢人也不会渲染
                event.setCancelled(true);
                sendPackAction(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED);
                sendPackAction(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
                notify("已拦截资源包请求（暴力绕过）");
            }
            case AUTO_DOWNLOAD -> {
                event.setCancelled(true);
                sendPackAction(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED);
                TacticalFSM.publishResourcePackDownloadStart(packet.id());
                notify("开始下载资源包，期间已自动压低发包速率");
                downloadAsync(packet.id(), packet.url(), packet.hash());
            }
            case VANILLA -> {
                // 交给原版流程
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  资源包下载：断点续传 + SHA-1 校验
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void downloadAsync(UUID packId, String url, String expectedHash) {
        CompletableFuture.runAsync(() -> {
            File finalFile = new File(RESOURCE_PACK_DIR, "pack_" + packId + ".zip");
            File partFile = new File(RESOURCE_PACK_DIR, "pack_" + packId + ".zip.part");

            try {
                // 已有同 hash 的成品，直接复用，避免跨服重复下载
                if (finalFile.exists() && hashMatches(finalFile, expectedHash)) {
                    finishDownload(packId, true, "资源包已存在，跳过下载");
                    return;
                }

                boolean ok = downloadWithRetry(url, partFile);
                if (!ok) {
                    finishDownload(packId, false, "资源包下载失败，已用尽 " + downloadRetries.get() + " 次重试");
                    return;
                }

                // 服务端给的 hash 为空时跳过校验：部分服务端确实不填这个字段
                if (expectedHash != null && !expectedHash.isEmpty() && !hashMatches(partFile, expectedHash)) {
                    partFile.delete();
                    finishDownload(packId, false, "资源包 SHA-1 校验不匹配，已删除残件");
                    return;
                }

                if (finalFile.exists()) finalFile.delete();
                if (!partFile.renameTo(finalFile)) {
                    finishDownload(packId, false, "资源包存盘失败，无法重命名临时文件");
                    return;
                }

                finishDownload(packId, true, "资源包下载完成：" + finalFile.getName());

            } catch (Exception e) {
                finishDownload(packId, false, "资源包下载异常：" + e);
            }
        }, DOWNLOAD_POOL);
    }

    /**
     * 带退避重试与断点续传的下载。
     *
     * 每次重试都从已有字节数继续请求。服务端返回 206 表示接受续传，
     * 返回 200 说明它不支持 Range，此时必须从头覆写，否则文件会错位。
     */
    private boolean downloadWithRetry(String urlStr, File partFile) {
        int retries = downloadRetries.get();

        for (int attempt = 1; attempt <= retries; attempt++) {
            long offset = (resumeDownload.get() && partFile.exists()) ? partFile.length() : 0L;

            try {
                HttpURLConnection conn = openConnection(urlStr, offset);
                int code = conn.getResponseCode();

                // 416 = Range 无效（文件已完整），直接删除重下
                if (code == 416) {
                    conn.disconnect();
                    partFile.delete();
                    continue;
                }

                // 206 = 接受续传；200 = 不支持续传，从头下
                boolean append = code == HttpURLConnection.HTTP_PARTIAL;
                if (code != HttpURLConnection.HTTP_OK && !append) {
                    conn.disconnect();
                    backoff(attempt);
                    continue;
                }
                if (!append && offset > 0) {
                    // 服务端忽略了 Range，之前的残件不能要了
                    partFile.delete();
                }

                try (InputStream in = conn.getInputStream();
                     RandomAccessFile out = new RandomAccessFile(partFile, "rw")) {

                    out.seek(append ? offset : 0L);
                    if (!append) out.setLength(0L);

                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                conn.disconnect();
                return true;

            } catch (Exception e) {
                // 断在中途也没关系，残件留着给下一轮续传
                backoff(attempt);
            }
        }
        return false;
    }

    private HttpURLConnection openConnection(String urlStr, long offset) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        // 部分 CDN 会对非常规 UA 返回 403，这里伪装成普通浏览器
        conn.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Encoding", "identity"); // 避免压缩导致 Range 偏移错乱
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(downloadTimeout.get() * 1000);
        conn.setInstanceFollowRedirects(true);
        if (offset > 0) conn.setRequestProperty("Range", "bytes=" + offset + "-");
        return conn;
    }

    /** 指数退避，避免对着挂掉的 CDN 连打。 */
    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(8000L, 500L * (1L << (attempt - 1))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean hashMatches(File file, String expectedHash) throws Exception {
        if (expectedHash == null || expectedHash.isEmpty()) return false;
        return sha1(file).equalsIgnoreCase(expectedHash);
    }

    private String sha1(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * 收尾：回主线程发状态包与提示。
     * 发包与聊天输出都不是线程安全的，不能在下载线程里直接做。
     */
    private void finishDownload(UUID packId, boolean success, String message) {
        mc.execute(() -> {
            if (success) {
                // 原版顺序是 ACCEPTED → DOWNLOADED → SUCCESSFULLY_LOADED，
                // 少发中间那步会让状态机跳变，部分服务端据此判定客户端异常
                sendPackAction(packId, ServerboundResourcePackPacket.Action.DOWNLOADED);
                sendPackAction(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
            } else {
                sendPackAction(packId, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD);
            }

            TacticalFSM.publishResourcePackDownloadComplete(packId, success);

            if (success) notify(message);
            else notifyError(message);
        });
    }

    private void sendPackAction(UUID packId, ServerboundResourcePackPacket.Action action) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return;
        connection.send(new ServerboundResourcePackPacket(packId, action));
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

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            table -> {
                WButton openFolder = theme.button("打开服务器资源库");
                openFolder.action = this::openResourcePackFolder;
                table.add(openFolder).expandX();
                table.row();
            },
            new String[]{ "🔍 §l服务器检测 · 使用说明" },
            new String[]{
                "§e§l▌ 侦测结果",
                "§f  · 核心：§a" + TacticalFSM.getDetectedServerCore(),
                "§f  · 反作弊：§c" + TacticalFSM.getDetectedAntiCheat(),
                "§f  · 已记录插件频道：§e" + seenChannels.size() + "§f 个",
                "§f  · 近期拉回次数：§e" + rubberBandTotal,
                "§f  · 侦测状态：" + (detectionDone ? "§a已完成" : "§7等待进服")
            },
            new String[]{
                "§a§l▌ 识别层级（可信度由高到低）",
                "§f  1. 插件频道 — 反作弊主动开的校验通道，命中基本确诊",
                "§f  2. 指令树 — 插件注册的实际结果，伪造成本高，主要依据",
                "§f  3. brand / version — 服务端可随手改写，仅作线索",
                "§f  4. 拉回频率 — 只能确认存在移动校验，认不出型号"
            },
            new String[]{
                "§b§l▌ 资源包模式",
                "§f  · §e暴力绕过§f — 回假包骗过服务端，不下载不渲染",
                "§f  · §e自动白嫖§f — 异步下载存本地，SHA-1 校验后回成功",
                "§f  · §e原版处理§f — 不干预，走原版弹窗流程"
            },
            new String[]{
                "§d§l▌ 下载增强",
                "§f  · 断点续传：重试时用 Range 接着传，大包不必从零开始",
                "§f    服务端不支持 Range 时会自动改为整包重下",
                "§f  · 重试 §e" + downloadRetries.get() + "§f 次，指数退避（0.5s 起，上限 8s）",
                "§f  · 读取超时 §e" + downloadTimeout.get() + "§f 秒，慢速服建议调大",
                "§f  · 伪装浏览器 UA，绕过 CDN 的 403 拦截"
            },
            new String[]{
                "§c§l▌ 注意",
                "§f  · 指令树需等服务端下发完成，侦测延迟太短会漏判",
                "§f  · 反作弊报「未发现」只代表没抓到指纹，不等于没装",
                "§f  · 侦测到高风险反作弊会自动通知飞行模块降级"
            }
        );
    }

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
