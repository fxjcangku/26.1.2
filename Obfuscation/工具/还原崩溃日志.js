#!/usr/bin/env node
/**
 * 崩溃日志还原工具
 *
 * 混淆版 jar 崩溃时，日志里的类名是 a / b / v$1 这种短名，没法直接定位。
 * 这个脚本读映射存档，把日志里的混淆名换回真实类名和方法名。
 *
 * 用法：
 *   node 还原崩溃日志.js <崩溃日志路径>              自动挑最新版本的映射
 *   node 还原崩溃日志.js <崩溃日志路径> 1.1-beta1    指定版本的映射
 *   node 还原崩溃日志.js --查 v                      单独查一个混淆名对应什么
 *   node 还原崩溃日志.js --列表                      看有哪些版本的映射存档
 */

const fs = require('fs');
const path = require('path');

const 存档目录 = path.join(__dirname, '..', '映射存档');

/** 列出所有映射存档，按版本号文件名排序 */
function 列出存档() {
    if (!fs.existsSync(存档目录)) return [];
    return fs.readdirSync(存档目录)
        .filter(f => f.startsWith('混淆映射-v') && f.endsWith('.txt'))
        .sort();
}

/**
 * 解析 ProGuard 映射文件
 *
 * 格式（类行不缩进，成员行缩进）：
 *   com.example.addon.modules.AutoFarmMatrix -> v:
 *       void onActivate() -> a
 *
 * 建反向索引：混淆名 → 原名
 */
function 解析映射(文件路径) {
    const 内容 = fs.readFileSync(文件路径, 'utf8');
    const 类映射 = new Map();   // v            → com.example.addon.modules.AutoFarmMatrix
    const 成员映射 = new Map(); // v#a          → void onActivate()
    let 当前混淆类 = null;

    for (const 原始行 of 内容.split(/\r?\n/)) {
        if (!原始行.trim() || 原始行.trimStart().startsWith('#')) continue;

        const 是成员行 = /^\s/.test(原始行);
        const 行 = 原始行.trim();
        const 箭头 = 行.indexOf(' -> ');
        if (箭头 === -1) continue;

        const 左 = 行.slice(0, 箭头).trim();
        const 右 = 行.slice(箭头 + 4).replace(/:$/, '').trim();

        if (!是成员行) {
            当前混淆类 = 右;
            类映射.set(右, 左);
        } else if (当前混淆类) {
            成员映射.set(`${当前混淆类}#${右}`, 左);
        }
    }

    return { 类映射, 成员映射 };
}

/** 选映射文件：指定版本就用那个，否则用最新的 */
function 选映射(指定版本) {
    const 存档 = 列出存档();
    if (存档.length === 0) {
        console.log('\n映射存档目录里没有文件。');
        console.log('跑一次 gradlew buildOfficial 会自动生成。\n');
        process.exit(1);
    }

    if (指定版本) {
        const 目标 = `混淆映射-v${指定版本}.txt`;
        if (!存档.includes(目标)) {
            console.log(`\n找不到版本 ${指定版本} 的映射存档。现有：`);
            存档.forEach(f => console.log(`  ${f}`));
            console.log('');
            process.exit(1);
        }
        return path.join(存档目录, 目标);
    }

    return path.join(存档目录, 存档[存档.length - 1]);
}

/** 还原日志：逐行替换混淆名 */
function 还原日志(日志路径, 指定版本) {
    if (!fs.existsSync(日志路径)) {
        console.log(`\n找不到日志文件：${日志路径}\n`);
        process.exit(1);
    }

    const 映射路径 = 选映射(指定版本);
    const { 类映射, 成员映射 } = 解析映射(映射路径);

    console.log(`\n使用映射：${path.basename(映射路径)}`);
    console.log(`已加载 ${类映射.size} 个类、${成员映射.size} 个成员\n`);
    console.log('─'.repeat(70));

    const 日志 = fs.readFileSync(日志路径, 'utf8');
    let 替换次数 = 0;

    const 还原后 = 日志.split(/\r?\n/).map(行 => {
        let 结果 = 行;

        // 栈帧形如 at v.a(Unknown Source)，先按「类.方法」整体匹配
        结果 = 结果.replace(/\b([a-zA-Z0-9$]{1,4})\.([a-zA-Z0-9$]{1,4})\(/g, (全文, 类, 方法) => {
            const 真类名 = 类映射.get(类);
            if (!真类名) return 全文;

            const 成员 = 成员映射.get(`${类}#${方法}`);
            替换次数++;
            // 成员签名形如 "void onActivate()"，只取方法名部分
            const 方法名 = 成员 ? (成员.match(/(\w+)\s*\(/)?.[1] ?? 方法) : 方法;
            return `${真类名}.${方法名}(`;
        });

        // 剩下的裸类名（例如异常类型），单独再扫一遍
        结果 = 结果.replace(/\b([a-zA-Z0-9$]{1,4})\b/g, (全文, 名) => {
            const 真类名 = 类映射.get(名);
            if (!真类名) return 全文;
            替换次数++;
            return 真类名;
        });

        return 结果;
    }).join('\n');

    console.log(还原后);
    console.log('─'.repeat(70));
    console.log(`\n共替换 ${替换次数} 处\n`);
}

/** 查单个混淆名 */
function 查名字(混淆名, 指定版本) {
    const 映射路径 = 选映射(指定版本);
    const { 类映射, 成员映射 } = 解析映射(映射路径);

    console.log(`\n使用映射：${path.basename(映射路径)}\n`);

    const 真类名 = 类映射.get(混淆名);
    if (真类名) {
        console.log(`${混淆名}  →  ${真类名}\n`);

        const 成员 = [...成员映射.entries()]
            .filter(([键]) => 键.startsWith(`${混淆名}#`))
            .map(([键, 值]) => [键.split('#')[1], 值]);

        if (成员.length > 0) {
            console.log(`该类的成员（${成员.length} 个）：`);
            成员.forEach(([混淆, 原始]) => console.log(`  ${混淆.padEnd(6)} → ${原始}`));
        }
        console.log('');
        return;
    }

    // 类里没找到，去成员里模糊找
    const 命中 = [...成员映射.entries()].filter(([键]) => 键.endsWith(`#${混淆名}`));
    if (命中.length > 0) {
        console.log(`「${混淆名}」是成员名，出现在 ${命中.length} 个类里：\n`);
        命中.forEach(([键, 值]) => {
            const 类 = 键.split('#')[0];
            console.log(`  ${类映射.get(类) ?? 类}`);
            console.log(`    → ${值}`);
        });
        console.log('');
        return;
    }

    console.log(`映射里没有「${混淆名}」。`);
    console.log('可能它本来就没被混淆（入口类、基类、Mixin 类都保留原名）。\n');
}

// ── 入口 ──

const 参数 = process.argv.slice(2);

if (参数.length === 0) {
    console.log(`
崩溃日志还原工具

  node 还原崩溃日志.js <崩溃日志路径> [版本]
  node 还原崩溃日志.js --查 <混淆名> [版本]
  node 还原崩溃日志.js --列表

不指定版本时用最新的映射存档。
`);
    process.exit(0);
}

if (参数[0] === '--列表') {
    const 存档 = 列出存档();
    if (存档.length === 0) {
        console.log('\n还没有映射存档，跑一次 gradlew buildOfficial 就有了。\n');
    } else {
        console.log(`\n映射存档（${存档.length} 个）：\n`);
        存档.forEach(f => {
            const 大小 = (fs.statSync(path.join(存档目录, f)).size / 1024).toFixed(1);
            console.log(`  ${f}  (${大小} KB)`);
        });
        console.log('');
    }
} else if (参数[0] === '--查') {
    if (!参数[1]) {
        console.log('\n要查什么？例：node 还原崩溃日志.js --查 v\n');
        process.exit(1);
    }
    查名字(参数[1], 参数[2]);
} else {
    还原日志(参数[0], 参数[1]);
}
