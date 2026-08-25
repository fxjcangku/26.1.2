/**
 * 从 Mojang 官方映射原文件提取可查阅索引
 *
 * 用法：node Mappings/工具/提取映射索引.js
 *
 * 输入：Mappings/官方映射原文件/client-1.21.11.txt
 * 输出：Mappings/类名索引-26.1.2.txt        全部 net.minecraft 类的官方全名
 *       Mappings/简名对照-26.1.2.txt        简名 → 完整包路径（写 import 时查）
 *
 * 原理：官方映射每行格式为 `完整类名 -> 混淆名:`，我们只取箭头左侧，
 * 那就是 26.1.2 里真实存在的官方映射类名。
 */

const fs = require('fs');
const path = require('path');

const 根目录 = path.join(__dirname, '..');
const 原文件 = path.join(根目录, '官方映射原文件', 'client-1.21.11.txt');

if (!fs.existsSync(原文件)) {
    console.error(`找不到映射原文件：${原文件}`);
    console.error('请先运行下载命令，见 Mappings/说明.md');
    process.exit(1);
}

const 全部类名 = [];
const 简名表 = new Map();   // 简名 -> [完整类名, ...]

const 内容 = fs.readFileSync(原文件, 'utf8');
const 行数组 = 内容.split('\n');

for (const 行 of 行数组) {
    // 类定义行不以空格开头，且以冒号结尾
    if (行.startsWith(' ') || 行.startsWith('#') || !行.includes(' -> ')) continue;

    const 完整名 = 行.split(' -> ')[0].trim();
    if (!完整名.startsWith('net.minecraft.')) continue;

    全部类名.push(完整名);

    // 内部类用 $ 分隔，取最后一段作为简名
    const 简名 = 完整名.split('.').pop();
    if (!简名表.has(简名)) 简名表.set(简名, []);
    简名表.get(简名).push(完整名);
}

全部类名.sort();

fs.writeFileSync(
    path.join(根目录, '类名索引-26.1.2.txt'),
    `# Minecraft 26.1.2（内部版本 1.21.11）官方映射类名索引\n` +
    `# 共 ${全部类名.length} 个类，由 Mappings/工具/提取映射索引.js 生成\n` +
    `# 用途：确认某个类在 26.1.2 里是否存在、完整包路径是什么\n\n` +
    全部类名.join('\n') + '\n',
    'utf8'
);

const 简名行 = [...简名表.keys()].sort().map(简名 => {
    const 列表 = 简名表.get(简名);
    return 列表.length === 1 ? `${简名}\t${列表[0]}` : `${简名}\t${列表.join('  |  ')}`;
});

fs.writeFileSync(
    path.join(根目录, '简名对照-26.1.2.txt'),
    `# 简名 → 完整包路径对照表（写 import 时查这个）\n` +
    `# 共 ${简名表.size} 个简名，同名类用 | 分隔\n\n` +
    简名行.join('\n') + '\n',
    'utf8'
);

console.log(`类名索引：${全部类名.length} 个类`);
console.log(`简名对照：${简名表.size} 个简名`);
