/**
 * 26.1.2 官方映射查询工具（开发时最常用）
 *
 * 用法：
 *   node Mappings/工具/查API.js Identifier              查类：完整包路径 + 全部方法
 *   node Mappings/工具/查API.js Minecraft --方法         只列方法，不列坐标
 *   node Mappings/工具/查API.js LocalPlayer sendSystem   在类里搜含关键字的方法
 *   node Mappings/工具/查API.js --找 sendCommand         全局搜方法名，不知道在哪个类时用
 *
 * 为什么要有这个脚本：
 *   映射原文件 11MB，直接翻不动。写代码时想确认「这个方法在 26.1.2 叫什么」，
 *   跑一下就有答案，比凭记忆猜靠谱。所有输出都直接来自官方映射文件。
 */

const fs = require('fs');
const path = require('path');

const 原文件 = path.join(__dirname, '..', '官方映射原文件', 'client-1.21.11.txt');

if (!fs.existsSync(原文件)) {
    console.error(`找不到映射原文件：${原文件}`);
    console.error('请按 Mappings/说明.md 里的命令重新下载。');
    process.exit(1);
}

const 参数 = process.argv.slice(2);
if (参数.length === 0) {
    console.log(`
26.1.2 官方映射查询工具

  node 查API.js <类名>                  查类的完整路径与全部方法
  node 查API.js <类名> <方法关键字>      在指定类里搜方法
  node 查API.js --找 <方法名>            全局搜方法，不确定在哪个类时用

示例：
  node 查API.js Identifier
  node 查API.js LocalPlayer sendSystem
  node 查API.js --找 getBlockState
`);
    process.exit(0);
}

const 行数组 = fs.readFileSync(原文件, 'utf8').split('\n');

/** 解析成 { 类全名 -> [成员行, ...] } 结构 */
function 遍历(回调) {
    let 当前类 = null;
    for (const 行 of 行数组) {
        if (行.startsWith('#') || 行.trim() === '') continue;

        if (!行.startsWith(' ')) {
            // 类定义行
            const 左侧 = 行.split(' -> ')[0];
            当前类 = 左侧 ? 左侧.trim() : null;
            continue;
        }
        if (当前类) 回调(当前类, 行.trim());
    }
}

// ── 模式一：全局搜方法名 ────────────────────────────────
if (参数[0] === '--找') {
    const 关键字 = 参数[1];
    if (!关键字) {
        console.error('请提供要搜索的方法名');
        process.exit(1);
    }

    const 命中 = [];
    遍历((类名, 成员) => {
        if (!类名.startsWith('net.minecraft.')) return;
        if (成员.toLowerCase().includes(关键字.toLowerCase())) {
            命中.push({ 类名, 成员 });
        }
    });

    console.log(`\n全局搜索「${关键字}」，命中 ${命中.length} 条\n`);
    for (const { 类名, 成员 } of 命中.slice(0, 60)) {
        console.log(`${类名}`);
        console.log(`    ${清理(成员)}`);
    }
    if (命中.length > 60) console.log(`\n... 还有 ${命中.length - 60} 条，请用更精确的关键字`);
    process.exit(0);
}

// ── 模式二：查类 ───────────────────────────────────────
const 目标类 = 参数[0];
const 方法过滤 = 参数[1] && !参数[1].startsWith('--') ? 参数[1].toLowerCase() : null;

const 匹配类 = new Map();   // 完整类名 -> [成员, ...]

遍历((类名, 成员) => {
    if (!类名.startsWith('net.minecraft.')) return;
    const 简名 = 类名.split('.').pop();
    // 精确匹配简名，或完整路径包含输入
    if (简名 === 目标类 || 类名 === 目标类) {
        if (!匹配类.has(类名)) 匹配类.set(类名, []);
        匹配类.get(类名).push(成员);
    }
});

if (匹配类.size === 0) {
    console.log(`\n未找到类「${目标类}」\n`);
    console.log('这说明该类在 26.1.2 中不存在，可能已改名或移除。');
    console.log('常见改名（Yarn/旧名 → 26.1.2 官方名）：');
    console.log('  ResourceLocation → Identifier');
    console.log('  MinecraftClient  → Minecraft');
    console.log('  Text             → Component');
    console.log('  NbtCompound      → CompoundTag');
    console.log('  World            → Level');
    console.log('\n完整对照见 Mappings/易错对照表-26.1.2.txt');
    console.log('模糊查找可用：node 查API.js --找 <关键字>\n');
    process.exit(1);
}

/** 去掉行号前缀，只留签名 */
function 清理(成员行) {
    // 格式可能是 `9:10:void method() -> a` 或 `void field -> a`
    return 成员行.replace(/^\d+:\d+:/, '');
}

for (const [类名, 成员列表] of 匹配类) {
    console.log(`\n═══ ${类名} ═══`);
    console.log(`import ${类名};\n`);

    const 方法 = [];
    const 字段 = [];
    for (const 成员 of 成员列表) {
        const 签名 = 清理(成员);
        if (方法过滤 && !签名.toLowerCase().includes(方法过滤)) continue;
        (签名.includes('(') ? 方法 : 字段).push(签名);
    }

    if (字段.length > 0) {
        console.log(`字段（${字段.length}）：`);
        字段.forEach(f => console.log(`  ${f}`));
        console.log('');
    }
    if (方法.length > 0) {
        console.log(`方法（${方法.length}）：`);
        方法.forEach(m => console.log(`  ${m}`));
    }
    if (方法过滤 && 方法.length === 0 && 字段.length === 0) {
        console.log(`该类中没有含「${方法过滤}」的成员`);
    }
}
console.log('');
