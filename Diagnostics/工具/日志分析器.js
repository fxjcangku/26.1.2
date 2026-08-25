/**
 * 运行日志分析器 —— 把 NDJSON 证据变成可读的结论
 *
 * 用法：
 *   node Diagnostics/工具/日志分析器.js                      分析最新一份日志
 *   node Diagnostics/工具/日志分析器.js 服务器检测-开启后自动关闭   指定会话
 *   node Diagnostics/工具/日志分析器.js --列表                 列出全部日志
 *   node Diagnostics/工具/日志分析器.js <会话> --栈             只看调用栈聚合
 *   node Diagnostics/工具/日志分析器.js <会话> --时间线 40      时间线显示条数
 *
 * 为什么需要：埋点几分钟就能产出上千行 NDJSON，肉眼翻不出规律。
 * 本工具做四件人工做不动的事：
 *   1. 时间线    按毫秒排序，标出相邻事件的间隔，异常停顿一眼可见
 *   2. 调用栈聚合 同一埋点被谁调用、各占多少次 —— 定位触发方的核心手段
 *   3. 状态迁移  从事件里提取 state 字段，画出迁移路径并检测死循环
 *   4. 节律分析  判断事件是 tick 级刷屏还是稀疏触发，附带间隔分布
 */

const fs = require('fs');
const path = require('path');

const 日志目录 = path.join(__dirname, '..', '运行日志');

// ── 参数解析 ────────────────────────────────────────────
const 全部参数 = process.argv.slice(2);
const 选项 = new Set(全部参数.filter(参 => 参.startsWith('--')));
const 位置参数 = 全部参数.filter(参 => !参.startsWith('--'));

const 时间线条数 = (() => {
    const 序号 = 全部参数.indexOf('--时间线');
    const 值 = 序号 >= 0 ? parseInt(全部参数[序号 + 1], 10) : NaN;
    return Number.isFinite(值) ? 值 : 30;
})();

if (选项.has('--帮助') || 选项.has('-h')) {
    process.stdout.write(`
运行日志分析器

  node 日志分析器.js                    分析最新一份日志
  node 日志分析器.js <会话名>            分析指定会话
  node 日志分析器.js --列表              列出全部日志文件
  node 日志分析器.js <会话> --栈          只输出调用栈聚合
  node 日志分析器.js <会话> --时间线 50   调整时间线条数（默认 30）
  node 日志分析器.js <会话> --全部        输出全部事件，不截断

`);
    process.exit(0);
}

if (!fs.existsSync(日志目录)) {
    process.stderr.write(`日志目录不存在：${日志目录}\n先启动监听服务并复现问题。\n`);
    process.exit(1);
}

const 全部日志 = fs.readdirSync(日志目录)
    .filter(名 => 名.endsWith('.ndjson'))
    .map(名 => {
        const 完整 = path.join(日志目录, 名);
        const 信息 = fs.statSync(完整);
        return { 名, 完整, 修改时刻: 信息.mtimeMs, 字节: 信息.size };
    })
    .sort((a, b) => b.修改时刻 - a.修改时刻);

if (全部日志.length === 0) {
    process.stderr.write(`${日志目录} 下没有 .ndjson 日志。\n`);
    process.exit(1);
}

if (选项.has('--列表')) {
    process.stdout.write('\n可分析的日志：\n\n');
    for (const 项 of 全部日志) {
        const 大小 = 项.字节 < 1024 ? `${项.字节} B` : `${(项.字节 / 1024).toFixed(1)} KB`;
        process.stdout.write(`  ${项.名}\n    ${大小}，最后写入 ${new Date(项.修改时刻).toLocaleString('zh-CN')}\n`);
    }
    process.stdout.write('\n');
    process.exit(0);
}

// 选定目标文件：位置参数做模糊匹配，否则取最新
const 目标 = 位置参数.length > 0
    ? 全部日志.find(项 => 项.名.includes(位置参数[0]))
    : 全部日志[0];

if (!目标) {
    process.stderr.write(`找不到匹配「${位置参数[0]}」的日志。用 --列表 查看全部。\n`);
    process.exit(1);
}

// ── 读取与解析 ──────────────────────────────────────────
const 事件列表 = [];
let 坏行数 = 0;

// 剥掉 BOM：日志若被外部工具改写过，首字节可能带 \uFEFF 导致首行解析失败
for (const 行 of fs.readFileSync(目标.完整, 'utf8').replace(/^\uFEFF/, '').split('\n')) {
    if (行.trim() === '') continue;
    try {
        事件列表.push(JSON.parse(行));
    } catch {
        坏行数++;
    }
}

if (事件列表.length === 0) {
    process.stderr.write(`${目标.名} 中没有可解析的事件（坏行 ${坏行数} 条）。\n`);
    process.exit(1);
}

事件列表.sort((a, b) => (a.ts || 0) - (b.ts || 0));

const 起始 = 事件列表[0].ts || 0;
const 结束 = 事件列表[事件列表.length - 1].ts || 0;

const 时刻 = 毫秒 => {
    const d = new Date(毫秒);
    const 补 = n => String(n).padStart(2, '0');
    return `${补(d.getHours())}:${补(d.getMinutes())}:${补(d.getSeconds())}.${String(d.getMilliseconds()).padStart(3, '0')}`;
};

const 分隔 = 标题 => `\n${'═'.repeat(4)} ${标题} ${'═'.repeat(Math.max(4, 62 - 标题.length))}\n`;

/** data 字段可能是对象或字符串，统一转成单行可读文本 */
function 摘要(事件, 上限 = 150) {
    const 值 = 事件.data ?? 事件.msg ?? '';
    const 文本 = typeof 值 === 'object' ? JSON.stringify(值) : String(值);
    const 单行 = 文本.replace(/\s+/g, ' ').trim();
    return 单行.length > 上限 ? 单行.slice(0, 上限) + '…' : 单行;
}

/** 从事件各处挖出调用栈数组 */
function 取调用栈(事件) {
    const 候选 = 事件.stack ?? 事件.callerStack ?? 事件.调用栈
        ?? (事件.data && typeof 事件.data === 'object'
            ? (事件.data.stack ?? 事件.data.callerStack ?? 事件.data.调用栈)
            : null);

    if (Array.isArray(候选)) return 候选.map(String);
    if (typeof 候选 === 'string') return 候选.split(/\s*(?:<-|←|\n|;)\s*/).filter(Boolean);
    return [];
}

// ── 概览 ────────────────────────────────────────────────
process.stdout.write(分隔('概览'));
process.stdout.write(
    `  文件：${目标.名}\n` +
    `  事件：${事件列表.length} 条${坏行数 > 0 ? `（无法解析 ${坏行数} 条）` : ''}\n` +
    `  跨度：${时刻(起始)} → ${时刻(结束)}，共 ${((结束 - 起始) / 1000).toFixed(2)} 秒\n`
);

const 统计 = (取值) => {
    const 表 = new Map();
    for (const 事件 of 事件列表) {
        const 键 = 取值(事件);
        if (键 === undefined || 键 === null || 键 === '') continue;
        表.set(键, (表.get(键) || 0) + 1);
    }
    return [...表.entries()].sort((a, b) => b[1] - a[1]);
};

const 按假设 = 统计(e => e.hypothesisId);
const 按位置 = 统计(e => e.location);
const 按运行 = 统计(e => e.runId);

const 打印分布 = (标题, 数据) => {
    if (数据.length === 0) return;
    process.stdout.write(`\n  ${标题}\n`);
    for (const [键, 数] of 数据) {
        const 占比 = ((数 / 事件列表.length) * 100).toFixed(1);
        const 条 = '▍'.repeat(Math.max(1, Math.round(数 / 事件列表.length * 24)));
        process.stdout.write(`    ${String(键).padEnd(34)} ${String(数).padStart(5)} 条 ${占比.padStart(5)}%  ${条}\n`);
    }
};

打印分布('按假设', 按假设);
打印分布('按埋点位置', 按位置);
if (按运行.length > 1) 打印分布('按运行批次', 按运行);

// ── 时间线 ──────────────────────────────────────────────
if (!选项.has('--栈')) {
    const 显示全部 = 选项.has('--全部');
    const 待显示 = 显示全部 ? 事件列表 : 事件列表.slice(0, 时间线条数);

    process.stdout.write(分隔(`时间线（${待显示.length}/${事件列表.length} 条）`));

    let 上次时刻 = null;
    for (const [序, 事件] of 待显示.entries()) {
        const 间隔 = 上次时刻 === null ? 0 : (事件.ts || 0) - 上次时刻;
        上次时刻 = 事件.ts || 0;

        // 间隔超过 500ms 视为明显停顿，单独标注 —— 常是状态卡住或等待超时的信号
        const 间隔文本 = 序 === 0
            ? '       '
            : 间隔 >= 500
                ? `+${(间隔 / 1000).toFixed(2)}s`.padStart(7)
                : `+${间隔}ms`.padStart(7);

        const 标记 = 间隔 >= 500 ? ' ⟵ 明显停顿' : '';

        process.stdout.write(
            `  ${时刻(事件.ts || 0)} ${间隔文本}  ${事件.hypothesisId || '--'} ${事件.location || '未标位置'}${标记}\n`
        );
        const 内容 = 摘要(事件);
        if (内容) process.stdout.write(`                       ${内容}\n`);
    }

    if (!显示全部 && 事件列表.length > 待显示.length) {
        process.stdout.write(`\n  还有 ${事件列表.length - 待显示.length} 条，加 --全部 查看，或 --时间线 <条数> 调整。\n`);
    }
}

// ── 调用栈聚合（定位触发方的核心）────────────────────────
const 有栈事件 = 事件列表.filter(事件 => 取调用栈(事件).length > 0);

if (有栈事件.length > 0) {
    process.stdout.write(分隔('调用栈聚合'));
    process.stdout.write(`  ${有栈事件.length} 条事件带调用栈。按埋点位置分组，看各自被谁调用。\n`);

    const 按位置分组 = new Map();
    for (const 事件 of 有栈事件) {
        const 位置 = 事件.location || '未标位置';
        if (!按位置分组.has(位置)) 按位置分组.set(位置, []);
        按位置分组.get(位置).push(事件);
    }

    for (const [位置, 组] of 按位置分组) {
        process.stdout.write(`\n  ▌ ${位置}（${组.length} 次）\n`);

        // 首帧统计：谁直接调用了埋点处
        const 首帧表 = new Map();
        for (const 事件 of 组) {
            const 栈 = 取调用栈(事件);
            const 首帧 = 栈[0] || '（空栈）';
            if (!首帧表.has(首帧)) 首帧表.set(首帧, { 次数: 0, 样本: 事件 });
            首帧表.get(首帧).次数++;
        }

        const 排序首帧 = [...首帧表.entries()].sort((a, b) => b[1].次数 - a[1].次数);

        for (const [首帧, { 次数, 样本 }] of 排序首帧) {
            process.stdout.write(`    直接调用方 ×${次数}：${首帧}\n`);

            // 只对最高频的那个展开完整栈，避免输出爆炸
            if (首帧 === 排序首帧[0][0]) {
                const 栈 = 取调用栈(样本);
                for (const [层, 帧] of 栈.slice(0, 10).entries()) {
                    process.stdout.write(`      ${String(层).padStart(2)}. ${帧}\n`);
                }
                if (栈.length > 10) {
                    process.stdout.write(`      …还有 ${栈.length - 10} 帧\n`);
                }
            }
        }

        if (排序首帧.length === 1) {
            process.stdout.write(`    ⟹ 调用方唯一，可直接认定为触发源。\n`);
        } else {
            process.stdout.write(`    ⟹ 有 ${排序首帧.length} 个不同调用方，需结合时间线判断哪次是异常触发。\n`);
        }
    }
} else {
    process.stdout.write(分隔('调用栈聚合'));
    process.stdout.write(
        `  没有事件携带调用栈。\n` +
        `  若正在定位「谁触发了这个动作」，埋点必须带至少 10 帧调用栈，\n` +
        `  否则无法认定触发方（这是历史上误判过的坑）。\n`
    );
}

if (选项.has('--栈')) process.exit(0);

// ── 状态迁移 ────────────────────────────────────────────
function 取状态(事件) {
    if (事件.state) return String(事件.state);
    if (事件.data && typeof 事件.data === 'object') {
        const 值 = 事件.data.state ?? 事件.data.状态 ?? 事件.data.to;
        if (值) return String(值);
    }
    // 字符串形式的 data 里也常写成 state=XXX
    const 文本 = typeof 事件.data === 'string' ? 事件.data : '';
    const 命中 = 文本.match(/state\s*[=:]\s*([A-Z_][A-Z0-9_]*)/i);
    return 命中 ? 命中[1] : null;
}

const 状态序列 = 事件列表.map(取状态).filter(Boolean);

if (状态序列.length > 0) {
    process.stdout.write(分隔('状态迁移'));

    // 压缩连续重复，只保留真实迁移
    const 迁移路径 = 状态序列.filter((值, 序) => 序 === 0 || 值 !== 状态序列[序 - 1]);
    process.stdout.write(`  迁移路径（${迁移路径.length} 次变化）：\n    ${迁移路径.join(' → ')}\n`);

    const 边表 = new Map();
    for (let i = 1; i < 迁移路径.length; i++) {
        const 边 = `${迁移路径[i - 1]} → ${迁移路径[i]}`;
        边表.set(边, (边表.get(边) || 0) + 1);
    }

    const 排序边 = [...边表.entries()].sort((a, b) => b[1] - a[1]);
    if (排序边.length > 0) {
        process.stdout.write(`\n  迁移频次：\n`);
        for (const [边, 数] of 排序边) {
            const 循环警示 = 数 >= 5 ? '  ⟵ 高频，疑似死循环' : '';
            process.stdout.write(`    ${边.padEnd(44)} ×${数}${循环警示}\n`);
        }
    }

    // A→B→A 形式的往复是状态机卡死的典型特征
    const 往复 = new Set();
    for (let i = 2; i < 迁移路径.length; i++) {
        if (迁移路径[i] === 迁移路径[i - 2]) {
            往复.add(`${迁移路径[i - 2]} ⇄ ${迁移路径[i - 1]}`);
        }
    }
    if (往复.size > 0) {
        process.stdout.write(`\n  检测到往复迁移（状态机可能卡死）：\n`);
        for (const 项 of 往复) process.stdout.write(`    ${项}\n`);
    }
}

// ── 节律分析 ────────────────────────────────────────────
process.stdout.write(分隔('节律分析'));

const 位置节律 = new Map();
for (const 事件 of 事件列表) {
    const 位置 = 事件.location || '未标位置';
    if (!位置节律.has(位置)) 位置节律.set(位置, []);
    位置节律.get(位置).push(事件.ts || 0);
}

for (const [位置, 时刻数组] of [...位置节律.entries()].sort((a, b) => b[1].length - a[1].length)) {
    if (时刻数组.length < 2) {
        process.stdout.write(`  ${位置}：仅 1 次，单次触发\n`);
        continue;
    }

    const 间隔数组 = [];
    for (let i = 1; i < 时刻数组.length; i++) 间隔数组.push(时刻数组[i] - 时刻数组[i - 1]);

    const 平均 = 间隔数组.reduce((和, 值) => 和 + 值, 0) / 间隔数组.length;
    const 最小 = Math.min(...间隔数组);
    const 最大 = Math.max(...间隔数组);

    // MC 一 tick = 50ms，平均间隔接近这个量级说明是每 tick 都在打
    const 判定 = 平均 <= 60 ? '≈ 每 tick 触发（tick 级刷屏，考虑收窄埋点条件）'
        : 平均 <= 250 ? '高频触发'
        : 平均 <= 2000 ? '中频触发'
        : '低频/事件驱动';

    process.stdout.write(
        `  ${位置}\n` +
        `    ${时刻数组.length} 次，平均间隔 ${平均.toFixed(0)}ms（最小 ${最小}ms，最大 ${最大}ms）\n` +
        `    ${判定}\n`
    );
}

// ── 异常线索 ────────────────────────────────────────────
const 线索 = [];

const 异常事件 = 事件列表.filter(事件 => {
    const 文本 = `${事件.location || ''} ${摘要(事件, 400)}`.toLowerCase();
    return /error|exception|fail|null|崩溃|异常|失败/.test(文本);
});
if (异常事件.length > 0) {
    线索.push(`${异常事件.length} 条事件含异常关键字，重点看：${异常事件.slice(0, 3).map(e => e.location || '未标位置').join('、')}`);
}

const 未验证假设 = 按假设.filter(([, 数]) => 数 === 0);
if (按假设.length === 1) {
    线索.push(`只有 ${按假设[0][0]} 一个假设产生了事件，其余假设未被触发 —— 要么埋点没覆盖，要么该分支未执行`);
}

// 末尾长停顿常意味着流程卡在某个状态没再前进
if (事件列表.length >= 2) {
    const 末间隔 = 结束 - (事件列表[事件列表.length - 2].ts || 0);
    if (末间隔 >= 3000) {
        线索.push(`最后两条事件间隔 ${(末间隔 / 1000).toFixed(1)} 秒，流程可能卡在 ${事件列表[事件列表.length - 1].location || '末位埋点'}`);
    }
}

if (坏行数 > 0) {
    线索.push(`${坏行数} 行无法解析，检查埋点的 JSON 转义（字符串里的引号、换行必须转义）`);
}

if (线索.length > 0) {
    process.stdout.write(分隔('异常线索'));
    for (const [序, 项] of 线索.entries()) {
        process.stdout.write(`  ${序 + 1}. ${项}\n`);
    }
}

process.stdout.write(
    分隔('下一步') +
    `  1. 把上面的时间线与调用栈结论填进 Diagnostics/会话记录/进行中/ 对应文档的「证据时间线」\n` +
    `  2. 逐条判定假设成立与否，只保留证据支持的那条作为根因\n` +
    `  3. 实施最小修复，复测通过后清理埋点，文档移入 已修复/\n\n`
);
