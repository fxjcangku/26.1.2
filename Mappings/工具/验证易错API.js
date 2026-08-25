/**
 * 验证常见 Yarn / 旧版类名在 26.1.2 官方映射里是否存在
 *
 * 用法：node Mappings/工具/验证易错API.js
 * 输出：Mappings/易错对照表-26.1.2.txt
 *
 * 为什么需要：26.1.2（1.21.11）之后 Mojang 官方映射有大量改名，
 * 很多 AI 和教程还在用 Yarn 名或旧官方名，直接抄会编译不过。
 * 本脚本以映射原文件为唯一事实来源，逐个核对，不靠记忆。
 */

const fs = require('fs');
const path = require('path');

const 根目录 = path.join(__dirname, '..');
const 原文件 = path.join(根目录, '官方映射原文件', 'client-1.21.11.txt');

if (!fs.existsSync(原文件)) {
    console.error(`找不到映射原文件：${原文件}`);
    process.exit(1);
}

// 收集 26.1.2 真实存在的全部类名与简名
const 全名集合 = new Set();
const 简名映射 = new Map();

for (const 行 of fs.readFileSync(原文件, 'utf8').split('\n')) {
    if (行.startsWith(' ') || 行.startsWith('#') || !行.includes(' -> ')) continue;
    const 完整名 = 行.split(' -> ')[0].trim();
    if (!完整名.startsWith('net.minecraft.')) continue;
    全名集合.add(完整名);
    const 简名 = 完整名.split('.').pop();
    if (!简名映射.has(简名)) 简名映射.set(简名, []);
    简名映射.get(简名).push(完整名);
}

/**
 * 待核对清单：[错误写法（Yarn 或旧官方名）, 猜测的正确简名, 用途说明]
 * 正确名由脚本在真实映射里查找确认，不预先写死。
 */
const 待核对 = [
    ['MinecraftClient',        'Minecraft',            '客户端主类'],
    ['ClientPlayerEntity',     'LocalPlayer',          '本地玩家'],
    ['ClientWorld',            'ClientLevel',          '客户端世界'],
    ['PlayerEntity',           'Player',               '玩家基类'],
    ['LivingEntity',           'LivingEntity',         '生物实体（未改名）'],
    ['Text',                   'Component',            '文本组件'],
    ['MutableText',            'MutableComponent',     '可变文本'],
    ['ResourceLocation',       'Identifier',           '资源标识符（1.21.11 改名重点）'],
    ['NbtCompound',            'CompoundTag',          'NBT 复合标签'],
    ['NbtList',                'ListTag',              'NBT 列表'],
    ['NbtElement',             'Tag',                  'NBT 基类'],
    ['Formatting',             'ChatFormatting',       '颜色格式枚举'],
    ['ActionResult',           'InteractionResult',    '交互结果'],
    ['Hand',                   'InteractionHand',      '手（主手/副手）'],
    ['Box',                    'AABB',                 '碰撞箱'],
    ['DrawContext',            'GuiGraphics',          'GUI 绘制上下文'],
    ['Screen',                 'Screen',               '界面基类（未改名）'],
    ['ItemStack',              'ItemStack',            '物品堆（未改名）'],
    ['PlayerInventory',        'Inventory',            '玩家背包'],
    ['ScreenHandler',          'AbstractContainerMenu','容器菜单'],
    ['BlockPos',               'BlockPos',             '方块坐标（未改名）'],
    ['Vec3d',                  'Vec3',                 '三维向量'],
    ['Vec3i',                  'Vec3i',                '整数向量（未改名）'],
    ['World',                  'Level',                '世界'],
    ['ServerWorld',            'ServerLevel',          '服务端世界'],
    ['BlockState',             'BlockState',           '方块状态（未改名）'],
    ['ClientPlayNetworkHandler','ClientPacketListener','客户端网络处理器'],
    ['ClientPlayerInteractionManager','MultiPlayerGameMode','交互管理器'],
    ['HungerManager',          'FoodData',             '饥饿值'],
    ['SoundEvent',             'SoundEvent',           '声音事件（未改名）'],
    ['Registries',             'Registries',           '注册表键（未改名）'],
    ['Registry',               'Registry',             '注册表（未改名）'],
    ['RegistryKey',            'ResourceKey',          '注册表键引用'],
    ['DynamicRegistryManager', 'RegistryAccess',       '动态注册表管理'],
    ['StatusEffects',          'MobEffects',           '状态效果'],
    ['EntityAttributes',       'Attributes',           '实体属性'],
    ['GameMode',               'GameType',             '游戏模式'],
    ['ClickEvent',             'ClickEvent',           '点击事件（未改名）'],
    ['Style',                  'Style',                '文本样式（未改名）']
];

const 结果行 = [];
let 已改名数 = 0;
let 未改名数 = 0;
let 待查数 = 0;

for (const [旧名, 猜测新名, 说明] of 待核对) {
    const 旧名存在 = 简名映射.has(旧名);
    const 新名命中 = 简名映射.get(猜测新名);

    if (!新名命中) {
        结果行.push(`?  ${旧名.padEnd(34)} 猜测的 ${猜测新名} 在映射中未找到，需人工复查   ${说明}`);
        待查数++;
        continue;
    }

    const 完整路径 = 新名命中.join('  |  ');

    if (旧名 === 猜测新名) {
        // 名字没变，确认它确实存在
        结果行.push(`=  ${旧名.padEnd(34)} ${完整路径}\n   └─ 未改名，可直接使用。${说明}`);
        未改名数++;
    } else if (旧名存在) {
        结果行.push(`!  ${旧名.padEnd(34)} 注意：${旧名} 在 26.1.2 中也存在，但语义可能不同，需人工确认\n   └─ 建议用 ${完整路径}。${说明}`);
        待查数++;
    } else {
        结果行.push(`X  ${旧名.padEnd(34)} 不存在！请改用：${完整路径}\n   └─ ${说明}`);
        已改名数++;
    }
}

const 输出 =
`# 26.1.2 易错 API 对照表（Yarn / 旧官方名 → 26.1.2 官方映射）
#
# 版本：Minecraft 26.1.2（内部版本号 1.21.11）
# 事实来源：Mappings/官方映射原文件/client-1.21.11.txt
# 生成命令：node Mappings/工具/验证易错API.js
#
# 图例：
#   X  该名称在 26.1.2 中不存在，必须改用右侧名称（最容易踩的坑）
#   =  名称未改变，可直接使用
#   ?  需要人工复查
#
# 统计：已改名 ${已改名数} 个，未改名 ${未改名数} 个，待复查 ${待查数} 个
#
# ⚠ 本项目使用 Mojang 官方映射，不是 Yarn。
#   任何以 MinecraftClient / Text / NbtCompound / ResourceLocation 开头的代码
#   都是错的，会直接编译失败。

${结果行.join('\n')}
`;

fs.writeFileSync(path.join(根目录, '易错对照表-26.1.2.txt'), 输出, 'utf8');
console.log(`已改名 ${已改名数} 个，未改名 ${未改名数} 个，待复查 ${待查数} 个`);
console.log('输出：Mappings/易错对照表-26.1.2.txt');
