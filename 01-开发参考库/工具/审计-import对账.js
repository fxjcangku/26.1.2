// 26.1.2 API 大审查：项目 import 对账脚本 v2
// 对照：Minecraft原始源码（Mojang官方映射）+ Meteor原始源码（含orbit）+ JAR 类名索引（net.minecraft 10208 类）
const fs = require('fs');
const path = require('path');

const 库 = path.resolve(__dirname, '..');
const MC源 = path.join(库, 'Minecraft原始源码');
const Meteor源 = path.join(库, 'Meteor原始源码');
const 类名索引 = fs.readFileSync(path.join(库, '生成的索引文件', '类名索引-26.1.2.txt'), 'utf8')
    .split(/\r?\n/).filter(l => l && !l.startsWith('#'));

const MC类集 = new Set(类名索引.map(l => l.trim()));

function 收集源码类(根) {
    const 集 = new Set();
    (function walk(dir) {
        let es; try { es = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
        for (const e of es) {
            const p = path.join(dir, e.name);
            if (e.isDirectory()) walk(p);
            else if (e.name.endsWith('.java')) {
                const rel = path.relative(根, p).replace(/\\/g, '/').replace(/\.java$/, '').replace(/\//g, '.');
                集.add(rel);
            }
        }
    })(根);
    return 集;
}
const MC源码类集 = 收集源码类(MC源);          // net.minecraft.* 与 com.mojang.*（部分）
const Meteor源码类集 = 收集源码类(Meteor源);   // meteordevelopment.meteorclient.* 与 meteordevelopment.orbit.*

function 主体名(全名) { return 全名.split('$')[0]; }

// com.mojang 依赖库（随合并 JAR 打包，但不在源码 jar 中）：先用 javap 验证存在性
const { execSync } = require('child_process');
const 项目根 = path.resolve(__dirname, '..', '..');
const 合并Jar = path.join(项目根, '.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-83e224879c/26.1.2/minecraft-merged-83e224879c-26.1.2.jar');
const 缓存已验证 = new Map();
function jar中存在(类名) {
    if (缓存已验证.has(类名)) return 缓存已验证.get(类名);
    let ok = false;
    try {
        const out = execSync(`javap -cp "${合并Jar}" "${类名}"`, { encoding: 'utf8', timeout: 30000 });
        ok = !out.includes('ClassNotFound') && (out.includes('class') || out.includes('interface') || out.includes('enum'));
    } catch (e) {
        ok = false;
    }
    缓存已验证.set(类名, ok);
    return ok;
}

const 导入 = fs.readFileSync(process.argv[2] || process.env.TEMP + '/addon-imports.txt', 'utf8')
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/).filter(l => l.trim());

const 问题 = [], 正常 = [], 通配 = [];
for (const fqn of 导入) {
    if (fqn.endsWith('.')) { 通配.push(fqn); continue; }
    if (fqn.startsWith('meteordevelopment')) {
        if (Meteor源码类集.has(主体名(fqn))) 正常.push(fqn);
        else 问题.push({ fqn, 说明: 'Meteor 26.1.2 源码中不存在' });
    } else {
        const 主体 = 主体名(fqn);
        if (MC类集.has(主体) || MC源码类集.has(主体)) { 正常.push(fqn); continue; }
        if (jar中存在(fqn)) { 正常.push(fqn + '  [com.mojang依赖库,JAR确认]'); continue; }
        问题.push({ fqn, 说明: 'Minecraft 26.1.2 JAR 与源码均不存在（可能是旧 Yarn 名/错包/内部类写法错误）' });
    }
}
// 通配 import 展开验证
const 通配问题 = [];
for (const w of 通配) {
    const 前缀 = w;
    const inMC = [...MC源码类集].some(k => k.startsWith(前缀)) || [...MC类集].some(k => k.startsWith(前缀));
    const inMeteor = [...Meteor源码类集].some(k => k.startsWith(前缀));
    if (!inMC && !inMeteor) 通配问题.push(w + '  —— 包不存在');
}

console.log('=== 对账结果 v2 ===');
console.log('总 import:', 导入.length, ' 正常:', 正常.length, ' 异常:', 问题.length, ' 通配:', 通配.length);
if (问题.length) { console.log('\n=== 异常清单 ==='); for (const p of 问题) console.log('✗', p.fqn, '——', p.说明); }
if (通配问题.length) { console.log('\n=== 通配包异常 ==='); 通配问题.forEach(x => console.log('✗', x)); }
if (!问题.length && !通配问题.length) console.log('\n全部 import 均可在 26.1.2 参考源码/JAR 中定位。');