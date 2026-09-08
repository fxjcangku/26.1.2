package com.example.addon.itemid;

import com.example.addon.autochest.config.InfoTextSetting;
import com.example.addon.core.AddonTemplate;
import com.example.addon.core.YiyiaddonModule;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * ID 识别模块（辅助体系 · 第一环）。
 *
 * <p>一次性工具模块：激活时识别玩家手持物品（主手优先，主手空读副手，均空提示），
 * 生成完整 {@link ItemIdentity}。按「识别模式」决定后续行为：</p>
 * <ul>
 *   <li>聊天复制/显示：弹出识别结果屏幕，可复制 Item ID / 完整信息 / 保存 ID / 添加到 ID 配置。</li>
 *   <li>自动保存：识别后直接写入 {@link ItemIdManager}。</li>
 * </ul>
 *
 * <p>产出的 ID 供「ID 配置管理」与「自动箱子」消费，对应数据链：
 * ID识别 → ItemIdentity → ID 文件（AutoChest/items/）。</p>
 */
public final class IdIdentifyModule extends YiyiaddonModule {

    private final Minecraft mc = Minecraft.getInstance();
    private final ItemIdManager idManager;

    // ── 识别模式设置 ──
    private final SettingGroup sgIdentify;
    public final Setting<IdentifyMode> identifyMode;
    public final InfoTextSetting currentMode;

    public IdIdentifyModule(ItemIdManager idManager) {
        super(AddonTemplate.CATEGORY_ASSIST, "ID识别", "识别手持物品并加入ID配置。点击开启即识别（主手→副手）。");
        this.idManager = idManager;

        sgIdentify = settings.createGroup("识别");
        identifyMode = sgIdentify.add(new EnumSetting.Builder<IdentifyMode>()
            .name("识别模式")
            .description("聊天复制/显示：识别后弹出结果屏幕可复制或保存；自动保存：识别后直接写入 ID 配置。")
            .defaultValue(IdentifyMode.AUTO_SAVE)
            .build());
        currentMode = sgIdentify.add(new InfoTextSetting(
            "当前模式",
            "当前选中的识别模式（实时显示）。",
            () -> "§a§l" + identifyMode.get().toString()));
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            notifyError("玩家未加载");
            closeQuietly();
            return;
        }

        // 主手优先，主手空读副手
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            held = mc.player.getOffhandItem();
        }
        if (held.isEmpty()) {
            notifyError("没有可识别物品：主手和副手都是空的");
            closeQuietly();
            return;
        }

        // 精确识别：生成完整身份（中文名 + 自定义名 + 附魔 + Data Component + 数量）
        ItemIdentity identity = ItemIdentifier.identifyItem(held);
        if (identity == null) {
            notifyError("识别失败");
            closeQuietly();
            return;
        }

        if (identifyMode.get() == IdentifyMode.AUTO_SAVE) {
            // 自动保存：直接写入 ID 配置
            String fileName = idManager.add(identity);
            if (fileName != null) {
                notify("§a§l✓ 已识别物品 §8▸ " + highlightText(identity.displayName()));
                for (ItemIdentity.EnchantmentEntry e : identity.enchantments()) {
                    notify("§7附魔　§8▸ §a" + e.displayName() + " §8▸ §f" + e.id());
                }
            } else {
                notifyError("该物品已在 ID 配置中");
            }
        } else {
            // 聊天复制/显示：弹出结果屏幕，供复制 / 保存 / 添加
            mc.setScreen(new IdResultScreen(GuiThemes.get(), identity, idManager));
        }

        // 一次性工具：识别完自动关闭，避免持续占用
        closeQuietly();
    }

    /** 静默关闭模块（不输出开关提示，避免与识别结果重复刷屏） */
    private void closeQuietly() {
        chatFeedback = false;
        mc.execute(() -> {
            if (isActive()) toggle();
            chatFeedback = true;
        });
    }
}
