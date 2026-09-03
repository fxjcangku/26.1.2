/**
 * 从本地 Minecraft JAR 生成类名索引
 * 用法：node Mappings/工具/从MinecraftJAR生成索引.js
 * 输出：类名索引-26.1.2.txt、简名对照-26.1.2.txt
 */

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const 项目根目录 = path.resolve(__dirname, '..', '..');
const 映射目录 = path.resolve(__dirname, '..');
const 目标版本 = '26.1.2';

// 递归查找 Minecraft merged JAR
function 查找JAR(目录) {
    if (!fs.existsSync(目录)) return null;
    for (const 项 of fs.readdirSync(目录, { withFileTypes: true })) {
        const 当前 = path.join(目录, 项.name);
        if (项.isDirectory()) {
            const 命中 = 查找JAR(当前);
            if (命中) return 命中;
        } else if (项.isFile() && 项.name.startsWith('minecraft-merged-') && 项.name.endsWith(`-${目标版本}.jar`)) {
            return 当前;
        }
    }
    return null;
}

const JAR = 查找JAR(path.join(项目根目录, '.gradle', 'loom-cache'));
if (!JAR) {
    console.error('找不到 Minecraft 26.1.2 merged JAR。请先执行 .\\gradlew.bat classes');
    process.exit(1);
}

// 列出 JAR 中所有 net.minecraft 类
const 条目 = execFileSync('jar', ['tf', JAR], {
    encoding: 'utf8',
    maxBuffer: 50 * 1024 * 1024
})
    .split(/\r?\n/)
    .filter(名称 => /^net\/minecraft\/.*\.class$/.test(名称))
    .filter(名称 => !名称.endsWith('module-info.class'))
    .map(名称 => 名称.slice(0, -6).replaceAll('/', '.'))
    .sort();

// 生成简名映射表
const 简名表 = new Map();
for (const 类名 of 条目) {
    const 简名 = 类名.split('.').pop();
    if (!简名表.has(简名)) 简名表.set(简名, []);
    简名表.get(简名).push(类名);
}

// 写入类名索引
const 输出目录 = path.join(映射目录, '生成的索引文件');
if (!fs.existsSync(输出目录)) fs.mkdirSync(输出目录, { recursive: true });

fs.writeFileSync(
    path.join(输出目录, '类名索引-26.1.2.txt'),
    `# Minecraft ${目标版本} 官方命名类名索引\n` +
    `# 来源：${path.relative(项目根目录, JAR)}\n` +
    `# 共 ${条目.length} 个 net.minecraft 类\n\n` +
    条目.join('\n') + '\n',
    'utf8'
);

// 写入简名对照表
const 简名行 = [...简名表.keys()].sort().map(简名 => `${简名}\t${简名表.get(简名).join('  |  ')}`);
fs.writeFileSync(
    path.join(输出目录, '简名对照-26.1.2.txt'),
    `# 简名 → 完整包路径对照表\n` +
    `# 来源：${path.relative(项目根目录, JAR)}\n` +
    `# 共 ${简名表.size} 个简名，同名类用 | 分隔\n\n` +
    简名行.join('\n') + '\n',
    'utf8'
);

console.log(`来源：${JAR}`);
console.log(`类名索引：${条目.length} 个类`);
console.log(`简名对照：${简名表.size} 个简名`);
