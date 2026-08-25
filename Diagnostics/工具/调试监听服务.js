/**
 * yiyiaddon 运行时调试监听服务
 *
 * 用途：接收游戏内埋点通过 HTTP 上报的事件，落盘为 NDJSON 供分析。
 * 启动：node .dbg/工具/调试监听服务.js
 * 停止：Ctrl+C（会打印本次会话的统计摘要）
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const 端口 = 7777;
const 日志目录 = path.join(__dirname, '..', '运行日志');
fs.mkdirSync(日志目录, { recursive: true });

// ── 运行时统计 ──────────────────────────────────────────
const 统计 = {
    启动时刻: Date.now(),
    总事件数: 0,
    解析失败数: 0,
    按会话: new Map(),   // 会话 -> 事件数
    按假设: new Map(),   // 假设 -> 事件数
    按位置: new Map()    // 埋点位置 -> 事件数
};

const 计数 = (表, 键) => 表.set(键, (表.get(键) || 0) + 1);

/** 同一位置的重复事件做频率折叠，避免 tick 级日志刷屏 */
const 上次输出 = new Map();
const 折叠窗口毫秒 = 1000;

function 时间戳(毫秒) {
    const d = new Date(毫秒);
    const 补零 = n => String(n).padStart(2, '0');
    return `${补零(d.getHours())}:${补零(d.getMinutes())}:${补零(d.getSeconds())}.${String(d.getMilliseconds()).padStart(3, '0')}`;
}

function 安全文件名(值) {
    // 会话名来自外部输入，禁止路径穿越
    return String(值).replace(/[^\u4e00-\u9fa5\w.-]/g, '_');
}

http.createServer((请求, 响应) => {
    // 健康检查：浏览器或脚本可直接访问确认服务存活
    if (请求.method === 'GET' && 请求.url === '/健康') {
        响应.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        响应.end(JSON.stringify({
            状态: '运行中',
            端口,
            已运行秒数: Math.round((Date.now() - 统计.启动时刻) / 1000),
            总事件数: 统计.总事件数,
            会话列表: [...统计.按会话.keys()]
        }));
        return;
    }

    if (请求.method !== 'POST' || 请求.url !== '/event') {
        响应.writeHead(404).end();
        return;
    }

    let 原文 = '';
    请求.on('data', 块 => 原文 += 块);
    请求.on('end', () => {
        let 会话 = '未知会话';
        let 行;
        let 事件 = null;

        try {
            事件 = JSON.parse(原文);
            会话 = 事件.sessionId || 会话;
            if (!事件.ts) 事件.ts = Date.now();
            行 = JSON.stringify(事件);
        } catch (错误) {
            统计.解析失败数++;
            行 = JSON.stringify({ sessionId: 会话, 原始内容: 原文, 解析错误: String(错误), ts: Date.now() });
        }

        统计.总事件数++;
        计数(统计.按会话, 会话);
        if (事件) {
            if (事件.hypothesisId) 计数(统计.按假设, 事件.hypothesisId);
            if (事件.location) 计数(统计.按位置, 事件.location);
        }

        const 文件 = path.join(日志目录, `调试日志-${安全文件名(会话)}.ndjson`);
        fs.appendFile(文件, 行 + '\n', 'utf8', 错误 => {
            if (错误) process.stderr.write(`写盘失败：${错误.message}\n`);
        });

        // 控制台摘要：同一埋点位置 1 秒内只打一条，末尾标注折叠数量
        if (事件) {
            const 键 = `${会话}|${事件.location}`;
            const 现在 = Date.now();
            const 上次 = 上次输出.get(键);
            if (!上次 || 现在 - 上次.时刻 >= 折叠窗口毫秒) {
                const 折叠提示 = 上次 && 上次.折叠 > 0 ? `（期间折叠 ${上次.折叠} 条）` : '';
                const 数据 = String(事件.data ?? '').slice(0, 160);
                process.stdout.write(
                    `[${时间戳(事件.ts)}] ${事件.hypothesisId || '--'} ${事件.location || '未标位置'}${折叠提示}\n` +
                    `    ${数据}\n`
                );
                上次输出.set(键, { 时刻: 现在, 折叠: 0 });
            } else {
                上次.折叠++;
            }
        }

        响应.writeHead(204).end();
    });
}).listen(端口, '127.0.0.1', () => {
    process.stdout.write(
        `调试监听服务已启动\n` +
        `  接收地址：http://127.0.0.1:${端口}/event\n` +
        `  健康检查：http://127.0.0.1:${端口}/健康\n` +
        `  日志目录：${日志目录}\n` +
        `  按 Ctrl+C 停止并查看统计摘要\n\n`
    );
});

process.on('SIGINT', () => {
    const 排序输出 = (标题, 表) => {
        if (表.size === 0) return '';
        const 行 = [...表.entries()]
            .sort((a, b) => b[1] - a[1])
            .map(([键, 数]) => `    ${键}：${数} 条`)
            .join('\n');
        return `  ${标题}\n${行}\n`;
    };

    process.stdout.write(
        `\n═══ 本次监听统计 ═══\n` +
        `  运行时长：${Math.round((Date.now() - 统计.启动时刻) / 1000)} 秒\n` +
        `  总事件数：${统计.总事件数}（解析失败 ${统计.解析失败数}）\n` +
        排序输出('按会话', 统计.按会话) +
        排序输出('按假设', 统计.按假设) +
        排序输出('按埋点位置', 统计.按位置) +
        `  日志目录：${日志目录}\n`
    );
    process.exit(0);
});
