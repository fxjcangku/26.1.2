// 后台管理页面 HTML（iOS 26 玻璃拟态风格）
// 设计要点：
//  - 动态渐变背景（mesh gradient）让毛玻璃有内容可透
//  - 真 backdrop-filter 毛玻璃 + 半透明卡片 + 细描边
//  - 深色/浅色主题用 data-theme 完整切换，localStorage 持久化
//  - 列表/按钮/sheet 全部带 cubic-bezier 过渡与 hover/press 动效
// 说明：本文件为“网站代码”，允许使用 emoji；个人 addon Java 代码仍禁止 emoji。
export const ADMIN_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<meta name="color-scheme" content="light dark">
<title>yiyiaddon 控制台</title>
<style>
/* ── 主题变量（浅色默认，深色用 [data-theme="dark"]） ── */
:root {
  --text: #1C1C1E;
  --text2: #6E6E73;
  --blue: #007AFF;
  --green: #34C759;
  --red: #FF3B30;
  --orange: #FF9500;
  --indigo: #5856D6;
  --glass: rgba(255,255,255,0.52);
  --glass-strong: rgba(255,255,255,0.66);
  --glass-border: rgba(255,255,255,0.55);
  --fill: rgba(120,120,128,0.12);
  --hairline: rgba(60,60,67,0.14);
  --shadow: 0 8px 32px rgba(0,0,0,0.10);
  --sheet-bg: rgba(252,252,252,0.98);
  --blob-op: 1;
}
[data-theme="dark"] {
  --text: #F2F2F7;
  --text2: #98989F;
  --blue: #0A84FF;
  --green: #30D158;
  --red: #FF453A;
  --orange: #FF9F0A;
  --indigo: #5E5CE6;
  --glass: rgba(28,28,30,0.52);
  --glass-strong: rgba(44,44,46,0.62);
  --glass-border: rgba(255,255,255,0.10);
  --fill: rgba(120,120,128,0.22);
  --hairline: rgba(84,84,88,0.55);
  --shadow: 0 8px 32px rgba(0,0,0,0.5);
  --sheet-bg: rgba(28,28,30,0.98);
  --blob-op: 0.45;
}

* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body { margin: 0; padding: 0; }
html { color-scheme: light; }
html[data-theme="dark"] { color-scheme: dark; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text",
    "Helvetica Neue", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", system-ui, sans-serif;
  color: var(--text);
  -webkit-font-smoothing: antialiased;
  min-height: 100vh;
  overflow-x: hidden;
  transition: color .3s ease;
}
button { font-family: inherit; }

/* ── 动态渐变背景（毛玻璃透视的内容）
   去掉 filter:blur 与持续动画，避免整屏大背景每帧重绘导致下滑卡顿；
   radial-gradient 本身足够柔和，视觉通透感不变。 */
body::before {
  content: ''; position: fixed; inset: 0; z-index: -1;
  background:
    radial-gradient(45% 45% at 12% 18%, rgba(0,122,255,0.22), transparent 70%),
    radial-gradient(40% 40% at 88% 12%, rgba(255,45,85,0.18), transparent 70%),
    radial-gradient(48% 48% at 82% 86%, rgba(52,199,89,0.18), transparent 70%),
    radial-gradient(42% 42% at 10% 92%, rgba(255,149,0,0.16), transparent 70%);
  opacity: var(--blob-op);
  transition: opacity .5s ease;
}

/* ── 通用过渡 ── */
.card, .seg, .icon-btn, .primary, .sheet, .kpi, .chip, .tabbar button {
  transition: transform .22s cubic-bezier(.32,.72,.33,1), background .25s ease,
    box-shadow .25s ease, color .25s ease, border-color .25s ease, opacity .2s ease;
}

/* ── 登录 ── */
#login { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; padding: 24px; }
.login-card {
  width: 100%; max-width: 360px; padding: 38px 30px; border-radius: 30px;
  background: var(--glass-strong);
  backdrop-filter: blur(40px) saturate(180%);
  -webkit-backdrop-filter: blur(40px) saturate(180%);
  border: 1px solid var(--glass-border);
  box-shadow: var(--shadow);
  animation: pop .45s cubic-bezier(.32,.72,.33,1);
}
@keyframes pop { from { opacity: 0; transform: translateY(14px) scale(.98); } to { opacity: 1; transform: none; } }
.login-card .logo { display: flex; justify-content: center; margin-bottom: 6px; }
.login-card .logo-mark {
  width: 64px; height: 64px; border-radius: 18px;
  background: linear-gradient(135deg, #0A84FF, #5856D6);
  color: #fff; font-size: 34px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 10px 28px rgba(10,132,255,.38);
}
.login-card h1 { text-align: center; font-size: 24px; font-weight: 700; margin: 12px 0 4px; letter-spacing: -.3px; }
.login-card p { text-align: center; color: var(--text2); margin: 0 0 28px; font-size: 14px; }
.field { margin-bottom: 14px; }
.field input {
  width: 100%; height: 50px; border-radius: 14px; border: 1px solid var(--glass-border);
  background: var(--fill); color: var(--text); padding: 0 16px; font-size: 16px; outline: none;
  transition: border-color .2s ease, background .2s ease;
}
.field input:focus { border-color: var(--blue); background: var(--glass-strong); }
/* 下拉选择器：与输入框统一样式（用于聊天收件人选择） */
.field select {
  width: 100%; height: 50px; border-radius: 14px; border: 1px solid var(--glass-border);
  background: var(--fill); color: var(--text); padding: 0 14px; font-size: 16px; outline: none;
  transition: border-color .2s ease, background .2s ease;
}
.field select:focus { border-color: var(--blue); background: var(--glass-strong); }
.primary {
  width: 100%; height: 50px; border: none; border-radius: 14px; background: var(--blue);
  color: #fff; font-size: 17px; font-weight: 600; cursor: pointer; box-shadow: 0 6px 18px rgba(0,122,255,.32);
}
.primary:active { transform: scale(.97); }
.err { color: var(--red); font-size: 13px; text-align: center; min-height: 18px; margin-top: 10px; }

/* ── 应用外壳 ── */
#app { display: none; }
nav.topbar {
  position: sticky; top: 0; z-index: 20; display: flex; align-items: center; gap: 10px;
  padding: 12px 18px; padding-top: calc(12px + env(safe-area-inset-top));
  background: var(--glass);
  backdrop-filter: blur(30px) saturate(180%);
  -webkit-backdrop-filter: blur(30px) saturate(180%);
  border-bottom: 1px solid var(--hairline);
}
nav.topbar .title { font-size: 22px; font-weight: 700; flex: 1; letter-spacing: -.3px; }
nav.topbar .icon-btn {
  min-width: 44px; height: 44px; border-radius: 12px; border: 1px solid var(--glass-border);
  background: var(--glass); color: var(--text); font-size: 18px; cursor: pointer;
}
nav.topbar .icon-btn:active { transform: scale(.92); }

.segmented {
  display: flex; gap: 4px; padding: 4px; background: var(--fill); border-radius: 14px; overflow-x: auto;
  margin: 14px 18px 0; max-width: 960px;
}
.segmented .seg {
  flex: 1; min-width: 84px; height: 40px; border: none; border-radius: 10px; background: transparent;
  color: var(--text2); font-size: 15px; font-weight: 600; cursor: pointer; white-space: nowrap;
  padding: 0 12px;
}
.segmented .seg.active { background: var(--glass-strong); color: var(--text); box-shadow: 0 2px 8px rgba(0,0,0,.14); }
.container { max-width: 960px; margin: 0 auto; padding: 16px 18px calc(96px + env(safe-area-inset-bottom)); }

/* ── 毛玻璃卡片（iOS 玻璃）
   滚动区卡片去掉 backdrop-filter 与入场动画，改半透明纯色，
   避免大量 blur 叠加与重复动画导致下滑卡顿。 */
.card {
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: 22px; box-shadow: var(--shadow); margin-bottom: 18px; overflow: hidden;
}
.sec-title { font-size: 13px; color: var(--text2); text-transform: uppercase; letter-spacing: .5px; padding: 16px 18px 10px; font-weight: 600; }

/* ── KPI ── */
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.kpi { padding: 16px; border-radius: 20px; background: var(--glass); border: 1px solid var(--glass-border); box-shadow: var(--shadow); }
.kpi:hover { transform: translateY(-2px); }
.kpi .icon { font-size: 22px; }
.kpi .num { font-size: 28px; font-weight: 700; margin: 6px 0 2px; letter-spacing: -.5px; }
.kpi .lbl { font-size: 13px; color: var(--text2); }

/* ── 列表行 ── */
.row { display: flex; align-items: center; gap: 13px; padding: 13px 18px; border-bottom: 1px solid var(--hairline); cursor: pointer; }
.row:last-child { border-bottom: none; }
.row:hover { background: rgba(120,120,128,0.08); }
.row:active { transform: scale(.995); }
.skin { width: 44px; height: 44px; border-radius: 50%; background: transparent; flex-shrink: 0; image-rendering: auto; box-shadow: inset 0 0 0 1px var(--hairline); object-fit: cover; }
.row .info { flex: 1; min-width: 0; }
.row .name { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.row .badges { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 3px; }
.row .sub { font-size: 13px; color: var(--text2); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.row .right { text-align: right; font-size: 13px; color: var(--text2); flex-shrink: 0; }

.dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; background: var(--hairline); }
.dot.on { background: var(--green); box-shadow: 0 0 0 3px rgba(52,199,89,0.22); animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }

.badge { display: inline-flex; align-items: center; gap: 3px; font-size: 11px; padding: 2px 8px; border-radius: 999px; font-weight: 600; }
.badge.green { background: rgba(52,199,89,0.16); color: var(--green); }
.badge.red { background: rgba(255,59,48,0.16); color: var(--red); }
.badge.gray { background: var(--fill); color: var(--text2); }
.badge.blue { background: rgba(0,122,255,0.14); color: var(--blue); }

.tag { display: inline-block; font-size: 12px; padding: 3px 9px; border-radius: 8px; background: var(--fill); color: var(--text2); margin: 2px 3px 2px 0; }
.tag.blue { background: rgba(0,122,255,0.14); color: var(--blue); }

.lat { font-weight: 700; }
.lat.good { color: var(--green); }
.lat.mid { color: var(--orange); }
.lat.bad { color: var(--red); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; word-break: break-all; }
.copy-btn { flex-shrink: 0; height: 30px; padding: 0 12px; border-radius: 15px; border: 1px solid var(--glass-border); background: var(--fill); color: var(--text); font-size: 12px; font-weight: 600; cursor: pointer; }
.copy-btn:active { transform: scale(.94); }

/* ── 筛选 chip ── */
.toolbar { display: flex; gap: 8px; align-items: center; padding: 2px 18px 14px; flex-wrap: wrap; }
.toolbar .search {
  flex: 1; min-width: 140px; height: 40px; border-radius: 12px; border: 1px solid var(--glass-border);
  background: var(--fill); color: var(--text); padding: 0 14px; font-size: 15px; outline: none;
}
.toolbar .search:focus { border-color: var(--blue); }
.chip { height: 34px; padding: 0 14px; border-radius: 999px; border: 1px solid var(--glass-border); background: var(--glass); color: var(--text2); font-size: 13px; font-weight: 600; cursor: pointer; }
.chip.on { background: var(--blue); border-color: var(--blue); color: #fff; }
.chip:active { transform: scale(.94); }

/* ── 图表 ── */
.bars { display: flex; align-items: flex-end; gap: 8px; height: 130px; padding: 14px 16px 6px; }
.bar { flex: 1; background: linear-gradient(180deg, var(--blue), rgba(0,122,255,.35)); border-radius: 6px 6px 0 0; min-height: 2px; }
.bar-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 5px; }
.bar-wrap .v { font-size: 10px; color: var(--text2); }
.bar-wrap .d { font-size: 11px; color: var(--text2); white-space: nowrap; text-align: center; }

/* ── 底部 Tab 栏（移动端） ── */
.tabbar {
  display: none; position: fixed; bottom: 0; left: 0; right: 0; z-index: 30;
  background: var(--glass-strong); border-top: 1px solid var(--hairline);
  padding: 6px 8px calc(6px + env(safe-area-inset-bottom));
  backdrop-filter: blur(30px) saturate(180%); -webkit-backdrop-filter: blur(30px) saturate(180%);
}
.tabbar button {
  flex: 1; min-height: 48px; border: none; background: transparent; color: var(--text2);
  font-size: 11px; display: flex; flex-direction: column; align-items: center; gap: 2px; cursor: pointer;
}
.tabbar button .e { font-size: 20px; }
.tabbar button.active { color: var(--blue); }

/* ── 返回顶部按钮（下滑后出现，iOS 毛玻璃圆形按钮） ── */
.backtop {
  position: fixed; right: 18px; bottom: calc(84px + env(safe-area-inset-bottom)); z-index: 25;
  width: 46px; height: 46px; border-radius: 50%; border: 1px solid var(--glass-border);
  background: var(--glass-strong); color: var(--blue); font-size: 20px; cursor: pointer;
  backdrop-filter: blur(30px) saturate(180%); -webkit-backdrop-filter: blur(30px) saturate(180%);
  box-shadow: var(--shadow); display: flex; align-items: center; justify-content: center;
  opacity: 0; pointer-events: none; transform: translateY(12px);
  transition: opacity .25s ease, transform .25s ease;
}
.backtop.show { opacity: 1; pointer-events: auto; transform: translateY(0); }
.backtop:active { transform: scale(.9); }
@media (min-width: 721px) {
  .backtop { bottom: 26px; right: 26px; }
}

/* ── 底部弹窗（bottom sheet） ── */
.sheet-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 40; display: none; opacity: 0; transition: opacity .3s ease; }
.sheet-overlay.open { display: block; opacity: 1; }
.sheet {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 41;
  background: var(--sheet-bg); border: 1px solid var(--glass-border);
  border-radius: 30px 30px 0 0; padding: 12px 20px calc(26px + env(safe-area-inset-bottom));
  max-height: 82vh; overflow-y: auto;
  transform: translateY(105%); transition: transform .36s cubic-bezier(.32,.72,.33,1.02);
}
.sheet.open { transform: translateY(0); }
.sheet .grab { width: 36px; height: 5px; border-radius: 3px; background: var(--hairline); margin: 4px auto 16px; }
.sheet-head { position: sticky; top: 0; z-index: 3; display: flex; align-items: center; gap: 10px; background: var(--sheet-bg); padding: 4px 0 10px; }
.sheet-head .grab { margin: 0 auto; }
.sheet-close { min-width: 34px; height: 34px; padding: 0 10px; border-radius: 17px; border: 1px solid var(--glass-border); background: var(--fill); color: var(--text); font-size: 15px; cursor: pointer; flex-shrink: 0; }
.sheet h2 { margin: 0 0 4px; font-size: 22px; font-weight: 700; letter-spacing: -.3px; display: flex; align-items: center; gap: 10px; }
.sheet .skin-head { width: 48px; height: 48px; border-radius: 50%; background: transparent; box-shadow: inset 0 0 0 1px var(--hairline); }
.sheet .kv { display: flex; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid var(--hairline); font-size: 15px; gap: 12px; }
.sheet .kv:last-child { border-bottom: none; }
.sheet .kv .k { color: var(--text2); flex-shrink: 0; }
.sheet .kv .val { font-weight: 600; text-align: right; word-break: break-all; }

/* ── 消息 ── */
.chat { max-height: 460px; overflow-y: auto; }
.chat .msg { margin: 0 0 10px; padding: 10px 14px; border-radius: 18px; background: var(--fill); max-width: 82%; }
.chat .msg.admin { background: rgba(0,122,255,0.18); margin-left: auto; }
.chat .msg .meta { font-size: 11px; color: var(--text2); margin-bottom: 3px; }
.chat .msg .txt { font-size: 15px; }
.composer { display: flex; gap: 8px; margin-top: 12px; }
.composer input { flex: 1; height: 46px; border-radius: 12px; border: 1px solid var(--glass-border); background: var(--fill); color: var(--text); padding: 0 14px; font-size: 15px; outline: none; }
.composer button { min-width: 46px; height: 46px; border-radius: 12px; border: none; background: var(--blue); color: #fff; font-size: 18px; cursor: pointer; }

.pad { padding: 16px 18px; }

/* 响应式：手机 / 电脑自动调整按钮位置 */
@media (max-width: 720px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .segmented { display: none; }
  .tabbar { display: flex; }
  .row { padding: 12px 14px; }
  .row .right { display: none; }
  .container { padding-bottom: calc(96px + env(safe-area-inset-bottom)); }
}
</style>
</head>
<body>

<div id="login">
  <div class="login-card">
    <div class="logo"><div class="logo-mark">Y</div></div>
    <h1>yiyiaddon</h1>
    <p>后台管理控制台</p>
    <div class="field"><input id="lg-user" type="text" placeholder="用户名" autocomplete="username"></div>
    <div class="field"><input id="lg-pass" type="password" placeholder="密码" autocomplete="current-password"></div>
    <button class="primary" id="lg-btn" type="button" onclick="(function(b){var u=document.getElementById('lg-user').value.trim(),p=document.getElementById('lg-pass').value,e=document.getElementById('lg-err');e.textContent='登录中…';b.disabled=true;fetch('/api/admin/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:u,password:p})}).then(function(r){return r.json();}).then(function(x){if(x.success&&x.token){localStorage.setItem('admin_token',x.token);location.reload();}else{e.textContent=x.error||'登录失败';b.disabled=false;}}).catch(function(){e.textContent='网络错误：无法连接后台';b.disabled=false;});})(this); return false;">登 录</button>
    <div class="err" id="lg-err"></div>
  </div>
</div>

<div id="app">
  <nav class="topbar">
    <div class="title">🍋 yiyiaddon</div>
    <button class="icon-btn" id="theme-btn" title="切换主题">🌙</button>
    <button class="icon-btn" id="logout-btn" title="退出">🚪</button>
  </nav>

  <div class="segmented" id="segmented"></div>
  <div class="container" id="view"></div>

  <div class="tabbar" id="tabbar"></div>
  <button class="backtop" id="backtop" title="回到顶部" aria-label="回到顶部">⬆️</button>
</div>

<div class="sheet-overlay" id="sheet-overlay"></div>
<div class="sheet" id="sheet"></div>

<script>
var API_BASE = 'https://yiyiaddonadmin.fxjggyx.workers.dev';
var state = {
  token: localStorage.getItem('admin_token') || null,
  tab: 'overview',
  players: [],
  analytics: {},
  timer: null,
  isMobile: window.matchMedia('(max-width: 720px)').matches,
  playerFilter: '',
  playerStatus: 'all'
};

var TABS = [
  { id: 'overview', e: '📊', label: '概览' },
  { id: 'players', e: '👥', label: '玩家' },
  { id: 'premium', e: '🔑', label: '正版账号' },
  { id: 'passwords', e: '🔐', label: '离线密码' },
  { id: 'chat', e: '💬', label: '消息' },
  { id: 'issues', e: '⚠️', label: '异常' },
  { id: 'settings', e: '⚙️', label: '设置' }
];

// ── 工具函数 ──
function $(id) { return document.getElementById(id); }
function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function fmtTime(ts) {
  if (!ts) return '—';
  var d = new Date(ts);
  var now = Date.now();
  var diff = now - ts;
  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前';
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前';
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'});
}
function fmtDuration(ms) {
  if (ms === null || ms === undefined) return '';
  var m = Math.floor(ms / 60000);
  if (m < 1) return '0 分钟';
  if (m < 60) return m + ' 分钟';
  return Math.floor(m / 60) + ' 小时 ' + (m % 60) + ' 分';
}
function flagEmoji(code) {
  if (!code || code.length !== 2) return '🌐';
  code = code.toUpperCase();
  return String.fromCodePoint(0x1F1E6 + code.charCodeAt(0) - 65, 0x1F1E6 + code.charCodeAt(1) - 65);
}
function countryName(code) {
  if (!code) return '未知';
  var upper = code.toUpperCase();
  // 优先用浏览器自动翻译
  try { 
    var translated = new Intl.DisplayNames(['zh-CN'], {type:'region'}).of(upper);
    if (translated && translated !== upper) return translated;
  } catch(e) {}
  // 备用手动翻译（兜底）
  var manual = {
    'CN':'中国', 'US':'美国', 'JP':'日本', 'KR':'韩国', 'TW':'台湾', 'HK':'香港', 'MO':'澳门',
    'SG':'新加坡', 'GB':'英国', 'DE':'德国', 'FR':'法国', 'CA':'加拿大', 'AU':'澳大利亚',
    'RU':'俄罗斯', 'IN':'印度', 'BR':'巴西', 'MX':'墨西哥', 'ES':'西班牙', 'IT':'意大利',
    'NL':'荷兰', 'SE':'瑞典', 'CH':'瑞士', 'TH':'泰国', 'VN':'越南', 'MY':'马来西亚',
    'ID':'印度尼西亚', 'PH':'菲律宾', 'PL':'波兰', 'TR':'土耳其', 'AR':'阿根廷'
  };
  return manual[upper] || upper;
}
// 常见城市 -> 中文（VPN 出口高频城市优先）
var CITY_ZH = { "Los Angeles":"洛杉矶","New York":"纽约","San Francisco":"旧金山","San Jose":"圣何塞","Seattle":"西雅图","Chicago":"芝加哥","Dallas":"达拉斯","Houston":"休斯顿","Atlanta":"亚特兰大","Miami":"迈阿密","Phoenix":"凤凰城","Denver":"丹佛","Ashburn":"阿什本","Buffalo":"布法罗","Fremont":"弗里蒙特","Santa Clara":"圣克拉拉","Boardman":"博德曼","London":"伦敦","Frankfurt":"法兰克福","Paris":"巴黎","Amsterdam":"阿姆斯特丹","Berlin":"柏林","Madrid":"马德里","Moscow":"莫斯科","Tokyo":"东京","Osaka":"大阪","Seoul":"首尔","Singapore":"新加坡","Hong Kong":"香港","Taipei":"台北","Sydney":"悉尼","Melbourne":"墨尔本","Toronto":"多伦多","Vancouver":"温哥华","Shanghai":"上海","Beijing":"北京","Guangzhou":"广州","Shenzhen":"深圳","Hangzhou":"杭州","Chengdu":"成都","Nanjing":"南京","Wuhan":"武汉" };
// 常见地区/州/省 -> 中文
var REGION_ZH = { "California":"加利福尼亚州","New York":"纽约州","Texas":"得克萨斯州","Washington":"华盛顿州","Virginia":"弗吉尼亚州","Illinois":"伊利诺伊州","Florida":"佛罗里达州","Georgia":"佐治亚州","Massachusetts":"马萨诸塞州","Pennsylvania":"宾夕法尼亚州","Ohio":"俄亥俄州","Michigan":"密歇根州","Arizona":"亚利桑那州","Colorado":"科罗拉多州","Oregon":"俄勒冈州","Utah":"犹他州","Nevada":"内华达州","New Jersey":"新泽西州","North Carolina":"北卡罗来纳州","Missouri":"密苏里州","Ontario":"安大略省","Quebec":"魁北克省","British Columbia":"不列颠哥伦比亚省","England":"英格兰","Hesse":"黑森州","Hessen":"黑森州","Bavaria":"巴伐利亚州","North Rhine-Westphalia":"北莱茵-威斯特法伦州","Tokyo":"东京都","Osaka":"大阪府","Seoul":"首尔","Guangdong":"广东省","Zhejiang":"浙江省","Beijing":"北京市","Shanghai":"上海市","Jiangsu":"江苏省","Sichuan":"四川省","Fujian":"福建省","Shandong":"山东省" };
// 常见运营商/机房/云厂商 -> 中文（数据中心类标注“机房”，方便识别为梯子出口）
var ISP_ZH = { "fdcservers":"FDC 机房（数据中心）","m247":"M247 机房（数据中心）","choopa":"Choopa 机房（数据中心）","colocrossing":"ColoCrossing 机房","psychz":"Psychz 机房","quadranet":"QuadraNet 机房","ovh":"OVH 机房","hetzner":"Hetzner 机房","contabo":"Contabo 机房","leaseweb":"LeaseWeb 机房","digitalocean":"DigitalOcean 云","linode":"Linode 云","vultr":"Vultr 云","cloudflare":"Cloudflare","amazon":"亚马逊云（AWS）","google":"谷歌云（GCP）","microsoft":"微软云（Azure）","oracle":"甲骨文云（Oracle）","alibaba":"阿里云","aliyun":"阿里云","tencent":"腾讯云","huawei":"华为云","china telecom":"中国电信","china unicom":"中国联通","china mobile":"中国移动","comcast":"康卡斯特（Comcast）","verizon":"Verizon","deutsche telekom":"德国电信","ntt":"NTT（日本）","kddi":"KDDI（日本）","cogent":"Cogent（骨干网）" };
function fmtCity(c) { return c ? (CITY_ZH[c] || c) : ''; }
function fmtRegion(r) { return r ? (REGION_ZH[r] || r) : ''; }
function fmtIsp(org) {
  if (!org) return '';
  // 去掉冗余的 AS 编号（如 (AS30058) / AS30058），只保留运营商主体更易读
  var cleaned = String(org).replace(/\\(?\\s*AS\\d+\\s*\\)?/gi, '').replace(/[\\s,;]+$/, '').trim();
  if (!cleaned) return String(org).trim();
  var key = cleaned.toLowerCase();
  for (var k in ISP_ZH) { if (key.indexOf(k) !== -1) return ISP_ZH[k]; }
  return cleaned;
}
// 玩家当前状态徽标：主菜单 / 单人世界 / 多人服务器
function statusBadge(s) {
  if (s === 'menu') return '<span class="badge blue">🏠 主菜单</span>';
  if (s === 'singleplayer') return '<span class="badge blue">🎮 单人世界</span>';
  return '<span class="badge green">🌐 多人服务器</span>';
}
function latClass(v) { if (v === null || v === undefined) return ''; if (v < 60) return 'good'; if (v < 150) return 'mid'; return 'bad'; }
// XUID 仅接受纯数字；authlib 未注入时返回的 auth_xuid 占位符一律按空处理，避免显示异常字符串
function fmtXuid(x) {
  if (x == null) return null;
  var v = String(x).trim();
  return /^\d{8,20}$/.test(v) ? v : null;
}
// Meteor 模块名 -> 中文映射（由 zh_cn.json 生成）
var MODULE_ZH = {"weather-changer":"天气更改","air-jump":"空中跳跃","auto-fish":"自动钓鱼","name-protect":"名称保护","velocity":"反击退","no-ghost-blocks":"防幽灵方块","bed-aura":"床光环","auto-jump":"自动连跳","ambience":"环境","better-tooltips":"更好的提示框","notifier":"通知器","item-physics":"物品物理","air-place":"空中放置","enderman-look":"末影人注视","excavator":"挖掘机","timer":"全局加速","surround":"自我包围","no-rotate":"无旋转","long-jump":"远跳","trajectories":"弹道预测","chams":"实体渲染","server-spoof":"服务器伪装","fullbright":"全局亮度","freecam":"灵魂出窍","exp-thrower":"经验投掷器","auto-totem":"自动图腾","book-bot":"书机器人","auto-gap":"自动金苹果","packet-logger":"数据包记录器","middle-click-extra":"中键增强","logout-spots":"登出标记","nuker":"范围破坏","sound-blocker":"声音屏蔽","vein-miner":"连锁采集","auto-trap":"自动陷阱","wall-hack":"透视","no-render":"禁止渲染","hand-view":"手部视角","instant-rebreak":"瞬间重新破坏","liquid-interact":"液体交互","crystal-aura":"水晶光环","spider":"蜘蛛","auto-log":"自动下线","high-jump":"高跳","speed":"速度","offhand":"副手","arrow-dodge":"箭矢闪避","multitask":"多任务","auto-replenish":"自动补充","break-indicators":"破坏指示器","potion-saver":"药水节约器","block-esp":"方块透视","packet-mine":"数据包挖掘","liquid-filler":"液体填充","quiver":"箭袋","chest-swap":"胸甲交换","fast-use":"快速使用","anchor":"锚点","anti-anvil":"防铁砧","highway-builder":"高速路建造者","infinity-miner":"无限矿工","breadcrumbs":"足迹","auto-brewer":"自动酿造器","storage-esp":"存储物透视","no-status-effects":"禁止状态效果","attribute-swap":"属性切换","echest-farmer":"末影箱农场","self-anvil":"自身铁砧","flamethrower":"火焰喷射器","anti-bed":"防床","auto-mend":"自动修补","auto-anvil":"自动铁砧","burrow":"钻地","auto-sign":"自动告示牌","swarm":"群组","auto-reconnect":"自动重连","build-height":"建筑高度","time-changer":"时间更改","hole-esp":"坑洞透视","city-esp":"连基透视","anti-afk":"防 AFK","gui-move":"GUI 中移动","trident-boost":"三叉戟助推","better-tab":"更好的 Tab 列表","mount-bypass":"骑乘绕过","anti-packet-kick":"防数据包踢出","light-overlay":"光照覆盖层","hole-filler":"坑洞填充","message-aura":"消息光环","fast-climb":"快速攀爬","sprint":"自动疾跑","xray":"X 光","notebot":"音符盒机器人","item-highlight":"物品高亮","auto-mount":"自动骑乘","zoom":"缩放","bow-spam":"弓连射","free-look":"自由视角","auto-walk":"自动行走","self-trap":"自我陷阱","safe-walk":"安全行走","auto-wasp":"自动黄蜂","break-delay":"破坏延迟","auto-armor":"自动盔甲","pop-chams":"图腾残影","ghost-hand":"幽灵之手","slippy":"滑溜","auto-tool":"自动工具","anti-hunger":"抗饥饿","auto-clicker":"自动点击器","auto-shearer":"自动剪毛","boss-stack":"BOSS 条合并","camera-tweaks":"相机调整","auto-weapon":"自动武器","better-chat":"更好的聊天","offhand-crash":"副手崩溃","auto-breed":"自动繁殖","spawn-proofer":"防刷怪","step":"自动上阶","entity-owner":"实体所有者","scaffold":"脚手架","sneak":"自动潜行","no-slow":"无减速","fake-player":"假人","nametags":"名称标签","esp":"实体透视","rotation":"朝向锁定","elytra-fly":"鞘翅飞行","auto-respawn":"自动重生","entity-control":"实体控制","stash-finder":"藏匿点查找器","better-beacons":"更好的信标","auto-nametag":"自动命名牌","elytra-boost":"鞘翅助推","blink":"闪现","block-selection":"方块选择","auto-city":"自动连基","waypoints":"路径点","auto-web":"自动蜘蛛网","auto-eat":"自动进食","hitboxes":"碰撞箱","trail":"轨迹粒子","self-web":"自我蜘蛛网","flight":"飞行","reverse-step":"快速下落","blur":"模糊背景","discord-presence":"Discord 状态","void-esp":"虚空透视","reach":"超长臂展","speed-mine":"快速挖掘","inventory-tweaks":"背包调整","no-fall":"防摔落","no-interact":"禁止交互","portals":"传送门","marker":"标记","criticals":"暴击","tunnel-esp":"隧道透视","auto-smelter":"自动冶炼","anchor-aura":"重生锚光环","anti-void":"防虚空","kill-aura":"杀戮光环","parkour":"跑酷","spam":"刷屏","collisions":"碰撞箱","click-tp":"点击传送","packet-canceller":"数据包取消器","tracers":"射线","auto-exp":"自动经验","jesus":"水上行走","no-mining-trace":"无挖掘痕迹","bow-aimbot":"弓自瞄","anti-kick-bypass":"终极防踢","flight-bypass":"飞行绕过","server-detector":"服务器检测"};
function fmtModule(n) { return MODULE_ZH[n] || n; }

// 维度汉化
var DIM_ZH = { overworld: '主世界', the_nether: '下界', the_end: '末地' };
function fmtDimension(d) {
  if (!d) return null;
  var key = String(d).toLowerCase().replace(/^minecraft:/, '');
  return DIM_ZH[key] || d;
}
// 游戏模式汉化
var MODE_ZH = { survival: '生存', creative: '创造', adventure: '冒险', spectator: '旁观' };
function fmtGameMode(m) {
  if (!m) return null;
  return MODE_ZH[String(m).toLowerCase()] || m;
}
// 时区显示：区域中文简称 + UTC 数字偏移（去掉冗长的「夏令时间」全称，只留易懂数字）
var TZ_REGION_ZH = { America: '北美', Asia: '亚洲', Europe: '欧洲', Africa: '非洲', Oceania: '大洋洲', Australia: '澳洲' };
function fmtTimezone(tz) {
  if (!tz) return '';
  var seg = String(tz).split('/');
  var region = TZ_REGION_ZH[seg[0]] || seg[0] || '';
  var off = '';
  try {
    var parts = new Intl.DateTimeFormat('en-US', { timeZone: tz, timeZoneName: 'shortOffset' }).formatToParts(new Date());
    var g = parts.find(function(p){ return p.type === 'timeZoneName'; });
    if (g && g.value) off = String(g.value).replace('GMT', 'UTC');
  } catch (e) {}
  var base = region || tz;
  return off ? base + '（' + off + '）' : base;
}
// 皮肤头像：统一走 mc-heads.net 原始皮肤（按游戏名解析，正版名自动命中 Mojang），
// canvas 手动裁剪头部正面 + 保留 Alpha，透明皮肤像素不再渲染成黑块（crafatar 已停服 500）。
function skin(p, size) {
  var s = size || 64;
  var name = (p && p.name) ? String(p.name) : 'MHF_Steve';
  var raw = 'https://mc-heads.net/skin/' + encodeURIComponent(name);
  var fb = 'https://mc-heads.net/avatar/' + encodeURIComponent(name) + '/' + s;
  return '<canvas class="skin" width="' + s + '" height="' + s + '" data-head-raw="' + raw + '" data-head-fallback="' + fb + '"></canvas>';
}

// 把原始皮肤头部正面的 8x8 区域绘制到 canvas，保留透明像素（透明不再渲染成黑块）
function drawHead(canvas, img) {
  var c = canvas.getContext('2d');
  var s = canvas.width;
  c.clearRect(0, 0, s, s);
  c.imageSmoothingEnabled = false; // 像素风，清晰显示每个像素
  try { c.drawImage(img, 8, 8, 8, 8, 0, 0, s, s); } catch (e) {}   // 头部正面（底层，8x8）
  try { c.drawImage(img, 40, 8, 8, 8, 0, 0, s, s); } catch (e) {}  // 帽子/第二层（40,8，同样保留 alpha）
}
// 扫描 DOM 中新出现的皮肤 canvas 并异步加载原始皮肤纹理渲染
function renderHeadCanvases(root) {
  var scope = root || document;
  scope.querySelectorAll('canvas[data-head-raw]').forEach(function(canvas) {
    if (canvas.__headRendered) return;
    canvas.__headRendered = true;
    var raw = canvas.getAttribute('data-head-raw');
    var fb = canvas.getAttribute('data-head-fallback');
    var img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = function() { drawHead(canvas, img); };
    img.onerror = function() {
      var fbImg = new Image();
      fbImg.crossOrigin = 'anonymous';
      fbImg.onload = function() {
        var c = canvas.getContext('2d');
        c.clearRect(0, 0, canvas.width, canvas.height);
        c.imageSmoothingEnabled = false;
        c.drawImage(fbImg, 0, 0, canvas.width, canvas.height);
      };
      fbImg.src = fb;
    };
    img.src = raw;
  });
}
// 监听 DOM 变化，列表/详情每次刷新后自动渲染新的皮肤头像
new MutationObserver(function(muts) {
  muts.forEach(function(m) {
    (m.addedNodes || []).forEach(function(n) {
      if (n.nodeType === 1) renderHeadCanvases(n);
    });
  });
}).observe(document.body, { childList: true, subtree: true });

// 内联 MD5（与 worker.js 同款，浏览器端计算离线 UUID 用）
function md5(message) {
  const rot = (v, c) => (v << c) | (v >>> (32 - c));
  const K = new Int32Array([
    0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee, 0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
    0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be, 0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
    0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa, 0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
    0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed, 0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
    0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c, 0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
    0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05, 0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
    0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039, 0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
    0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1, 0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391,
  ]);
  const S = [7,12,17,22,7,12,17,22,7,12,17,22,7,12,17,22,5,9,14,20,5,9,14,20,5,9,14,20,5,9,14,20,4,11,16,23,4,11,16,23,4,11,16,23,4,11,16,23,6,10,15,21,6,10,15,21,6,10,15,21,6,10,15,21];
  const bytes = new TextEncoder().encode(String(message));
  const n = bytes.length;
  const padded = new Uint8Array((((n + 8) >>> 6) + 1) << 6);
  padded.set(bytes);
  padded[n] = 0x80;
  const dv = new DataView(padded.buffer);
  dv.setUint32(padded.length - 8, (n << 3) >>> 0, true);
  dv.setUint32(padded.length - 4, Math.floor((n << 3) / 0x100000000), true);

  let a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
  for (let off = 0; off < padded.length; off += 64) {
    const M = new Int32Array(16);
    for (let i = 0; i < 16; i++) M[i] = dv.getInt32(off + i * 4, true);
    let A = a0, B = b0, C = c0, D = d0;
    for (let i = 0; i < 64; i++) {
      let f, g;
      if (i < 16) { f = (B & C) | (~B & D); g = i; }
      else if (i < 32) { f = (D & B) | (~D & C); g = (5 * i + 1) & 15; }
      else if (i < 48) { f = B ^ C ^ D; g = (3 * i + 5) & 15; }
      else { f = C ^ (B | ~D); g = (7 * i) & 15; }
      const tmp = D;
      D = C; C = B;
      B = (B + rot((A + f + K[i] + M[g]) | 0, S[i])) | 0;
      A = tmp;
    }
    a0 = (a0 + A) | 0; b0 = (b0 + B) | 0; c0 = (c0 + C) | 0; d0 = (d0 + D) | 0;
  }

  function hex(w) {
    let s = '';
    for (let i = 0; i < 4; i++) s += ((w >>> (i * 8)) & 0xff).toString(16).padStart(2, '0');
    return s;
  }
  return hex(a0) + hex(b0) + hex(c0) + hex(d0);
}

function offlineUuid(name) {
  const d = md5('OfflinePlayer:' + String(name));
  const variant = (x) => ((parseInt(x, 16) & 0x3) | 0x8).toString(16);
  return d.slice(0, 8) + '-' + d.slice(8, 12) + '-' + '3' + d.slice(13, 16)
    + '-' + variant(d[16]) + d.slice(17, 20) + '-' + d.slice(20, 32);
}

// ── 主题切换（真·深色/浅色，localStorage 持久化） ──
function applyTheme(t) {
  if (t !== 'light' && t !== 'dark') {
    t = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.dataset.theme = t;
  $('theme-btn').textContent = t === 'dark' ? '☀️' : '🌙';
  localStorage.setItem('theme', t);
}
function initTheme() {
  var saved = localStorage.getItem('theme');
  if (saved === 'light' || saved === 'dark') { applyTheme(saved); }
  else { applyTheme(window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'); }
}
function toggleTheme() {
  var cur = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
  applyTheme(cur === 'dark' ? 'light' : 'dark');
}

// ── HTTP 请求 ──
function api(path, opts) {
  opts = opts || {};
  var headers = { 'Content-Type': 'application/json' };
  if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
  return fetch(API_BASE + path, { method: opts.method || 'GET', headers: headers, body: opts.body ? JSON.stringify(opts.body) : undefined })
    .then(function(r) { return r.json().catch(function(){ return {}; }).then(function(j){ j._status = r.status; return j; }); })
    .catch(function(){ return { _status: 0, error: '网络错误：无法连接后台，请检查网络或 VPN 节点', _network: true }; });
}

// ── 登录 ──
function tryLogin() {
  var u = $('lg-user').value.trim(), p = $('lg-pass').value;
  $('lg-err').textContent = '';
  api('/api/admin/login', { method: 'POST', body: { username: u, password: p } }).then(function(res) {
    if (res._network) {
      $('lg-err').textContent = res.error || '网络错误';
    } else if (res.success && res.token) {
      state.token = res.token;
      localStorage.setItem('admin_token', res.token);
      showApp();
    } else {
      $('lg-err').textContent = res.error || '登录失败';
    }
  }).catch(function(err) {
    $('lg-err').textContent = '网络错误：' + (err.message || '无法连接后台');
  });
}
function logout() {
  state.token = null;
  localStorage.removeItem('admin_token');
  $('app').style.display = 'none';
  $('login').style.display = 'flex';
  if (state.timer) { clearInterval(state.timer); state.timer = null; }
}

// ── 应用初始化 ──
function showApp() {
  $('login').style.display = 'none';
  $('app').style.display = 'block';
  buildNav();
  switchTab('overview');
  if (state.timer) clearInterval(state.timer);
  state.timer = setInterval(refreshIfNeeded, 15000);
}
function buildSegmented() {
  var seg = $('segmented'); seg.innerHTML = '';
  TABS.forEach(function(t) {
    var b = document.createElement('button');
    b.className = 'seg' + (t.id === state.tab ? ' active' : '');
    b.textContent = t.e + ' ' + t.label;
    b.onclick = function() { switchTab(t.id); };
    seg.appendChild(b);
  });
}
function buildTabbar() {
  var tb = $('tabbar'); tb.innerHTML = '';
  TABS.forEach(function(t) {
    var b = document.createElement('button');
    b.className = t.id === state.tab ? 'active' : '';
    b.innerHTML = '<span class="e">' + t.e + '</span>' + t.label;
    b.onclick = function() { switchTab(t.id); };
    tb.appendChild(b);
  });
}
function buildNav() { buildSegmented(); buildTabbar(); }

function switchTab(id) {
  state.tab = id;
  buildSegmented(); buildTabbar();
  if (id === 'overview') loadOverview();
  else if (id === 'players') loadPlayers();
  else if (id === 'premium') loadPremium();
  else if (id === 'passwords') loadPasswords();
  else if (id === 'chat') loadChat();
  else if (id === 'issues') loadIssues();
  else if (id === 'settings') loadSettings();
}
function refreshIfNeeded() {
  if (state.tab === 'players') loadPlayers();
  if (state.tab === 'overview') loadOverview();
  if (state.tab === 'premium') loadPremium();
  if (state.tab === 'chat') loadChat();
}

// ── 概览 ──
function loadOverview() {
  api('/api/admin/analytics').then(function(a) {
    if (a._status === 401 || a._status === 403) { return logout(); }
    state.analytics = a;
    var html = '';
    html += '<div class="kpi-grid">';
    html += kpi('🌍', a.total_users, '总玩家');
    html += kpi('🟢', a.online_count, '当前在线');
    html += kpi('🔒', a.vpn_suspected, '疑似VPN');
    html += kpi('✅', (a.premium && a.premium.premium) || 0, '正版账户');
    html += '</div>';

    html += '<div class="card"><div class="sec-title">最近 14 天活跃</div>';
    html += '<div class="bars">';
    var max = 1;
    (a.daily_active || []).forEach(function(d){ if (d.active > max) max = d.active; });
    (a.daily_active || []).forEach(function(d){
      var h = Math.max(2, Math.round(d.active / max * 96));
      var dd = new Date(d.date + 'T00:00:00');
      var label = (dd.getMonth() + 1) + '月' + dd.getDate() + '日';
      html += '<div class="bar-wrap"><span class="v">' + d.active + '</span><div class="bar" style="height:' + h + 'px"></div><span class="d">' + label + '</span></div>';
    });
    html += '</div></div>';

    html += '<div class="card"><div class="sec-title">国家分布</div>';
    (a.country_distribution || []).slice(0, 12).forEach(function(c){
      html += '<div class="row"><div style="font-size:24px;flex-shrink:0;">' + flagEmoji(c.client_country) + '</div><div class="info"><div class="name">' + countryName(c.client_country) + '</div></div><div class="right">' + c.c + ' 人</div></div>';
    });
    html += '</div>';

    html += '<div class="card"><div class="sec-title">版本分布</div>';
    (a.version_distribution || []).slice(0, 10).forEach(function(c){
      html += '<div class="row"><div class="info"><div class="name">' + esc(c.version) + '</div></div><div class="right">' + c.c + ' 人</div></div>';
    });
    html += '</div>';

    $('view').innerHTML = html;
  });
}
function kpi(icon, num, label) {
  return '<div class="kpi"><div class="icon">' + icon + '</div><div class="num">' + (num || 0) + '</div><div class="lbl">' + label + '</div></div>';
}

// ── 玩家（带搜索 + 筛选） ──
function loadPlayers() {
  api('/api/admin/players').then(function(res) {
    if (res._status === 401 || res._status === 403) { return logout(); }
    state.players = res.users || [];
    var list = state.players.filter(function(p) {
      var q = state.playerFilter.trim().toLowerCase();
      if (q) {
        var hay = (p.name || '') + ' ' + (p.uuid || '') + ' ' + (p.client_country || '') + ' ' + (p.server_name || '');
        if (hay.toLowerCase().indexOf(q) < 0) return false;
      }
      if (state.playerStatus === 'online' && !p.is_online) return false;
      if (state.playerStatus === 'offline' && p.is_online) return false;
      if (state.playerStatus === 'premium' && !p.is_premium) return false;
      if (state.playerStatus === 'vpn' && !p.is_vpn_suspected) return false;
      return true;
    });
    var online = 0;
    state.players.forEach(function(p){ if (p.is_online) online++; });

    var html = '<div style="padding:2px 4px 4px;" class="sec-title">共 ' + state.players.length + ' 人 · 在线 ' + online + '</div>';
    html += '<div class="toolbar">';
    html += '<input class="search" id="p-search" type="text" placeholder="搜索名字 / UUID / 国家 / 服务器" value="' + esc(state.playerFilter) + '">';
    html += chip('all', '全部');
    html += chip('online', '🟢 在线');
    html += chip('offline', '⚪ 离线');
    html += chip('premium', '✅ 正版');
    html += chip('vpn', '🔒 VPN');
    html += '</div>';
    html += '<div class="card">';
    list.forEach(function(p) {
      html += playerRow(p);
    });
    if (!list.length) html += '<div class="row">暂无匹配玩家</div>';
    html += '</div>';
    $('view').innerHTML = html;

    var si = $('p-search');
    if (si) si.oninput = function(){ state.playerFilter = si.value; loadPlayers(); };
  });
}
function chip(val, label) {
  return '<button class="chip' + (state.playerStatus === val ? ' on' : '') + '" data-f="' + val + '">' + label + '</button>';
}
function playerRow(p) {
  var badges = '';
  badges += p.is_premium ? '<span class="badge green">✅正版</span>' : '<span class="badge gray">🔓离线账号</span>';
  badges += p.is_online ? '<span class="badge green">🟢在线</span>' : '<span class="badge gray">⚪离线</span>';
  // 仅在在线时才显示状态徽标，离线玩家不再残留「多人服务器」等上一次状态
  if (p.is_online) badges += statusBadge(p.status);
  if (p.is_vpn_suspected) badges += '<span class="badge red">🔒VPN</span>';
  var sub = (p.client_country ? flagEmoji(p.client_country) + ' ' + countryName(p.client_country) : '') +
    (p.client_city ? ' · ' + esc(fmtCity(p.client_city)) : '') +
    (p.client_timezone ? ' · ⏰ ' + esc(fmtTimezone(p.client_timezone)) : '') +
    (p.client_as_org ? ' · 📡 ' + esc(fmtIsp(p.client_as_org)) : '') +
    (p.server_name ? ' · 🎮 ' + esc(p.server_name) : '');
  var lat = '';
  // 只有在线玩家才显示延迟
  if (p.is_online) {
    if (p.server_latency !== null && p.server_latency !== undefined && p.server_latency > 0) lat += '🖥 <span class="lat ' + latClass(p.server_latency) + '">' + Math.round(p.server_latency) + 'ms</span>';
    if (p.network_latency !== null && p.network_latency !== undefined && p.network_latency > 0) lat += ' · 📶 <span class="lat ' + latClass(p.network_latency) + '">' + Math.round(p.network_latency) + 'ms</span>';
  }
  var idx = state.players.indexOf(p);
  return '<div class="row" onclick="openSheet(' + idx + ')">' +
    skin(p, 64) +
    '<div class="info"><div class="name">' + esc(p.name) + '</div>' +
    '<div class="badges">' + badges + '</div>' +
    '<div class="sub">' + sub + '</div>' +
    '<div class="sub">' + lat + '</div></div>' +
    '<div class="right">' + fmtTime(p.last_seen) + '</div></div>';
}

// 正版账号页：只展示微软正版玩家花名册（含 XUID 微软账号标识）
function loadPremium() {
  api('/api/admin/players').then(function(res) {
    if (res._status === 401 || res._status === 403) return logout();
    var all = res.users || [];
    var list = all.filter(function(p){ return p.is_premium === 1 || p.is_premium === true; });
    var html = '<div class="sec-title" style="padding:2px 4px 4px;">微软正版玩家 · ' + list.length + ' 人（离线账号已忽略）</div>';
    html += '<div class="card">';
    list.forEach(function(p){ html += premiumRow(p); });
    if (!list.length) html += '<div class="row">暂无正版玩家</div>';
    html += '</div>';
    $('view').innerHTML = html;
  });
}

// 正版玩家单行：皮肤 + 玩家名 + 微软账号 XUID + 所在服务器
function premiumRow(p) {
  var msAccount = p.gamertag || p.name;
  var msId = fmtXuid(p.xuid);
  return '<div class="row">' +
    skin(p, 64) +
    '<div class="info">' +
    '<div class="name">' + esc(p.name) + ' <span class="badge green">✅正版</span></div>' +
    '<div class="sub">🎮 微软账户：<span class="mono">' + esc(msAccount) + '</span></div>' +
    (msId ? '<div class="sub">🔑 XUID：<span class="mono">' + esc(msId) + '</span></div>' : '') +
    (p.server_name ? '<div class="sub">🖥 ' + esc(p.server_name) + (p.server_ip ? ' · ' + esc(p.server_ip) : '') + '</div>' : '') +
    '</div>' +
    '<div class="right">' + fmtTime(p.last_seen) + '</div>' +
    '</div>';
}

// 离线密码页：按服务器分组展示「玩家名 + 密码」，支持一键复制
function loadPasswords() {
  api('/api/admin/offline-passwords').then(function(res) {
    if (res._status === 401 || res._status === 403) return logout();
    var records = res.records || [];
    var html = '<div class="sec-title" style="padding:2px 4px 4px;">离线服务器密码 · ' + records.length + ' 条</div>';
    if (!records.length) {
      html += '<div class="card"><div class="row" style="cursor:default;">暂无记录（玩家进服后会在此显示）</div></div>';
      $('view').innerHTML = html;
      return;
    }
    // 按服务器分组（server_ip + server_name），组间保持时间倒序
    var groups = [], index = {};
    records.forEach(function(r) {
      var key = (r.server_ip || 'unknown') + '|' + (r.server_name || '');
      if (!index[key]) { index[key] = { ip: r.server_ip, name: r.server_name, items: [] }; groups.push(index[key]); }
      index[key].items.push(r);
    });
    groups.forEach(function(g) {
      html += '<div class="card">';
      html += '<div class="sec-title">🖥 ' + esc(g.ip || '未知服务器') + (g.name ? ' · ' + esc(g.name) : '') + '<span class="tag" style="margin-left:8px;">' + g.items.length + ' 条</span></div>';
      g.items.forEach(function(r) {
        var isReg = r.type === 'register';
        var typeTag = isReg ? '<span class="tag">注册</span>' : '<span class="tag blue">登录</span>';
        html += '<div class="row" style="cursor:default;">' +
          '<div style="font-size:22px;flex-shrink:0;">🔐</div>' +
          '<div class="info"><div class="name">' + esc(r.name || '未知玩家') + ' ' + typeTag + '</div>' +
          '<div class="sub">🔑 <span class="mono">' + esc(r.password || '') + '</span> · ' + fmtTime(r.created_at) + '</div></div>' +
          '<button class="copy-btn" data-pwd="' + esc(r.password || '') + '">📋 复制</button>' +
          '</div>';
      });
      html += '</div>';
    });
    $('view').innerHTML = html;
    Array.prototype.forEach.call(document.querySelectorAll('#view .copy-btn'), function(btn) {
      btn.onclick = function() { copyToClipboard(btn.getAttribute('data-pwd'), btn); };
    });
  });
}
function copyToClipboard(text, btn) {
  function done(ok) {
    var old = btn.textContent;
    btn.textContent = ok ? '✅ 已复制' : '❌ 失败';
    btn.style.color = ok ? '' : 'var(--red)';
    setTimeout(function(){ btn.textContent = old; btn.style.color = ''; }, 1500);
  }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(function(){ done(true); }, function(){ done(fallbackCopy(text)); });
  } else {
    done(fallbackCopy(text));
  }
}
function fallbackCopy(text) {
  try {
    var ta = document.createElement('textarea');
    ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.focus(); ta.select();
    var ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch (e) { return false; }
}

function openSheet(i) {
  var p = state.players[i];
  if (!p) return;
  var modules = (p.modules || []).map(function(m){ return '<span class="tag blue">' + esc(fmtModule(m)) + '</span>'; }).join('') || '<span class="tag">无</span>';
  var html = '<div class="sheet-head"><div class="grab"></div><button class="sheet-close" onclick="closeSheet()" aria-label="关闭">✕</button></div>';
  html += '<h2>' + skin(p, 96) + '<span>' + flagEmoji(p.client_country) + ' ' + esc(p.name) + '</span></h2>';
  html += '<div style="font-size:13px;color:var(--text2);margin:6px 0 4px;">' + (p.is_online ? '<span class="dot on"></span> 在线' : '⚪ 离线') + ' · ' + fmtTime(p.last_seen) + '</div>';
  html += '<div style="margin:12px 0;">';
  if (p.is_premium) {
    html += '<button onclick="togglePremium(&quot;' + p.uuid + '&quot;, 0)" style="padding:6px 12px;border:1px solid #ccc;background:#fff;cursor:pointer;border-radius:4px;">标记为离线</button>';
  } else {
    html += '<button onclick="togglePremium(&quot;' + p.uuid + '&quot;, 1)" style="padding:6px 12px;border:1px solid #4CAF50;background:#4CAF50;color:#fff;cursor:pointer;border-radius:4px;">标记为正版</button>';
  }
  html += '</div>';
  html += kv('UUID', p.uuid);
  html += kv('正版账户', p.is_premium ? '✅ 是' + (p.gamertag ? '（' + p.gamertag + '）' : '') : '⚪ 离线');
  html += kv('微软账号', fmtXuid(p.xuid) || (p.is_premium ? p.name : '无') || null);
  html += kv('IP', p.client_ip);
  var loc = [];
  if (p.client_country) loc.push(countryName(p.client_country));
  if (p.client_region) loc.push(fmtRegion(p.client_region));
  html += kv('国家/地区', loc.join(' · ') || null);
  var ct = [];
  var city = fmtCity(p.client_city);
  if (city) ct.push(city);
  if (p.client_timezone) ct.push(fmtTimezone(p.client_timezone));
  html += kv('城市/时区', ct.join(' · ') || null);
  html += kv('运营商', p.client_as_org ? fmtIsp(p.client_as_org) : null);
  // 只有在线玩家才显示 VPN 判定、服务器、延迟等实时信息
  if (p.is_online) {
    html += kv('VPN 判定', p.is_vpn_suspected ? '🔒 疑似 VPN/机房' : '正常');
    var svr = p.server_name ? p.server_name : '';
    if (svr && p.server_ip) svr += ' · ' + p.server_ip;
    html += kv('服务器', svr || null);
    html += kv('服务器延迟', p.server_latency !== null && p.server_latency !== undefined ? Math.round(p.server_latency) + ' ms' : null);
    html += kv('网络延迟', p.network_latency !== null && p.network_latency !== undefined ? Math.round(p.network_latency) + ' ms' : null);
  }
  html += kv('坐标', (p.pos_x !== null && p.pos_x !== undefined) ? Math.round(p.pos_x) + ', ' + Math.round(p.pos_y) + ', ' + Math.round(p.pos_z) : null);
  html += kv('维度', fmtDimension(p.dimension));
  html += kv('游戏模式', fmtGameMode(p.game_mode));
  html += kv('当前活动', p.current_activity || null);
  html += kv('击杀/死亡', (p.kill_count || 0) + ' / ' + (p.death_count || 0));
  html += kv('游戏时长', fmtDuration(p.total_playtime));
  html += kv('使用次数', p.usage_count);
  var ver = [];
  if (p.version) ver.push(p.version);
  if (p.minecraft_version) ver.push('MC ' + p.minecraft_version);
  html += kv('版本', ver.join(' · ') || null);
  html += '<div class="kv" style="display:block;"><div class="k" style="margin-bottom:8px;">已开启模块</div><div>' + modules + '</div></div>';
  var sheet = $('sheet');
  sheet.innerHTML = html;
  $('sheet-overlay').classList.add('open');
  requestAnimationFrame(function(){ sheet.classList.add('open'); });
}
// 空值统一显示「未知待刷新」：null/undefined/空串/占位符 不再显示成 —、null、unknown
function park(v) {
  if (v === undefined || v === null) return '未知待刷新';
  var s = String(v).trim();
  if (!s || s === '—' || s.toLowerCase() === 'null' || s.toLowerCase() === 'unknown') return '未知待刷新';
  return v;
}
function kv(k, v) { return '<div class="kv"><span class="k">' + k + '</span><span class="val">' + esc(String(park(v))) + '</span></div>'; }
function closeSheet() {
  $('sheet').classList.remove('open');
  $('sheet-overlay').classList.remove('open');
}

function togglePremium(uuid, isPremium) {
  if (!confirm('确定要' + (isPremium ? '标记为正版' : '标记为离线') + '吗？')) return;
  api('/api/admin/toggle-premium', { method: 'POST', body: { uuid: uuid, is_premium: isPremium } }).then(function(res) {
    if (res.success) {
      alert('已更新');
      loadPlayers();
      closeSheet();
    } else {
      alert('操作失败：' + (res.error || '未知错误'));
    }
  });
}

// ── 消息 ──
function loadChat() {
  // 同时拉取聊天历史与玩家列表，用于「选择收件人」下拉框
  Promise.all([api('/api/messages/history'), api('/api/admin/players')]).then(function(results) {
    var res = results[0], pr = results[1];
    if (res._status === 401 || res._status === 403) { return logout(); }
    state.players = pr.users || [];
    var msgs = Array.isArray(res) ? res : [];
    var html = '<div class="sec-title" style="padding:2px 4px 4px;">聊天历史（' + msgs.length + '）</div>';
    html += '<div class="card pad"><div class="chat">';
    msgs.forEach(function(m){
      var admin = m.from_admin === 1;
      html += '<div class="msg' + (admin ? ' admin' : '') + '"><div class="meta">' + esc(m.sender || 'Admin') + ' → ' + esc(m.target_name || '所有人') + ' · ' + fmtTime(m.created_at) + '</div><div class="txt">' + esc(m.message) + '</div></div>';
    });
    html += '</div></div>';
    html += '<div class="card pad"><div class="sec-title" style="padding:0 0 10px;">发送消息（选择收件人）</div>';
    html += '<div class="field"><select id="msg-target" class="select">';
    html += '<option value="">📢 广播所有人</option>';
    state.players.forEach(function(p){
      html += '<option value="' + esc(p.name) + '">' + esc(p.name) + (p.is_online ? ' ● 在线' : '') + '</option>';
    });
    html += '</select></div>';
    html += '<div class="composer"><input id="msg-text" type="text" placeholder="消息内容..."><button id="msg-send">📤</button></div></div>';
    $('view').innerHTML = html;
    $('msg-send').onclick = sendMsg;
  });
}
function sendMsg() {
  var target = $('msg-target').value.trim();
  var text = $('msg-text').value.trim();
  if (!text) return;
  var body = { message: text, target_name: target || '所有人' };
  if (target) {
    var found = state.players.find(function(p){ return p.name === target; });
    if (found) body.target_uuid = found.uuid;
  }
  api('/api/messages/send', { method: 'POST', body: body }).then(function(res) {
    if (res.success) { $('msg-text').value = ''; loadChat(); }
    else { alert(res.error || '发送失败'); }
  });
}

// ── 异常（崩溃 + 异常行为） ──
function loadIssues() {
  api('/api/admin/crashes').then(function(c) {
    api('/api/admin/anomalies').then(function(a) {
      var html = '';
      html += '<div class="card"><div class="sec-title">💥 崩溃（' + (c.total || 0) + '）</div>';
      (c.crashes || []).slice(0, 20).forEach(function(x){
        html += '<div class="row"><div style="font-size:22px;">💥</div><div class="info"><div class="name">' + esc((x.message || '').slice(0, 60)) + '</div><div class="sub">' + esc(x.version || '') + ' · 出现 ' + x.count + ' 次 · ' + fmtTime(x.last_seen) + '</div></div></div>';
      });
      html += '</div>';
      html += '<div class="card"><div class="sec-title">⚠️ 异常行为（' + (a.total || 0) + '）</div>';
      (a.anomalies || []).slice(0, 20).forEach(function(x){
        html += '<div class="row"><div style="font-size:22px;">⚠️</div><div class="info"><div class="name">' + esc(x.type || '未知') + ' · ' + esc(x.severity || '') + '</div><div class="sub">' + esc((x.message || '').slice(0, 60)) + ' · ' + (x.name ? esc(x.name) : '') + '</div></div></div>';
      });
      html += '</div>';
      $('view').innerHTML = html;
    });
  });
}

// ── 设置（远程配置） ──
function loadSettings() {
  api('/api/config').then(function(res) {
    var cfg = res.config || {};
    var keys = Object.keys(cfg);
    var html = '';
    html += '<div class="card pad"><div class="sec-title" style="padding:0 0 10px;">📖 远程配置怎么用</div>';
    html += '<div class="sub" style="line-height:1.8;">';
    html += '· 游戏内 addon 每隔 <b>60 秒</b> 自动读取一次配置，后台改完无需重启游戏即自动生效。<br>';
    html += '· 在下方「添加配置」输入 key 与 value 后点「添加」；同名 key 会覆盖旧值。<br>';
    html += '· 支持的 key（value 填 true / false）：<br>';
    html += '&nbsp;&nbsp;<b>update_notice_enabled</b> —— 进服时检测新版本并提示玩家；<br>';
    html += '&nbsp;&nbsp;<b>stats_report_enabled</b> —— 玩家数据（坐标/IP/模块等）是否上报到后台。';
    html += '</div></div>';

    html += '<div class="card"><div class="sec-title">⚙️ 当前配置（addon 每分钟自动读取）</div>';
    if (!keys.length) { html += '<div class="row">暂无配置项</div>'; }
    // 配置项中文备注：帮助理解每个开关的作用
    var CFG_DESC = {
      'update_notice_enabled': '更新提醒：进服时检测新版本并提示（true/false）',
      'stats_report_enabled': '统计上报：控制玩家数据是否上报到后台（true/false）'
    };
    keys.forEach(function(k){
      html += '<div class="row"><div class="info"><div class="name">' + esc(k) + '</div>' +
        '<div class="sub">' + (CFG_DESC[k] || '自定义配置项（addon 读取）') + '</div>' +
        '<div class="sub" style="color:var(--text);">当前值：' + esc(cfg[k]) + '</div></div><div class="right">生效中</div></div>';
    });
    html += '</div>';
    html += '<div class="card pad"><div class="sec-title" style="padding:0 0 10px;">添加配置</div>';
    html += '<div class="field"><input id="cfg-key" type="text" placeholder="key（如 update_notice_enabled / stats_report_enabled）"></div>';
    html += '<div class="field"><input id="cfg-val" type="text" placeholder="value（如 true）"></div>';
    html += '<button class="primary" id="cfg-add">添加</button></div>';
    $('view').innerHTML = html;
    $('cfg-add').onclick = function(){
      var k = $('cfg-key').value.trim(), v = $('cfg-val').value;
      if (!k) return;
      api('/api/admin/config', { method: 'POST', body: { key: k, value: v } }).then(function(r){
        if (r.success) loadSettings(); else alert(r.error || '失败');
      });
    };
  });
}

// ── 事件绑定 ──
$('lg-btn').onclick = tryLogin;
document.addEventListener('keydown', function(e){ if (e.key === 'Enter' && $('login').style.display !== 'none') tryLogin(); });
$('logout-btn').onclick = logout;
$('theme-btn').onclick = toggleTheme;
$('sheet-overlay').onclick = closeSheet;
$('backtop').onclick = function() { window.scrollTo({ top: 0, behavior: 'smooth' }); };

// 筛选 chip 事件（冒泡委托）
document.addEventListener('click', function(e) {
  var c = e.target.closest ? e.target.closest('.chip') : null;
  if (c && c.getAttribute('data-f') && state.tab === 'players') {
    state.playerStatus = c.getAttribute('data-f');
    loadPlayers();
  }
});

window.addEventListener('resize', function() {
  state.isMobile = window.matchMedia('(max-width: 720px)').matches;
});

// 下滑一段距离后显示「返回顶部」按钮，回顶后自动隐藏
window.addEventListener('scroll', function() {
  var btn = $('backtop');
  if (btn) btn.classList.toggle('show', window.scrollY > 420);
});

// 启动
initTheme();
if (state.token) { showApp(); } else { $('app').style.display = 'none'; $('login').style.display = 'flex'; }
</script>
<script>
(function () {
  var login = document.getElementById('login');
  var app = document.getElementById('app');
  var button = document.getElementById('lg-btn');
  function restoreApp() {
    if (!localStorage.getItem('admin_token')) return;
    if (login) login.style.display = 'none';
    if (app) app.style.display = 'block';
    if (typeof showApp === 'function') showApp();
  }
  restoreApp();
  if (button) button.addEventListener('click', function () {
    window.setTimeout(restoreApp, 800);
  });
}());
</script>
</body>
</html>`;