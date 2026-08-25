/**
 * 生成按功能域分类的 26.1.2 官方映射速查表
 *
 * 用法：node Mappings/工具/生成分类速查表.js
 *
 * 输入：Mappings/官方映射原文件/client-1.21.11.txt
 * 输出：Mappings/分类速查/*.txt   按功能域拆分，每个文件带中文用途注释
 *
 * 为什么要分类：类名索引有 9848 条，平铺查不动。
 * 按包前缀归到开发时真正会用到的几个域，写代码时直接翻对应文件。
 */

const fs = require('fs');
const path = require('path');

const 根目录 = path.join(__dirname, '..');
const 原文件 = path.join(根目录, '官方映射原文件', 'client-1.21.11.txt');
const 输出目录 = path.join(根目录, '分类速查');

if (!fs.existsSync(原文件)) {
    console.error(`找不到映射原文件：${原文件}\n请先按 Mappings/说明.md 下载。`);
    process.exit(1);
}

fs.mkdirSync(输出目录, { recursive: true });

/**
 * 分类规则：按包前缀匹配，顺序敏感（先匹配到的优先）。
 * 每类带中文说明，写进输出文件头部。
 */
const 分类规则 = [
    {
        文件: '01-客户端与玩家.txt',
        说明: '客户端主类、本地玩家、客户端世界、交互管理器。写模块最常用的入口。',
        前缀: ['net.minecraft.client.Minecraft', 'net.minecraft.client.player.', 'net.minecraft.client.multiplayer.']
    },
    {
        文件: '02-文本与聊天.txt',
        说明: '聊天消息、文本组件、样式、点击/悬浮事件。发消息、改显示文本用。',
        前缀: ['net.minecraft.network.chat.']
    },
    {
        文件: '03-物品与背包.txt',
        说明: '物品、物品栏、容器菜单、槽位、附魔。物流类模块的核心。',
        前缀: ['net.minecraft.world.item.', 'net.minecraft.world.inventory.', 'net.minecraft.world.entity.player.']
    },
    {
        文件: '04-方块与世界.txt',
        说明: '方块、方块状态、世界、维度、方块实体。挖矿、农场类模块用。',
        前缀: ['net.minecraft.world.level.']
    },
    {
        文件: '05-实体.txt',
        说明: '实体基类、生物、属性、效果、伤害来源。',
        前缀: ['net.minecraft.world.entity.', 'net.minecraft.world.effect.', 'net.minecraft.world.damagesource.']
    },
    {
        文件: '06-网络数据包.txt',
        说明: '所有收发包类型。绕过类模块、包监听必查。Clientbound=服务端发来，Serverbound=客户端发出。',
        前缀: ['net.minecraft.network.protocol.']
    },
    {
        文件: '07-坐标与数学.txt',
        说明: '方块坐标、向量、朝向、碰撞箱、射线检测结果。',
        前缀: ['net.minecraft.core.BlockPos', 'net.minecraft.core.Direction', 'net.minecraft.core.Vec3i', 'net.minecraft.world.phys.', 'net.minecraft.util.Mth']
    },
    {
        文件: '08-注册表与资源标识.txt',
        说明: 'Identifier（旧名 ResourceLocation，已改名）、注册表、ResourceKey、Holder。',
        前缀: ['net.minecraft.resources.', 'net.minecraft.core.registries.', 'net.minecraft.core.Registry', 'net.minecraft.core.Holder']
    },
    {
        文件: '09-NBT标签.txt',
        说明: 'NBT 读写。配置持久化、fromTag/toTag 覆写用。',
        前缀: ['net.minecraft.nbt.']
    },
    {
        文件: '10-GUI界面.txt',
        说明: '原版界面、渲染上下文、控件。写 Mixin 拦截界面文本用。',
        前缀: ['net.minecraft.client.gui.']
    },
    {
        文件: '11-渲染.txt',
        说明: '渲染管线、着色器、纹理、模型。ESP 类模块用。',
        前缀: ['net.minecraft.client.renderer.', 'net.minecraft.client.model.', 'net.minecraft.client.resources.']
    },
    {
        文件: '12-声音.txt',
        说明: '声音事件、音源。',
        前缀: ['net.minecraft.sounds.', 'net.minecraft.client.sounds.', 'net.minecraft.client.resources.sounds.']
    },
    {
        文件: '13-指令系统.txt',
        说明: '指令来源、参数类型、建议提供者。写自定义指令用。',
        前缀: ['net.minecraft.commands.']
    },
    {
        文件: '14-顶层常用类.txt',
        说明: 'net.minecraft 根包下的类，如 ChatFormatting（颜色代码枚举）。',
        前缀: ['net.minecraft.ChatFormatting', 'net.minecraft.Util', 'net.minecraft.SharedConstants', 'net.minecraft.references.']
    }
];

// 读取并分类
const 全部类名 = [];
const 内容 = fs.readFileSync(原文件, 'utf8').split('\n');

for (const 行 of 内容) {
    if (行.startsWith(' ') || 行.startsWith('#') || !行.includes(' -> ')) continue;
    const 完整名 = 行.split(' -> ')[0].trim();
    if (完整名.startsWith('net.minecraft.')) 全部类名.push(完整名);
}

const 已分类 = new Set();
let 汇总 = [];

for (const 规则 of 分类规则) {
    const 命中 = 全部类名
        .filter(名 => 规则.前缀.some(前 => 名.startsWith(前)))
        .filter(名 => !名.includes('$'))   // 内部类噪音大，速查表里排除
        .sort();

    命中.forEach(名 => 已分类.add(名));

    const 头部 =
        `# ${规则.文件.replace(/^\d+-|\.txt$/g, '')}\n` +
        `#\n` +
        `# 用途：${规则.说明}\n` +
        `# 版本：Minecraft 26.1.2（内部版本号 1.21.11）Mojang 官方映射\n` +
        `# 数量：${命中.length} 个类（已排除内部类）\n` +
        `# 来源：Mappings/官方映射原文件/client-1.21.11.txt\n` +
        `# 生成：node Mappings/工具/生成分类速查表.js\n` +
        `#\n` +
        `# 这些是 26.1.2 里真实存在的类，可直接 import。\n` +
        `# 如果某个类不在这里，说明它在本版本已改名或移除，不要凭记忆写。\n\n`;

    fs.writeFileSync(path.join(输出目录, 规则.文件), 头部 + 命中.join('\n') + '\n', 'utf8');
    汇总.push(`${规则.文件}  ${命中.length} 个类  —— ${规则.说明}`);
    console.log(`${规则.文件}：${命中.length} 个类`);
}

const 未分类 = 全部类名.filter(名 => !已分类.has(名) && !名.includes('$')).sort();
fs.writeFileSync(
    path.join(输出目录, '99-其他未分类.txt'),
    `# 其他未分类\n#\n` +
    `# 用途：不属于上述功能域的类，日常开发少用，但确实存在于 26.1.2。\n` +
    `# 数量：${未分类.length} 个类\n\n` +
    未分类.join('\n') + '\n',
    'utf8'
);
console.log(`99-其他未分类.txt：${未分类.length} 个类`);
