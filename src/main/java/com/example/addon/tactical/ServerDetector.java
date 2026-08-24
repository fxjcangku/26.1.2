package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
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
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 服务器检测模块（加强版）
 * 
 * 功能：
 * 1. 服务器核心侦测（Paper/Purpur/Leaves/Spigot/Folia/CraftBukkit）
 * 2. 反作弊侦测（GrimAC/Matrix/Vulcan/Grim2/AAC/Themis）
 * 3. NIO 异步资源包下载 + SHA-1 校验
 * 4. 一键打开资源包文件夹
 * 
 * @author yiyijia
 */
public class ServerDetector extends YiyiaddonModule {

    private final SettingGroup sgDetection = settings.createGroup("底裤侦测");
    private final SettingGroup sgResourcePack = settings.createGroup("资源包劫持");

    // 底裤侦测设置
    private final Setting<Boolean> detectCore = sgDetection.add(new BoolSetting.Builder()
        .name("检测服务器核心")
        .description("识别 Paper/Purpur/Leaves/Spigot/Folia/CraftBukkit")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectAntiCheat = sgDetection.add(new BoolSetting.Builder()
        .name("检测反作弊")
        .description("嗅探 GrimAC/Matrix/Vulcan/Grim2/AAC/Themis 指纹")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> announceDetection = sgDetection.add(new BoolSetting.Builder()
        .name("公屏播报")
        .description("检测到结果后在聊天栏显示")
        .defaultValue(true)
        .build()
    );

    // 资源包劫持设置
    private final Setting<ResourcePackMode> resourcePackMode = sgResourcePack.add(new EnumSetting.Builder<ResourcePackMode>()
        .name("资源包模式")
        .description("选择如何处理服务器资源包")
        .defaultValue(ResourcePackMode.AUTO_DOWNLOAD)
        .build()
    );

    // 异步线程池（NIO 下载专用）
    private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "yiyiaddon-ResourcePackDownloader");
        t.setDaemon(true);
        return t;
    });

    // 资源包保存目录
    private static final File RESOURCE_PACK_DIR = new File(Minecraft.getInstance().gameDirectory, "yiyiaddon_resourcepacks");

    public ServerDetector() {
        super(CATEGORY_TACTICAL, "服务器检测", "侦测服务器核心/反作弊类型，自动下载资源包。");
    }

    @Override
    public void onActivate() {
        // 确保资源包目录存在
        if (!RESOURCE_PACK_DIR.exists()) {
            RESOURCE_PACK_DIR.mkdirs();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  进服侦测
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (!isActive()) return;

        // 延迟 2 秒后开始侦测（等待服务器完全加载）
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                performDetection();
            } catch (InterruptedException ignored) {}
        }, "yiyiaddon-ServerDetector").start();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // 离开服务器时重置状态
        TacticalFSM.reset();
    }

    /**
     * 执行服务器核心与反作弊侦测
     */
    private void performDetection() {
        if (mc.player == null) return;

        StringBuilder report = new StringBuilder();
        report.append("§c§l[服务器检测]§r\n");

        // 1. 服务器核心侦测
        if (detectCore.get()) {
            String core = detectServerCore();
            TacticalFSM.setDetectedServerCore(core);
            report.append("§e服务器核心: §f").append(core).append("\n");
        }

        // 2. 反作弊侦测
        if (detectAntiCheat.get()) {
            String antiCheat = detectAntiCheatPlugin();
            if (!antiCheat.equals("未检测到")) {
                TacticalFSM.publishAntiCheatDetected(antiCheat);
                report.append("§c反作弊插件: §f").append(antiCheat).append("\n");
            } else {
                report.append("§a反作弊插件: §f").append(antiCheat).append("\n");
            }
        }

        // 3. 公屏播报
        if (announceDetection.get()) {
            notify(report.toString());
        }
    }

    /**
     * 侦测服务器核心类型
     * 
     * 检测方法：
     * 1. ServerData.version 字段（Paper 会返回 "Paper 1.21.2" 等）
     * 2. Brand 字符串（Purpur/Leaves 会暴露）
     * 3. 特征包指纹（BlockChangedAck 是 Paper+ 独有）
     */
    private String detectServerCore() {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return "未知";

        // 方法 1: 检查 ServerData.version
        ServerData serverData = connection.getServerData();
        if (serverData != null && serverData.version != null) {
            String versionStr = serverData.version.getString().toLowerCase();
            if (versionStr.contains("paper")) return "Paper";
            if (versionStr.contains("purpur")) return "Purpur";
            if (versionStr.contains("leaves")) return "Leaves";
            if (versionStr.contains("folia")) return "Folia";
            if (versionStr.contains("spigot")) return "Spigot";
        }

        // 方法 2: 检查 Brand
        String brand = connection.serverBrand();
        if (brand != null) {
            String lowerBrand = brand.toLowerCase();
            if (lowerBrand.contains("paper")) return "Paper";
            if (lowerBrand.contains("purpur")) return "Purpur";
            if (lowerBrand.contains("leaves")) return "Leaves";
            if (lowerBrand.contains("folia")) return "Folia";
            if (lowerBrand.contains("spigot")) return "Spigot";
            if (lowerBrand.contains("craftbukkit")) return "CraftBukkit";
        }

        // 方法 3: 包指纹检测（通过 Mixin 监听是否收到过 BlockChangedAck）
        // 这需要在 PacketEvent.Receive 中记录，此处简化为未知
        return "原版/未知";
    }

    /**
     * 侦测反作弊插件
     * 
     * 检测方法：
     * 1. 收到 BlockChangedAck 包 → Paper+ 核心 → 可能有 GrimAC
     * 2. 拉回包频率 > 3次/10秒 → 可能有 Matrix
     * 3. Brand 字符串包含反作弊关键词（部分服务器会暴露）
     */
    private String detectAntiCheatPlugin() {
        // 简化实现：通过服务器核心推测
        String core = TacticalFSM.getDetectedServerCore();
        if (core.contains("Paper") || core.contains("Purpur") || core.contains("Leaves")) {
            return "疑似 GrimAC/Vulcan（Paper系）";
        }
        if (core.contains("Spigot")) {
            return "疑似 Matrix/AAC（Spigot系）";
        }
        return "未检测到";
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  资源包劫持（NIO 异步下载 + SHA-1 校验）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;

        // 拦截资源包请求
        if (event.packet instanceof ClientboundResourcePackPushPacket packet) {
            handleResourcePackRequest(packet);

            // 根据模式决定是否拦截
            if (resourcePackMode.get() == ResourcePackMode.BYPASS) {
                event.setCancelled(true);
                sendFakeAccept(packet.id());
            } else if (resourcePackMode.get() == ResourcePackMode.AUTO_DOWNLOAD) {
                event.setCancelled(true);
                downloadResourcePackAsync(packet);
            }
        }
    }

    /**
     * 处理资源包请求（模式分发）
     */
    private void handleResourcePackRequest(ClientboundResourcePackPushPacket packet) {
        UUID packId = packet.id();
        String url = packet.url();
        String hash = packet.hash();

        switch (resourcePackMode.get()) {
            case BYPASS:
                notify("§e已拦截资源包请求（暴力绕过模式）");
                sendFakeAccept(packId);
                sendFakeLoaded(packId);
                break;

            case AUTO_DOWNLOAD:
                notify("§a开始下载资源包: " + url);
                TacticalFSM.publishResourcePackDownloadStart(packId);
                downloadResourcePackAsync(packet);
                break;

            case VANILLA:
                // 不拦截，原版处理
                break;
        }
    }

    /**
     * NIO 异步下载资源包
     */
    private void downloadResourcePackAsync(ClientboundResourcePackPushPacket packet) {
        UUID packId = packet.id();
        String url = packet.url();
        String expectedHash = packet.hash();

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 构造文件名
                String fileName = "pack_" + packId + ".zip";
                File targetFile = new File(RESOURCE_PACK_DIR, fileName);

                // 2. 检查是否已存在且 Hash 匹配
                if (targetFile.exists()) {
                    String localHash = calculateSHA1(targetFile);
                    if (localHash.equalsIgnoreCase(expectedHash)) {
                        notify("§a资源包已存在，跳过下载");
                        sendFakeLoaded(packId);
                        TacticalFSM.publishResourcePackDownloadComplete(packId, true);
                        return;
                    }
                }

                // 3. 下载文件（NIO + 重试机制）
                boolean success = downloadWithRetry(url, targetFile, 3);
                if (!success) {
                    notifyError("资源包下载失败（3次重试均失败）");
                    TacticalFSM.publishResourcePackDownloadComplete(packId, false);
                    return;
                }

                // 4. 校验 SHA-1
                String actualHash = calculateSHA1(targetFile);
                if (!actualHash.equalsIgnoreCase(expectedHash)) {
                    notifyError("资源包 Hash 校验失败（期望: " + expectedHash + "，实际: " + actualHash + "）");
                    targetFile.delete();
                    TacticalFSM.publishResourcePackDownloadComplete(packId, false);
                    return;
                }

                // 5. 发送成功加载包
                sendFakeLoaded(packId);
                notify("§a资源包下载完成: " + fileName);
                TacticalFSM.publishResourcePackDownloadComplete(packId, true);

            } catch (Exception e) {
                notifyError("资源包下载异常: " + e.getMessage());
                TacticalFSM.publishResourcePackDownloadComplete(packId, false);
            }
        }, downloadExecutor);
    }

    /**
     * 下载文件（带重试 + 302 重定向跟随）
     */
    private boolean downloadWithRetry(String urlStr, File target, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                URI uri = new URI(urlStr);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true); // 自动跟随 302

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    if (attempt < maxRetries) continue;
                    return false;
                }

                // NIO 流式下载
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                return true;

            } catch (Exception e) {
                if (attempt == maxRetries) {
                    e.printStackTrace();
                    return false;
                }
                try {
                    Thread.sleep(1000 * attempt); // 递增延迟
                } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }

    /**
     * 计算文件 SHA-1 哈希
     */
    private String calculateSHA1(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream fis = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 发送假"已接受"包
     */
    private void sendFakeAccept(UUID packId) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.ACCEPTED));
    }

    /**
     * 发送假"加载成功"包
     */
    private void sendFakeLoaded(UUID packId) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
    }

    /**
     * 打开本地资源包文件夹
     * 26.1.2 已移除 net.minecraft.Util，改用 AWT Desktop，放到独立线程避免卡渲染线程
     */
    private void openResourcePackFolder() {
        if (!RESOURCE_PACK_DIR.exists()) RESOURCE_PACK_DIR.mkdirs();

        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(RESOURCE_PACK_DIR);
                } else {
                    new ProcessBuilder("explorer.exe", RESOURCE_PACK_DIR.getAbsolutePath()).start();
                }
            } catch (Exception e) {
                notifyError("打开资源库失败：" + e.getMessage());
            }
        }, "yiyiaddon-OpenFolder").start();
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
            new String[]{ "§l服务器检测 · 功能说明" },
            new String[]{
                "§e§l▌ 底裤侦测",
                "§f  · 服务器核心: §a" + TacticalFSM.getDetectedServerCore(),
                "§f  · 反作弊插件: §c" + TacticalFSM.getDetectedAntiCheat()
            },
            new String[]{
                "§a§l▌ 资源包劫持",
                "§f  · §e暴力绕过§r: 拦截弹窗，不下载",
                "§f  · §e自动白嫖§r: 异步下载 + SHA-1 校验",
                "§f  · §e原版处理§r: 不拦截，交给原版"
            },
            new String[]{
                "§b§l▌ 核心列表",
                "§f  Paper / Purpur / Leaves / Spigot / Folia / CraftBukkit"
            },
            new String[]{
                "§d§l▌ 反作弊列表",
                "§f  GrimAC / Matrix / Vulcan / Grim2 / AAC / Themis"
            }
        );
    }

    /** 资源包处理模式 */
    public enum ResourcePackMode {
        BYPASS("暴力绕过"),
        AUTO_DOWNLOAD("自动白嫖"),
        VANILLA("原版处理");

        public final String displayName;

        ResourcePackMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
