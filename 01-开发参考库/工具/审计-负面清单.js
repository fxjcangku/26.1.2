// 26.1.2 API 审查：负面清单扫描 v1
// 扫描项目源码中所有「26.1.2 已移除/改名/不存在的 API」可疑用法
// 判定依据：01-开发参考库（API参考 + 开发机制 + 原始源码）；命中项需人工逐一对照裁定
const fs = require('fs');
const path = require('path');

const 项目根 = 'd:/mcaddon/26.1.2/src/main/java';

// 模式列表：{ 模式: 正则, 说明: 中文说明, 依据: 参考库依据 }
const 模式表 = [
    // ===== Meteor 层 =====
    { 模式: /\bVisSetting\b/, 说明: '旧可见性包装类', 依据: 'API参考/Meteor/Setting设置.md：26.1.2 已移除，用 IVisible+.visible()' },
    { 模式: /\bRotationUtils\b|\bPositionUtils\b/, 说明: '旧工具类名', 依据: 'API参考/Meteor/玩家工具.md：实为 Rotations，PositionUtils 不存在' },
    { 模式: /\bChatUtils\s*\.\s*sendCommand\b/, 说明: 'ChatUtils 无 sendCommand', 依据: 'API参考/Meteor/Packet数据包.md：用 sendPlayerMsg 或原版 Connection.sendCommand' },
    { 模式: /\bCommandArgumentType\b/, 说明: '旧命令参数类型', 依据: 'API参考/Meteor/命令.md：26.1.2 已移除，用 Command.argument()/literal()' },
    { 模式: /\bplaceBlock\b/, 说明: 'BlockUtils 旧方法名', 依据: 'API参考/Meteor/方块工具.md：26.1.2 为 BlockUtils.place(...)' },
    { 模式: /\bUtils\s*\.\s*mc\b|\bMC\s*\.\s*player\b/, 说明: '旧 mc 单例', 依据: 'API参考/Meteor/其他.md：mc 单例为 MeteorClient.mc' },
    { 模式: /\bModuleList\b|\bModuleGroup\b/, 说明: '不存在的模块容器类', 依据: 'API参考/Meteor/Module模块.md：模块存于 Modules Map' },
    { 模式: /\bKeyEvent\b(?!\.Pre|\.Post)/, 说明: '旧键盘事件名', 依据: 'API参考/Meteor/Event事件.md：26.1.2 为 KeyInputEvent' },
    { 模式: /\bGuiRenderer\b|\bTextRenderer\b|\bVanillaTextRenderer\b/, 说明: '旧渲染文本类', 依据: 'API参考/Meteor/渲染.md：渲染已重构' },
    { 模式: /\bRenderer2D\b|\bRenderer3D\b/, 说明: '旧渲染入口', 依据: 'API参考/Meteor/渲染.md：MeshBuilder + MeteorRenderPipelines' },
    { 模式: /\bSettingsChangedEvent\b/, 说明: '旧设置变更事件（若引用）', 依据: 'API参考/Meteor/Setting设置.md：按源码确认' },
    { 模式: /\bThemeModule\b.*\btheme\b|\btheme\s*\(\s*\)\s*\.\s*button\b/, 说明: 'GUI 主题直用模式', 依据: '本项目规范 addUniformButton；GUI 用法需人工复核' },
    // ===== Minecraft 层（Yarn旧名）=====
    { 模式: /\bMinecraftClient\b|\bClientPlayerEntity\b|\bClientWorld\b|\bPlayerInventory\b|\bScreenHandler\b|\bVec3d\b/, 说明: 'Yarn 旧类名', 依据: '易错对照表-26.1.2.txt：官方名 Minecraft/LocalPlayer/ClientLevel/Inventory/AbstractContainerMenu/Vec3' },
    { 模式: /\bDyeColor\b/, 说明: '易误 Yarn 名（官方 Color）', 依据: '易错对照表：官方 net.minecraft.world.item.Color' },
    { 模式: /\bDrawContext\b|\bGuiGraphics\b/, 说明: '旧 GUI 绘图上下文', 依据: 'API参考/Minecraft/GUI与界面.md：26.1.2 抽帧模型 GuiGraphicsExtractor' },
    { 模式: /\bgetNbt\s*\(\s*\)\b|\bgetOrCreateNbt\b|\bsetNbt\s*\(\s*[^,]+,\s*CompoundTag/, 说明: '旧 NBT API（ItemStack）', 依据: 'API参考/Minecraft/数据组件.md：全部走组件系统' },
    { 模式: /ResourceKey[^;\n]*\.location\s*\(\s*\)/, 说明: 'ResourceKey 旧方法名', 依据: '易错对照表：26.1.2 为 identifier()' },
    { 模式: /\.\s*random\s*(==|!=|$|;)/, 说明: 'Level.random 字段访问', 依据: '易错对照表：26.1.2 为 getRandom() 方法' },
    { 模式: /renderBg\b|renderLabels\b|renderSlot\b/, 说明: '旧容器 GUI 渲染钩子', 依据: 'API参考/Minecraft/GUI与界面.md：26.1.2 为 extractXxx' },
    { 模式: /\bNbtCompound\b/, 说明: 'NBT 类（应 CompoundTag）', 依据: '易错对照表：官方 net.minecraft.nbt.CompoundTag' },
    { 模式: /\bText\s*\.\s*(literal|translatable)\b|\bMutableText\b/, 说明: '旧文本类（应 Component）', 依据: '易错对照表：官方 Component/MutableComponent' },
    { 模式: /\bFormatting\s*\./, 说明: '旧颜色类（应 ChatFormatting）', 依据: '易错对照表：官方 ChatFormatting' },
    { 模式: /\bGameMode\b(?!s)/, 说明: '旧游戏模式（应 GameType）', 依据: '易错对照表：官方 world.level.GameType' },
    { 模式: /\bsendMessage\s*\(/, 说明: 'Player 旧发送消息（26.1 移除？）', 依据: '待对照 Minecraft原始源码 Player.java' },
    { 模式: /\bgetAttribute\s*\(\s*Attributes\./, 说明: 'LivingEntity 旧属性入口', 依据: '待对照 Minecraft原始源码 LivingEntity.java' },
    { 模式: /\bgetAttributeInstance\b/, 说明: '旧属性入口', 依据: '待对照 26.1.2 是否移除' },
    { 模式: /\bgetItemInHand\b|\bsetItemInHand\b|\bgetEquippedItem\b/, 说明: '旧装备栏 API', 依据: '待对照 26.1.2 LivingEntity/Entity' },
    { 模式: /\bgetEnchantments\s*\(\s*\)\b|\baddEnchantment\s*\(/, 说明: '旧附魔 API（ItemStack）', 依据: 'API参考/Minecraft/附魔.md：走 Enchantments/ItemEnchantments 组件' },
    { 模式: /\bgetRarity\s*\(\s*\)\b|\bisTreasureOnly\b/, 说明: '旧附魔属性 API', 依据: 'API参考/Minecraft/附魔.md：26.1.2 数据驱动已移除' },
    { 模式: /\bsendChatMessage\b/, 说明: '旧聊天发送', 依据: '待对照 26.1.2 Player 发送链路' },
    { 模式: /\bPacketUtil[s]?\b/, 说明: '可能旧类', 依据: '待对照' },
    { 模式: /\bStatusEffects\b/, 说明: 'Yarn 旧名（官方 MobEffects）', 依据: '易错对照表' },
    { 模式: /\bEntityEquipment\b/, 说明: 'Yarn 旧名', 依据: '易错对照表' },
    { 模式: /\bTooltipContext\b|\bTooltipFlag\b/, 说明: '旧 tooltip 上下文', 依据: '待对照 26.1.2' },
];

function 扫描(dir) {
    const 命中 = [];
    (function walk(d) {
        for (const e of fs.readdirSync(d, { withFileTypes: true })) {
            const p = path.join(d, e.name);
            if (e.isDirectory()) walk(p);
            else if (e.name.endsWith('.java')) {
                const 内容 = fs.readFileSync(p, 'utf8');
                const 行 = 内容.split(/\r?\n/);
                行.forEach((line, i) => {
                    for (const m of 模式表) {
                        if (m.模式.test(line)) {
                            命中.push({ 文件: path.relative(项目根, p), 行号: i + 1, 内容: line.trim().slice(0, 100), 说明: m.说明, 依据: m.依据, 模式: String(m.模式) });
                        }
                    }
                });
            }
        }
    })(dir);
    return 命中;
}

const 结果 = 扫描(项目根);
console.log('=== 负面清单扫描结果：共', 结果.length, '处命中 ===\n');
let 当前 = '';
for (const h of 结果) {
    if (h.说明 !== 当前) { console.log(`\n## ${h.说明}\n   （${h.依据}）`); 当前 = h.说明; }
    console.log(`   ${h.文件}:${h.行号}  ${h.内容}`);
}