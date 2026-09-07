package com.example.addon.tactical;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.tactical.core.ServerFingerprints;
import com.example.addon.tactical.core.TacticalCoordinator;
import com.mojang.brigadier.tree.CommandNode;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static com.example.addon.core.AddonTemplate.CATEGORY_TACTICAL;

/**
 * 服务器检测模块（L0 观测层，被动只读）。
 *
 * 职责边界（2026-09-03 重构后）：
 * - 只采集「观测事实」：服务器核心 / 反作弊指纹 / 插件频道 / 资源包；
 * - 检测结果通过 {@link TacticalCoordinator#reportDetection} 上报，
 *   带会话代次校验，跨服迟到的结果直接丢弃（审计 P2-1）；
 * - 不写任何执行状态（冷却/模式/抑制一律归 L1 协调器），
 *   TPS 采样与拉回统计已移交协调器常驻处理（审计 P0-5、P2-8）。
 *
 * 识别思路是多层指纹叠加，可信度由低到高：
 * 1. brand / version 字符串 —— 最容易被服务端改掉，只作为线索
 * 2. 插件消息频道 —— 反作弊主动开的校验频道，命中基本可确诊
 * 3. 指令树命名空间 —— 插件注册的实际结果，伪造成本高，是主要依据
 * 4. 拉回频率 —— 只能说明反作弊存在且激进，无法定型号
 *
 * 资源包处理走独立线程池，NIO 分块写入，支持断点续传与 SHA-1 校验。
 * （与绕过主线解耦的独立功能，保留原实现迁入）
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
        .noSlider()
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
        .noSlider()
        .visible(() -> resourcePackMode.get() == ResourcePackMode.AUTO_DOWNLOAD)
        .build()
    );

    private final Setting<Integer> downloadTimeout = sgResourcePack.add(new IntSetting.Builder()
        .name("读取超时（秒）")
        .description("大资源包在慢速服务器上很容易超时，调大可显著提升成功率")
        .defaultValue(60)
        .min(10)
        .max(300)
        .noSlider()
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

    public ServerDetector() {
        super(CATEGORY_TACTICAL, "服务器检测", "多层指纹识别核心与反作弊，自动白嫖资源包。点击按钮查看说明。");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme, table -> {
            addUniformButton(theme, table, "§e查看使用说明",
                () -> mc.setScreen(new com.example.addon.ui.HelpScreen(theme, this, buildHelpContent())));
            table.row();
            addUniformButton(theme, table, "§b查看下载资源包", this::openResourcePackFolder);
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
                "§a[4] §f结果上报协调器供飞行/发包策略统一决策"
            ),

            new com.example.addon.ui.HelpScreen.HelpSection("资源包模式",
                "§6▸ §f暴力绕过 §8- §7自动回应并拦截所有资源包",
                "§6▸ §f自动白嫖 §8- §7下载到本地（支持断点续传）",
                "§6▸ §f原版处理 §8- §7不干预，走原版弹窗"
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
                "§c⚠ §f暴力绕过可能被强制资源包的服务器踢出",
                "§c⚠ §f单人世界自动禁用，仅在多人服务器生效"
            )
        );
    }

    @Override
    public void onActivate() {
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
            seenChannels.clear();

            long delayMs = detectDelay.get() * 1000L;
            long token = TacticalCoordinator.currentSession();
            Thread waiter = new Thread(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                mc.execute(() -> performDetection(token));
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

        seenChannels.clear();

        // 指令树与插件频道都是进服后陆续下发的，等一会儿再判，否则漏判率很高。
        // 调度时冻结会话代次，防止等待期间换服后把结果写到新服务器会话上
        long delayMs = detectDelay.get() * 1000L;
        long token = TacticalCoordinator.currentSession();
        Thread waiter = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // 侦测要读客户端世界与连接状态，必须回主线程
            mc.execute(() -> performDetection(token));
        }, "yiyiaddon-ServerDetector");
        waiter.setDaemon(true);
        waiter.start();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        seenChannels.clear();
        // 全局状态由 TacticalCoordinator 常驻自清（审计 P0-5），这里只清本模块私有状态
    }

    /**
     * 执行侦测并上报协调器。
     *
     * 三个开关全关时也要上报一次「未检测」结论——协调器的会话状态
     * 需要与真实世界对齐，而不是永远停留在上一服务器的旧值。
     *
     * @param token 调度时冻结的会话代次
     */
    private void performDetection(long token) {
        if (mc.player == null || mc.getConnection() == null) return;

        String core = detectCore.get() ? detectServerCore() : "未检测";
        String antiCheat = detectAntiCheat.get() ? detectAntiCheatPlugin() : "未检测";

        // 唯一合法上报通道：带会话代次校验，跨服迟到的结果在协调器内直接丢弃
        TacticalCoordinator.reportDetection(token, core, antiCheat);

        // 侦测完成：合并成单条多行报告，只带一次模块前缀，避免逐条刷屏
        if (announceDetection.get()) {
            notify(buildDetectionReport(core, antiCheat));
        }
    }

    /**
     * 组装侦测报告（单条多行消息块）。
     *
     * 排版规范：正文统一「标签 §8▸ 值」，标签固定宽度对齐；
     * 风险等级用 §a✓ / §e⚠ / §c✗ 图标统一，反作弊命中红色高亮。
     */
    private String buildDetectionReport(String core, String antiCheat) {
        StringBuilder sb = new StringBuilder();
        sb.append("§b§l━━ 服务端侦测报告 ━━§r\n");

        // 服务器核心：未知/未检测用灰色弱化，命中用金色高亮
        boolean coreUnknown = "未知".equals(core) || "未检测".equals(core);
        sb.append("§7服务器核心 §8▸ ").append(coreUnknown ? "§8" + core : highlightServer(core)).append("\n");

        // 反作弊：未检测灰色 / 未发现绿色提示 / 命中红色高亮
        if ("未检测".equals(antiCheat)) {
            sb.append("§7反作弊   §8▸ §8未检测");
        } else if ("未发现".equals(antiCheat)) {
            sb.append("§7反作弊   §8▸ ").append(highlightText("未发现指纹")).append(" §8（不等于没有）");
        } else {
            sb.append("§7反作弊   §8▸ §c§l").append(antiCheat);
        }

        // 风险等级：只在反作弊命中时播报，高风险标 ✗、中低风险标 ⚠
        if (!"未检测".equals(antiCheat) && !"未发现".equals(antiCheat)) {
            sb.append("\n§7风险等级 §8▸ ");
            if (ServerFingerprints.isHighRisk(antiCheat)) {
                sb.append("§c§l✗ 高风险 §8（协调器已收紧绕过策略）");
            } else {
                sb.append("§e§l⚠ 中低风险");
            }
        }

        return sb.toString();
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
     * 两者都没有时看协调器的会话累计拉回次数，只能给出「存在且激进」程度的结论。
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

        // 第三层：拉回频率（协调器常驻统计，不依赖本模块开关）。只说明有东西在校验移动，认不出型号
        if (TacticalCoordinator.getRubberBandTotal() >= 3) {
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
    //  收包监听：频道指纹 + 资源包劫持
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;

        // 记录插件频道，供反作弊频道指纹使用。
        // 26.1.2 会把 register 的频道名丢进 DiscardedPayload（只留 id、正文被丢弃），
        // 因此 register 里罗列的反作弊频道名无法从包对象恢复，这里只能捕获服务端
        // 直接推送数据的真实频道。协议级频道（brand/register/unregister）与指纹无关，跳过
        if (event.packet instanceof ClientboundCustomPayloadPacket payload) {
            String id = payload.payload().type().id().toString().toLowerCase(Locale.ROOT);
            if (id.equals("minecraft:brand")
                || id.equals("minecraft:register")
                || id.equals("minecraft:unregister")) {
                return;
            }
            if (seenChannels.size() < 64) seenChannels.add(id);
            return;
        }

        // 拉回统计已移交 TacticalCoordinator 常驻处理（onReceivePacket 优先级 -1000），
        // 本模块需要结论时直接读 getRubberBandTotal()，不再维护第二份计数器

        if (event.packet instanceof ClientboundResourcePackPushPacket packet) {
            ResourcePackMode mode = resourcePackMode.get();
            if (mode == ResourcePackMode.BYPASS) {
                event.setCancelled(true);
                sendPackAction(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED);
                sendPackAction(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
                notify("已拦截资源包（暴力绕过）");
            } else if (mode == ResourcePackMode.AUTO_DOWNLOAD) {
                // 必须取消原版处理，否则包会继续走 handleResourcePackPush 弹窗/下载，
                // 与自己异步下载形成双重处理
                event.setCancelled(true);
                downloadResourcePackAsync(packet.id(), packet.url(), packet.hash());
            }
        }
    }

    public boolean handleResourcePackPushFromVanilla(ClientboundResourcePackPushPacket packet, Consumer<Packet<?>> sendPacket) {
        if (!isActive()) return false;
        ResourcePackMode mode = resourcePackMode.get();
        if (mode == ResourcePackMode.BYPASS) {
            UUID packId = packet.id();
            sendPacket.accept(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.ACCEPTED));
            sendPacket.accept(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            notify("已拦截资源包（Mixin入口）");
            return true;
        }
        return false;
    }

    private void sendPackAction(UUID packId, ServerboundResourcePackPacket.Action action) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return;
        }
        connection.send(new ServerboundResourcePackPacket(packId, action));
    }

    /**
     * 异步下载资源包，支持重试、超时、断点续传与 SHA-1 校验。
     *
     * 下载过程中边写边算 SHA-1，完成后与服务器给的 hash 比对，不一致则丢弃重下。
     */
    private void downloadResourcePackAsync(UUID packId, String url, String hash) {
        Thread.ofVirtual().start(() -> {
            sendPackAction(packId, ServerboundResourcePackPacket.Action.ACCEPTED);

            if (!RESOURCE_PACK_DIR.exists()) RESOURCE_PACK_DIR.mkdirs();

            String fileName = packId.toString() + ".zip";
            File targetFile = new File(RESOURCE_PACK_DIR, fileName);
            // 断点续传用的临时文件，下载完成且校验通过后重命名为最终文件
            File partFile = new File(RESOURCE_PACK_DIR, fileName + ".part");

            // 已下载过直接跳过
            if (targetFile.exists()) {
                sendPackAction(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
                asyncNotify("该服务器资源包已下载过：" + fileName);
                return;
            }

            int retries = downloadRetries.get();
            int timeoutMs = downloadTimeout.get() * 1000;

            for (int attempt = 1; attempt <= retries; attempt++) {
                try {
                    if (attempt > 1) {
                        asyncNotify("第 " + attempt + " 次重试下载资源包…");
                    }
                    if (downloadOnce(packId, url, hash, partFile, targetFile, timeoutMs)) {
                        return; // 成功
                    }
                } catch (Exception e) {
                    if (attempt >= retries) {
                        failDownload(packId, "资源包下载失败（已重试 " + retries + " 次）：" + e.getMessage());
                        return;
                    }
                }
            }

            failDownload(packId, "资源包下载失败（已达重试上限 " + retries + " 次）");
        });
    }

    /**
     * 单次下载尝试。
     *
     * @return true 表示下载并校验成功，false 表示本次失败可重试
     */
    private boolean downloadOnce(UUID packId, String url, String hash, File partFile, File targetFile, int timeoutMs) throws Exception {
        long existingSize = 0;
        if (resumeDownload.get() && partFile.exists()) {
            existingSize = partFile.length();
        }

        // 先手动跟随重定向拿到真实下载地址（http→https 跨协议也能走通），
        // 否则 HttpURLConnection 只跟随同协议重定向，跨协议会直接失败
        String finalUrl = followRedirects(url, timeoutMs);

        HttpURLConnection conn = (HttpURLConnection) URI.create(finalUrl).toURL().openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        if (existingSize > 0) {
            conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
        }

        int code = conn.getResponseCode();

        // 续传：服务端返回 206 表示支持 Range，接着写；返回 200 表示不支持，从零重下
        boolean append = code == 206;
        if (code != 200 && code != 206) {
            conn.disconnect();
            return false;
        }

        // 追加模式（206）用 RandomAccessFile 接着写；覆盖模式（200）用 FileOutputStream 截断
        if (append) {
            try (InputStream in = conn.getInputStream();
                 RandomAccessFile raf = new RandomAccessFile(partFile, "rw")) {
                raf.seek(existingSize);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    raf.write(buffer, 0, read);
                }
            }
        } else {
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(partFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }

        // SHA-1 校验：服务器给了 hash 就对整个文件算一次（续传场景必须哈希全文件，
        // 增量哈希会漏掉已下载的前半段）。不一致则丢弃重下
        if (hash != null && !hash.isEmpty()) {
            String actual = sha1OfFile(partFile);
            if (!actual.equalsIgnoreCase(hash)) {
                partFile.delete();
                return false;
            }
        }

        // 校验通过，临时文件转正
        if (!partFile.renameTo(targetFile)) {
            // 跨盘 rename 可能失败，退化为复制
            try (InputStream in = new java.io.FileInputStream(partFile);
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            partFile.delete();
        }

        sendPackAction(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED);
        asyncNotify("资源包已下载：" + targetFile.getName());
        return true;
    }

    /**
     * 手动跟随 HTTP 重定向，返回最终下载地址。
     *
     * HttpURLConnection 默认只跟随同协议重定向（http→http、https→https），
     * 遇到 http→https 的跨协议跳转会原样返回 3xx 交给调用方。资源包 CDN
     * 经常 http 入口跳 https，不手动跟就会下载失败。这里最多跟 5 层，
     * 相对路径用 URI.resolve 拼到当前地址上。
     */
    private String followRedirects(String url, int timeoutMs) throws Exception {
        String current = url;
        for (int i = 0; i < 5; i++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(current).toURL().openConnection();
            conn.setInstanceFollowRedirects(false); // 手动跟，才能处理跨协议跳转
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new java.io.IOException("重定向缺少 Location");
                }
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            conn.disconnect();
            return current; // 非重定向，直接用这个地址
        }
        throw new java.io.IOException("重定向次数过多（超过 5 层）");
    }

    /** 下载线程里发普通提示：投递回主线程，避免跨线程碰客户端崩溃 */
    private void asyncNotify(String message) {
        mc.execute(() -> notify(message));
    }

    /**
     * 下载失败收尾：回主线程先播报失败、再回 FAILED_DOWNLOAD。
     *
     * 二者必须在同一次主线程投递里按顺序执行——若先发包再播报，服务端收到
     * FAILED_DOWNLOAD 后可能立即断开，把 mc.player 清空，导致 notifyError 里
     * mc.player == null 直接 return，玩家就看不到失败提示。
     */
    private void failDownload(UUID packId, String message) {
        mc.execute(() -> {
            notifyError(message);
            sendPackAction(packId, ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD);
        });
    }

    /** 计算文件 SHA-1 十六进制摘要，用于资源包完整性校验 */
    private String sha1OfFile(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 26.1.2 已移除 net.minecraft.Util，改用 AWT Desktop，放独立线程避免卡渲染。 */
    private void openResourcePackFolder() {
        if (!RESOURCE_PACK_DIR.exists()) RESOURCE_PACK_DIR.mkdirs();

        Thread opener = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    // 首选 AWT Desktop：Windows 开资源管理器 / Mac 开 Finder / 有桌面的 Linux 开文件管理器
                    Desktop.getDesktop().open(RESOURCE_PACK_DIR);
                } else {
                    openWithSystemCommand(RESOURCE_PACK_DIR);
                }
            } catch (Exception e) {
                // Desktop.open 在无桌面环境（部分 Linux/服务器）会失败，回退到系统命令兜底
                try {
                    openWithSystemCommand(RESOURCE_PACK_DIR);
                } catch (Exception ex) {
                    mc.execute(() -> notifyError("打开资源库失败：" + ex.getMessage()));
                }
            }
        }, "yiyiaddon-OpenFolder");
        opener.setDaemon(true);
        opener.start();
    }

    /** 按操作系统调用系统命令打开目录，兜底无 AWT Desktop 或 Desktop.open 失败的环境。 */
    private void openWithSystemCommand(File dir) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // Windows：/select 让资源管理器选中目标，路径含空格也能正确解析
            new ProcessBuilder("explorer.exe", "/select," + dir.getAbsolutePath()).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", dir.getAbsolutePath()).start();
        } else {
            // Linux / 其他 Unix：xdg-open 是主流桌面环境的通用打开命令
            new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
        }
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