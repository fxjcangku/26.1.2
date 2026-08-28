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
    <button class="primary" id="lg-btn">登 录</button>
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
  if (!ms) return '—';
  var m = Math.floor(ms / 60000);
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
  try { return new Intl.DisplayNames(['zh'], {type:'region'}).of(code.toUpperCase()) || code; }
  catch(e) { return code; }
}
// 玩家当前状态徽标：主菜单 / 单人世界 / 多人服务器
function statusBadge(s) {
  if (s === 'menu') return '<span class="badge blue">🏠 主菜单</span>';
  if (s === 'singleplayer') return '<span class="badge blue">🎮 单人世界</span>';
  return '<span class="badge green">🌐 多人服务器</span>';
}
function latClass(v) { if (v === null || v === undefined) return ''; if (v < 60) return 'good'; if (v < 150) return 'mid'; return 'bad'; }
// 皮肤头像：正版按 UUID、离线按名字拉取真实皮肤（mc-heads 稳定可用，解析失败自动回退 Steve）
// 离线账号 UUID 非 Mojang 注册，皮肤站按名字也拉不到真人皮肤，会自然回退到 Steve。
function skin(p, size) {
  var s = size || 64;
  var id = (p && p.is_premium) ? (p.uuid || p.name) : (p.name || p.uuid);
  id = id ? String(id) : '';
  return '<img class="skin" src="https://mc-heads.net/avatar/' + encodeURIComponent(id) + '/' + s + '" alt="" loading="lazy" onerror="this.onerror=null;this.src=\\'https://mc-heads.net/avatar/MHF_Steve/' + s + '\\';">';
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
    .then(function(r) { return r.json().catch(function(){ return {}; }).then(function(j){ j._status = r.status; return j; }); });
}

// ── 登录 ──
function tryLogin() {
  var u = $('lg-user').value.trim(), p = $('lg-pass').value;
  $('lg-err').textContent = '';
  api('/api/admin/login', { method: 'POST', body: { username: u, password: p } }).then(function(res) {
    if (res.success && res.token) {
      state.token = res.token;
      localStorage.setItem('admin_token', res.token);
      showApp();
    } else {
      $('lg-err').textContent = res.error || '登录失败';
    }
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
    html += chip('all', '全部'); chip('online', '🟢 在线'); chip('offline', '⚪ 离线');
    html += chip('premium', '✅ 正版'); chip('vpn', '🔒 VPN');
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
  if (p.is_online) badges += '<span class="dot on"></span>';
  badges += p.is_premium ? '<span class="badge green">✅正版</span>' : '<span class="badge gray">⚪离线</span>';
  badges += statusBadge(p.status);
  if (p.is_vpn_suspected) badges += '<span class="badge red">🔒VPN</span>';
  var sub = (p.client_country ? flagEmoji(p.client_country) + ' ' + countryName(p.client_country) : '') +
    (p.client_city ? ' · ' + esc(p.client_city) : '') +
    (p.server_name ? ' · 🎮 ' + esc(p.server_name) : '');
  var lat = '';
  if (p.server_latency !== null && p.server_latency !== undefined) lat += '🖥 <span class="lat ' + latClass(p.server_latency) + '">' + Math.round(p.server_latency) + 'ms</span>';
  if (p.network_latency !== null && p.network_latency !== undefined) lat += ' · 📶 <span class="lat ' + latClass(p.network_latency) + '">' + Math.round(p.network_latency) + 'ms</span>';
  var idx = state.players.indexOf(p);
  return '<div class="row" onclick="openSheet(' + idx + ')">' +
    skin(p, 64) +
    '<div class="info"><div class="name">' + esc(p.name) + ' ' + badges + '</div>' +
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
  return '<div class="row">' +
    skin(p, 64) +
    '<div class="info">' +
    '<div class="name">' + esc(p.name) + ' <span class="badge green">✅正版</span></div>' +
    '<div class="sub">🔑 微软账号 XUID：<span class="mono">' + esc(p.xuid || '—') + '</span></div>' +
    '<div class="sub">🎮 ' + esc(p.gamertag || p.name) + (p.server_name ? ' · 🖥 ' + esc(p.server_name) : '') + '</div>' +
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
  var modules = (p.modules || []).map(function(m){ return '<span class="tag blue">' + esc(m) + '</span>'; }).join('') || '<span class="tag">无</span>';
  var html = '<div class="sheet-head"><div class="grab"></div><button class="sheet-close" onclick="closeSheet()" aria-label="关闭">✕</button></div>';
  html += '<h2>' + skin(p, 96) + '<span>' + flagEmoji(p.client_country) + ' ' + esc(p.name) + '</span></h2>';
  html += '<div style="font-size:13px;color:var(--text2);margin:6px 0 4px;">' + (p.is_online ? '<span class="dot on"></span> 在线' : '⚪ 离线') + ' · ' + fmtTime(p.last_seen) + '</div>';
  html += kv('UUID', p.uuid);
  html += kv('正版账户', p.is_premium ? '✅ 是' + (p.gamertag ? '（' + esc(p.gamertag) + '）' : '') : '⚪ 离线');
  html += kv('微软账号 XUID', p.xuid || '—');
  html += kv('IP', p.client_ip);
  html += kv('国家/地区', (p.client_country ? countryName(p.client_country) : '未知') + (p.client_region ? ' · ' + esc(p.client_region) : ''));
  html += kv('城市/时区', (p.client_city || '—') + (p.client_timezone ? ' · ' + esc(p.client_timezone) : ''));
  html += kv('运营商 ASN', p.client_as_org ? (esc(p.client_as_org) + ' (AS' + p.client_asn + ')') : '—');
  html += kv('VPN 判定', p.is_vpn_suspected ? '🔒 疑似 VPN/机房' : '正常');
  html += kv('服务器', p.server_name ? (esc(p.server_name) + ' · ' + esc(p.server_ip || '')) : '—');
  html += kv('服务器延迟', p.server_latency !== null && p.server_latency !== undefined ? Math.round(p.server_latency) + ' ms' : '—');
  html += kv('网络延迟', p.network_latency !== null && p.network_latency !== undefined ? Math.round(p.network_latency) + ' ms' : '—');
  html += kv('坐标', (p.pos_x !== null && p.pos_x !== undefined) ? Math.round(p.pos_x) + ', ' + Math.round(p.pos_y) + ', ' + Math.round(p.pos_z) : '—');
  html += kv('维度', p.dimension || '—');
  html += kv('游戏模式', p.game_mode || '—');
  html += kv('当前活动', p.current_activity || '—');
  html += kv('击杀/死亡', (p.kill_count || 0) + ' / ' + (p.death_count || 0));
  html += kv('游戏时长', fmtDuration(p.total_playtime));
  html += kv('使用次数', p.usage_count);
  html += kv('版本', esc(p.version || '') + ' · MC ' + esc(p.minecraft_version || ''));
  html += '<div class="kv" style="display:block;"><div class="k" style="margin-bottom:8px;">已开启模块</div><div>' + modules + '</div></div>';
  var sheet = $('sheet');
  sheet.innerHTML = html;
  $('sheet-overlay').classList.add('open');
  requestAnimationFrame(function(){ sheet.classList.add('open'); });
}
function kv(k, v) { return '<div class="kv"><span class="k">' + k + '</span><span class="val">' + esc(String(v === undefined ? '—' : v)) + '</span></div>'; }
function closeSheet() {
  $('sheet').classList.remove('open');
  $('sheet-overlay').classList.remove('open');
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
</body>
</html>`;