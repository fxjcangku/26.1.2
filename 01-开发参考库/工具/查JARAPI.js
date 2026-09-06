/**
 * 从本地 Minecraft JAR 查询类和方法
 * 用法：
 *   node 03-映射表/工具/查JARAPI.js Minecraft
 *   node 03-映射表/工具/查JARAPI.js LocalPlayer sendSystem
 *   node 03-映射表/工具/查JARAPI.js --找 sendCommand
 */

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const 项目根目录 = path.resolve(__dirname, '..', '..');
const 索引文件 = path.resolve(__dirname, '..', '生成的索引文件', '类名索引-26.1.2.txt');
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

if (!fs.existsSync(索引文件)) {
    console.error('找不到类名索引，请先执行：node 03-映射表/工具/从MinecraftJAR生成索引.js');
    process.exit(1);
}

const JAR = 查找JAR(path.join(项目根目录, '.gradle', 'loom-cache'));
if (!JAR) {
    console.error('找不到 Minecraft 26.1.2 merged JAR，请先执行：.\\gradlew.bat classes');
    process.exit(1);
}

const 参数 = process.argv.slice(2);
if (参数.length === 0) {
    console.log('用法：');
    console.log('  node 查JARAPI.js <类名>');
    console.log('  node 查JARAPI.js <类名> <方法关键字>');
    console.log('  node 查JARAPI.js --找 <方法名>');
    process.exit(0);
}

// 读取类名索引
const 类名 = fs.readFileSync(索引文件, 'utf8')
    .split(/\r?\n/)
    .filter(行 => 行 && !行.startsWith('#'));

// 匹配类名（支持简名和全名）
function 匹配类(目标) {
    return 类名.filter(全名 => 全名 === 目标 || 全名.endsWith(`.${目标}`));
}

// 用 javap 反编译类
function 反编译(全名) {
    const 输出 = execFileSync('javap', ['-classpath', JAR, '-p', '-s', 全名], { encoding: 'utf8' });
    return 输出.split(/\r?\n/).filter(行 => 行.trim() && !行.startsWith('Compiled from'));
}

// 模式一：全局搜方法
if (参数[0] === '--找') {
    const 关键字 = 参数[1];
    if (!关键字) {
        console.error('请提供搜索关键字');
        process.exit(1);
    }
    let 命中 = 0;
    for (const 全名 of 类名) {
        let 行数组;
        try { 行数组 = 反编译(全名); } catch { continue; }
        const 结果 = 行数组.filter(行 => 行.toLowerCase().includes(关键字.toLowerCase()));
        if (!结果.length) continue;
        console.log(`\n${全名}`);
        console.log(结果.join('\n'));
        命中 += 结果.length;
        if (命中 >= 80) break;
    }
    console.log(`\n命中约 ${命中} 条`);
    process.exit(0);
}

// 模式二：查类
const 匹配 = 匹配类(参数[0]);
if (!匹配.length) {
    console.error(`找不到类：${参数[0]}`);
    console.error('提示：ResourceLocation → Identifier, MinecraftClient → Minecraft, Text → Component');
    process.exit(1);
}

const 过滤词 = 参数[1] && !参数[1].startsWith('--') ? 参数[1].toLowerCase() : null;
for (const 全名 of 匹配) {
    const 行数组 = 反编译(全名);
    const 结果 = 过滤词 ? 行数组.filter(行 => 行.toLowerCase().includes(过滤词)) : 行数组;
    console.log(`\n${全名}`);
    console.log(结果.join('\n'));
}
