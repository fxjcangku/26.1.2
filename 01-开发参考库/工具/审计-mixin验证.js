// 26.1.2 API 审查：Mixin 目标/方法字符串存在性验证 v1
// 对照 26.1.2 参考源码：Minecraft原始源码 + Meteor原始源码 + Baritone 26.1.2 源码
// 验证 @Mixin value/targets 类存在性、method= 字符串对应的真实方法、Accessor/Invoker value
const fs = require('fs');
const path = require('path');

const 项目根 = path.resolve(__dirname, '..', '..');
const mixin目录 = path.join(项目根, 'src/main/java/com/example/addon/mixin');
const MC源 = path.join(项目根, '01-开发参考库/Minecraft原始源码');
const Meteor源 = path.join(项目根, '01-开发参考库/Meteor原始源码/meteordevelopment');
const Baritone源 = path.join(项目根, '02-Baritone自动寻路/baritone-26.1.2-source/src/main/java');

function 收集源码(根) {
    const map = new Map(); // 类FQN -> 文件路径
    const 简名Map = new Map(); // 简名 -> [FQN...]
    (function walk(dir) {
        let es; try { es = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
        for (const e of es) {
            const p = path.join(dir, e.name);
            if (e.isDirectory()) walk(p);
            else if (e.name.endsWith('.java')) {
                const rel = path.relative(根, p).replace(/\\/g, '/').replace(/\.java$/, '');
                const fqn = rel.replace(/\//g, '.');
                map.set(fqn, p);
                const 简名 = fqn.slice(fqn.lastIndexOf('.') + 1);
                if (!简名Map.has(简名)) 简名Map.set(简名, []);
                简名Map.get(简名).push(fqn);
            }
        }
    })(根);
    return { map, 简名Map };
}
const MC类 = 收集源码(MC源);
const Meteor类 = 收集源码(Meteor源);
const Baritone类 = 收集源码(Baritone源);

function 查找类(类名) {
    // 1) 全名直查（含内部类分隔符容错）
    const 基础名 = 类名.split('$')[0];
    for (const { map, 简名Map } of [MC类, Meteor类, Baritone类]) {
        if (map.has(类名)) return { 路径: map.get(类名), 域: map === MC类.map ? 'MC' : map === Meteor类.map ? 'Meteor' : 'Baritone' };
    }
    // 2) 简名查
    const 简名 = 基础名.slice(基础名.lastIndexOf('.') + 1);
    const 候选 = [];
    for (const { map, 简名Map } of [MC类, Meteor类, Baritone类]) {
        if (简名Map.has(简名)) 候选.push(...简名Map.get(简名).map(fqn => ({ 路径: map.get(fqn), 域: map === MC类.map ? 'MC' : map === Meteor类.map ? 'Meteor' : 'Baritone' })));
    }
    if (候选.length === 1) return 候选[0];
    if (候选.length > 1) {
        // 包名前缀最匹配者优先（如 meteordevelopment.meteorclient... 简名 Category）
        const 匹配 = 候选.filter(c => c.路径.includes(类名.split('.')[0]));
        if (匹配.length === 1) return 匹配[0];
        // 内部类：主类文件就是路径（内部类在同一个 .java 里）
        const 主类候选 = 候选.filter(c => !c.路径.split('/').pop().includes('$'));
        if (主类候选.length === 1) return 主类候选[0];
    }
    return null;
}
function 全名到路径(类记录) { return 类记录.路径; }

// 从方法字符串提取 Java 方法名（剥离 L...; 描述符），支持 lambda$execute$1
function 拆方法名(方法串) {
    // 格式: 方法名(参数描述)返回描述  或  方法名
    const m = 方法串.match(/^([\w$<>]+)/);
    return m ? m[1] : 方法串;
}

const 存疑 = [];
const 通过 = [];
for (const f of fs.readdirSync(mixin目录)) {
    if (!f.endsWith('.java')) continue;
    const 内容 = fs.readFileSync(path.join(mixin目录, f), 'utf8');
    const mixin匹配 = 内容.match(/@Mixin\s*\(([^)]*)\)/);
    if (!mixin匹配) continue;
    const 参数 = mixin匹配[1];
    const value匹配 = 参数.match(/value\s*=\s*([\w.]+)\.class/);
    const targets匹配 = 参数.match(/targets\s*=\s*"([^"]+)"/);
    const targets数组匹配 = 参数.match(/targets\s*=\s*\{([^}]*)\}/);
    const remap匹配 = 参数.match(/remap\s*=\s*(true|false)/);
    const remap = remap匹配 ? remap匹配[1] : '默认';

    // 解析 targets（可能多个、可能是方法级 targets）
    const target列表 = [];
    if (value匹配) target列表.push(value匹配[1]);
    if (targets匹配) targets匹配[1].split(',').forEach(t => target列表.push(t.trim()));
    if (targets数组匹配) targets数组匹配[1].split(',').forEach(t => target列表.push(t.trim().replace(/"/g, '')));
    // 方法级 @Inject(targets = ...)
    for (const m of 内容.matchAll(/@\w+\s*\([^)]*targets\s*=\s*"([^"]+)"/g)) {
        m[1].split(',').forEach(t => { const tt = t.trim(); if (tt && !tt.endsWith('.class')) target列表.push(tt); });
    }

    // 验证 targets 类存在
    const 目标类文件 = [];
    for (const t of target列表) {
        if (t === '' || /\$\d+$/.test(t)) continue; // 匿名内部类编译器生成，无法静态核
        const 命 = 查找类(t);
        if (命) 目标类文件.push(命);
        else 存疑.push(`${f}: @Mixin targets 类不存在于任何参考源码 —— ${t}`);
    }

    // 验证 method= 字符串
    for (const m of 内容.matchAll(/method\s*=\s*"([^"]+)"/g)) {
        const 方法串 = m[1];
        const 方法名 = 拆方法名(方法串);
        if (方法名 === '<init>') continue; // 构造器，已由 targets 类存在性覆盖大半
        if (方法名.startsWith('lambda$')) continue; // 编译器生成 lambda 名，静态源码无法核，跳过
        // 在 targets 类（及父类链：简单向上查 import/extends）中找方法声明
        let 命中 = false;
        for (const 文件 of 目标类文件) {
            try {
                const 源码 = fs.readFileSync(文件.路径, 'utf8');
                const re = new RegExp('\\b' + 方法名.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*\\(');
                if (re.test(源码)) { 命中 = true; break; }
            } catch (e) {}
        }
        if (命中) 通过.push(`${f}: method "${方法名}" 存在`);
        else 存疑.push(`${f}: method "${方法名}"（${方法串}）在 targets 类源码未找到（可能继承自父类/接口，需人工复核）`);
    }

    // 验证 @Accessor/@Invoker value/字段名
    for (const a of 内容.matchAll(/@Accessor(?:\(\s*(?:"([^"]+)"|value\s*=\s*"([^"]+)")\))?/g)) {
        const 字段名 = a[1] || a[2];
        if (!字段名) continue;
        let 命中 = false;
        for (const 文件 of 目标类文件) {
            try {
                const 源码 = fs.readFileSync(文件.路径, 'utf8');
                if (new RegExp('\\b' + 字段名 + '\\s*[;=]').test(源码)) { 命中 = true; break; }
            } catch (e) {}
        }
        if (命中) 通过.push(`${f}: @Accessor "${字段名}" 字段存在`);
        else 存疑.push(`${f}: @Accessor "${字段名}" 字段在 targets 类源码未找到`);
    }
    for (const iv of 内容.matchAll(/@Invoker(?:\(\s*(?:"([^"]+)"|value\s*=\s*"([^"]+)")\))?/g)) {
        const 方法名x = iv[1] || iv[2];
        if (!方法名x) continue;
        let 命中 = false;
        for (const 文件 of 目标类文件) {
            try {
                const 源码 = fs.readFileSync(文件.路径, 'utf8');
                if (new RegExp('\\b' + 方法名x + '\\s*\\(').test(源码)) { 命中 = true; break; }
            } catch (e) {}
        }
        if (命中) 通过.push(`${f}: @Invoker "${方法名x}" 方法存在`);
        else 存疑.push(`${f}: @Invoker "${方法名x}" 在 targets 类源码未找到`);
    }
}

console.log('=== 验证通过（抽样命中）:', 通过.length, '项 ===');
console.log('=== 存疑清单:', 存疑.length, '项 ===\n');
for (const s of 存疑) console.log(s);