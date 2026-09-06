// 检查 addon-template.mixins.json 注册项与 mixin 目录 java 文件一一对应（漏注入检查）
const fs = require('fs');
const path = require('path');
const json = JSON.parse(fs.readFileSync('d:/mcaddon/26.1.2/src/main/resources/addon-template.mixins.json', 'utf8'));
const 目录 = 'd:/mcaddon/26.1.2/src/main/java/com/example/addon/mixin';
const 声明 = new Set(json.client || []);
const 文件 = fs.readdirSync(目录).filter(f => f.endsWith('.java')).map(f => f.replace('.java', ''));
console.log('json client 声明数:', 声明.size);
console.log('mixin 目录 java 文件数:', 文件.length);
console.log('\n=== 有 java 文件但未在 json 声明（漏注入！）===');
for (const f of 文件) if (!声明.has(f)) console.log('✗', f);
console.log('\n=== json 声明了但 java 文件不存在（死配置！）===');
for (const s of 声明) if (!文件.includes(s)) console.log('✗', s);
console.log('\n=== json 其他段 ===');
for (const k of Object.keys(json)) if (k !== 'client') console.log(k, ':', JSON.stringify(json[k]));
console.log('required:', json.required);
console.log('package:', json.package);
console.log('compatibilityLevel:', json.compatibilityLevel);