// 26.1.2 API 审查：Mixin 审计 v1
// 提取每个 mixin 的 @Mixin 目标、注入方法名字符串、Accessor/Invoker/Shadow 名称
// 便于与 26.1.2 参考源码逐项对照（编译期不校验这些字符串）
const fs = require('fs');
const path = require('path');

const mixin目录 = 'd:/mcaddon/26.1.2/src/main/java/com/example/addon/mixin';
const 结果 = [];
for (const f of fs.readdirSync(mixin目录)) {
    if (!f.endsWith('.java')) continue;
    const 内容 = fs.readFileSync(path.join(mixin目录, f), 'utf8');
    const 项 = { 文件: f, target: [], 注入方法: [], accessor: [], invoker: [], shadow: [], remap: [] };
    // @Mixin(value = X.class, remap = false) / @Mixin(X.class) / @Mixin(targets = "...")
    const mixin匹配 = 内容.match(/@Mixin\s*\(([^)]*)\)/);
    if (mixin匹配) {
        const 参数 = mixin匹配[1];
        const value匹配 = 参数.match(/value\s*=\s*([\w.]+)\.class/);
        const targets匹配 = 参数.match(/targets\s*=\s*"([^"]+)"/);
        const remap匹配 = 参数.match(/remap\s*=\s*(true|false)/);
        if (value匹配) 项.target.push(value匹配[1]);
        if (targets匹配) 项.target.push('字符串target: ' + targets匹配[1]);
        if (remap匹配) 项.remap.push(remap匹配[1]);
    }
    // method = "..."
    for (const m of 内容.matchAll(/method\s*=\s*"([^"]+)"/g)) 项.注入方法.push(m[1]);
    if (内容.includes('@Accessor')) {
        const m = 内容.match(/@Accessor(?:\([^)]*\))?\s*\n\s*(?:public\s+)?[\w<>]+\s+([\w$]+)\s*\(/);
        if (m) 项.accessor.push(m[1]);
    }
    if (内容.includes('@Invoker')) {
        const m = 内容.match(/@Invoker(?:\([^)]*\))?\s*\n\s*(?:public\s+)?[\w<>]+\s+([\w$]+)\s*\(/);
        if (m) 项.invoker.push(m[1]);
    }
    const ats = [...内容.matchAll(/@(?:Inject|Redirect|ModifyConstant|ModifyVariable|ModifyArg|Overwrite|WrapWithCondition|WrapOperation)(?:\(([^)]*)\))?\s*\n\s*private[^\n]*/g)].length;
    项.注入点数 = ats;
    结果.push(项);
}
for (const r of 结果) {
    console.log(`\n${r.文件}`);
    if (r.target.length) console.log('  TARGET:', r.target.join(' | '), r.remap.length ? `(remap=${r.remap.join(',')})` : '');
    if (r.remap.includes('false')) console.log('  ⚠ remap=false');
    if (r.注入方法.length) console.log('  METHOD-STRING:', [...new Set(r.注入方法)].join(', '));
    if (r.accessor.length) console.log('  ACCESSOR 方法名:', r.accessor.join(', '));
    if (r.invoker.length) console.log('  INVOKER 方法名:', r.invoker.join(', '));
}