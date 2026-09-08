package com.example.addon.teleport;

import com.example.addon.core.YiyiaddonModule;
import com.example.addon.teleport.core.TeleportCoordinator;
import com.example.addon.teleport.model.TeleportMode;
import com.example.addon.teleport.model.TeleportRequest;
import com.example.addon.teleport.remind.KeyRemindListener;
import com.example.addon.teleport.render.TeleportRender;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

import static com.example.addon.core.AddonTemplate.CATEGORY;

/**
 * 传送模块：TP地面 / TP穿墙 / TP坐标 三个独立功能，各绑独立按键（松开触发）。
 *
 * <p>全部业务逻辑分类在 teleport 功能包：模型 model / 安全判据 safety /
 * 几何扫描 geo / 发包执行 move / 回弹验证 verify / 决策状态机 core /
 * 调试渲染 render / 指令 command；本类只负责设置面板、事件接线与触发打包。</p>
 *
 * @author yiyijia
 */
public class TeleportModule extends YiyiaddonModule {

    private final SettingGroup sgKeys = settings.createGroup("触发按键");
    private final SettingGroup sgGround = settings.createGroup("TP地面");
    private final SettingGroup sgWall = settings.createGroup("TP穿墙");
    private final SettingGroup sgCoord = settings.createGroup("TP坐标");
    private final SettingGroup sgVerify = settings.createGroup("验证");
    private final SettingGroup sgDebug = settings.createGroup("调试");

    // TP地面
    private final Setting<Integer> maxRise;
    // TP穿墙
    private final Setting<Double> maxDistance;
    private final Setting<Double> maxDeviation;
    private final Setting<Integer> maxFall;
    // TP坐标
    private final Setting<Integer> coordX;
    private final Setting<Integer> coordY;
    private final Setting<Integer> coordZ;
    private final Setting<Integer> fallbackRadius;
    // 验证
    private final Setting<Double> verifyThreshold;
    private final Setting<Integer> verifyWindow;
    // 调试
    private final Setting<Boolean> debugDraw;

    /** 三个功能按键：保存引用供提醒监听器读取绑定值 */
    private final KeybindSetting keyGround;
    private final KeybindSetting keyWall;
    private final KeybindSetting keyCoord;

    /** 独立提醒监听器：常驻事件总线，与模块生命周期解耦 */
    private final KeyRemindListener keyReminder;

    /** 决策层：三模式共用状态机（Observe → Decide → Execute → Verify） */
    private final TeleportCoordinator coordinator = new TeleportCoordinator(sink());

    public TeleportModule() {
        super(CATEGORY, "传送", "三模式安全传送：TP地面回地表 / TP穿墙过障碍 / TP坐标定点，带服务端回弹验证。");

        // 禁用基类自动订阅：本模块的订阅由 onActivate/onDeactivate 自行管理，
        // 否则 toggle() 的基类订阅 + onActivate 订阅会双重注册，事件全部触发两次
        autoSubscribe = false;

        // 三个独立按键：KeybindSetting 的 action 在模块开启且按键松开时触发
        keyGround = sgKeys.add(new KeybindSetting.Builder()
            .name("TP地面键")
            .description("触发 TP地面：回到头顶真正露天地表（洞穴脱身）")
            .defaultValue(Keybind.none())
            .action(this::triggerGround)
            .build());

        keyWall = sgKeys.add(new KeybindSetting.Builder()
            .name("TP穿墙键")
            .description("触发 TP穿墙：沿准星方向智能搜索落点，穿墙/赶路一体，前方无障碍也能前进")
            .defaultValue(Keybind.none())
            .action(this::triggerWall)
            .build());

        keyCoord = sgKeys.add(new KeybindSetting.Builder()
            .name("TP坐标键")
            .description("触发 TP坐标：传送到下方配置的坐标")
            .defaultValue(Keybind.none())
            .action(this::triggerCoord)
            .build());

        // 独立提醒监听器：进构造器即常驻，只做「模块未开启按键提醒」，不碰模块事件接线
        keyReminder = new KeyRemindListener(keyGround, keyWall, keyCoord, keyName -> {
            if (!isActive()) notify("§c✗ 模块未开启 ▸ 请先开启 " + highlightFunction("传送") + "，再按快捷键使用 " + highlightFunction(keyName));
        });

        maxRise = sgGround.add(new IntSetting.Builder()
            .name("地表扫描上限")
            .description("从当前高度向上扫描真正地表的最大格数")
            .defaultValue(200)
            .min(16)
            .max(320)
            .noSlider()
            .build());

        maxDistance = sgWall.add(new DoubleSetting.Builder()
            .name("最大穿墙距离")
            .description("沿准星方向智能搜索落点的最大距离（格），实际是否被接受由服务端验证决定")
            .defaultValue(16)
            .min(4)
            .max(64)
            .noSlider()
            .build());

        maxDeviation = sgWall.add(new DoubleSetting.Builder()
            .name("落点修正范围")
            .description("理想落点无法容纳玩家时，就近搜索安全落点的修正半径（格）")
            .defaultValue(3)
            .min(1)
            .max(8)
            .noSlider()
            .build());

        maxFall = sgWall.add(new IntSetting.Builder()
            .name("最大下落落差")
            .description("墙后自动下落到支撑的最大落差（格）")
            .defaultValue(6)
            .min(0)
            .max(20)
            .noSlider()
            .build());

        coordX = sgCoord.add(new IntSetting.Builder()
            .name("坐标 X")
            .description("TP坐标键的目标 X 坐标")
            .defaultValue(0)
            .min(-30000000)
            .max(30000000)
            .noSlider()
            .build());

        coordY = sgCoord.add(new IntSetting.Builder()
            .name("坐标 Y")
            .description("TP坐标键的目标 Y 坐标")
            .defaultValue(100)
            .min(-64)
            .max(640)
            .noSlider()
            .build());

        coordZ = sgCoord.add(new IntSetting.Builder()
            .name("坐标 Z")
            .description("TP坐标键的目标 Z 坐标")
            .defaultValue(0)
            .min(-30000000)
            .max(30000000)
            .noSlider()
            .build());

        fallbackRadius = sgCoord.add(new IntSetting.Builder()
            .name("回退搜索半径")
            .description("目标坐标不可站立时的邻近安全落点搜索半径（格，0 = 不回退）")
            .defaultValue(4)
            .min(0)
            .max(8)
            .noSlider()
            .build());

        verifyThreshold = sgVerify.add(new DoubleSetting.Builder()
            .name("回弹判定阈值")
            .description("服务端权威位置与预期位置的距离超过该值判定为回弹（格）")
            .defaultValue(1.5)
            .min(0.25)
            .max(4)
            .noSlider()
            .build());

        verifyWindow = sgVerify.add(new IntSetting.Builder()
            .name("验证窗口")
            .description("发送位置后等待服务端权威包的最大时长（tick），超时无包即视为接受")
            .defaultValue(8)
            .min(2)
            .max(20)
            .noSlider()
            .build());

        debugDraw = sgDebug.add(new BoolSetting.Builder()
            .name("调试渲染")
            .description("渲染最近一次传送的目标点/射线/候选格（仅自己可见）")
            .defaultValue(false)
            .build());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  触发入口（三个独立按键 + 指令）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** TP地面：回到真正露天地表 */
    private void triggerGround() {
        send(build(TeleportMode.GROUND, null));
    }

    /** TP穿墙：锁定当前真实视线快照（相机起点 + 观察方向 + 眼高） */
    private void triggerWall() {
        if (mc.player == null) return;
        TeleportRequest req = build(TeleportMode.WALL, null);
        req.rayOrigin = mc.player.getEyePosition(1.0F);
        req.rayDir = mc.player.getViewVector(1.0F);
        req.eyeHeight = mc.player.getEyeHeight();
        send(req);
    }

    /** TP坐标：传送到面板配置的坐标 */
    private void triggerCoord() {
        send(build(TeleportMode.COORD, new Vec3(coordX.get() + 0.5, coordY.get(), coordZ.get() + 0.5)));
    }

    /** 指令入口：.tp X Y Z */
    public void teleportToCoord(int x, int y, int z) {
        send(build(TeleportMode.COORD, new Vec3(x + 0.5, y, z + 0.5)));
    }

    /** 从设置面板打包请求参数（决策层不依赖设置对象） */
    private TeleportRequest build(TeleportMode mode, Vec3 coord) {
        TeleportRequest req = new TeleportRequest();
        req.mode = mode;
        req.coord = coord;
        req.maxRise = maxRise.get();
        req.maxDistance = maxDistance.get();
        req.maxDeviation = maxDeviation.get();
        req.maxFall = maxFall.get();
        req.fallbackRadius = fallbackRadius.get();
        req.verifyThreshold = verifyThreshold.get();
        req.verifyWindow = verifyWindow.get();
        return req;
    }

    private void send(TeleportRequest req) {
        if (mc.player == null || mc.level == null) return;
        coordinator.request(mc.player, mc.level, req);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  生命周期与事件接线
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public void onActivate() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
        coordinator.cancel("模块已关闭，传送中止");
        coordinator.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        coordinator.tick(mc.player, mc.level);
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null) return;
        coordinator.onServerPacket(event.packet, mc.player);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (debugDraw.get()) TeleportRender.draw(event, coordinator.last());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  播报回调：高亮统一走基类 highlight* 方法（强调色转换体系）
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private TeleportCoordinator.Sink sink() {
        return new TeleportCoordinator.Sink() {
            @Override
            public void broadcast(String message) {
                // 限定外层实例：匿名类内 notify 与 Object.notify() 撞名
                TeleportModule.this.notify(message);
            }

            @Override
            public String text(String s) {
                return highlightText(s);
            }

            @Override
            public String func(String s) {
                return highlightFunction(s);
            }

            @Override
            public String num(String s) {
                return highlightNumber(s);
            }

            @Override
            public String loc(String s) {
                return highlightLocation(s);
            }
        };
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  说明面板
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return buildInfoWidget(theme,
            new String[]{
                "§b§l传送 ▸ 三模式安全传送",
                "§7基于 26.1.2 真实碰撞形状判据 + 服务端回弹验证"
            },
            new String[]{
                "§8▸ §fTP地面 §8▸ 回到头顶真正的露天地面（洞穴脱身）",
                "§8▸ §fTP穿墙 §8▸ 沿准星方向智能落点：可穿墙/门窗/半砖，山体建筑直接穿过",
                "§8▸ §f方向赶路 §8▸ 前方无墙也能前进，连续按键逐步赶路，理想落点被占自动就近修正",
                "§8▸ §fTP坐标 §8▸ 传送到配置坐标，或指令 .tp X Y Z",
                "§8▸ §f独立按键 §8▸ 三个功能各绑一个按键（松开触发）",
                "§8▸ §f回弹验证 §8▸ 服务端拉回时自动跟随并播报偏差",
                "§8▸ §f载具支持 §8▸ 乘坐本机权威载具时整体位移传送",
            });
    }
}