var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// admin-html.js
var ADMIN_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<meta name="color-scheme" content="light dark">
<title>yiyiaddon \u63A7\u5236\u53F0</title>
<style>
/* \u2500\u2500 \u4E3B\u9898\u53D8\u91CF\uFF08\u6D45\u8272\u9ED8\u8BA4\uFF0C\u6DF1\u8272\u7528 [data-theme="dark"]\uFF09 \u2500\u2500 */
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

/* \u2500\u2500 \u52A8\u6001\u6E10\u53D8\u80CC\u666F\uFF08\u6BDB\u73BB\u7483\u900F\u89C6\u7684\u5185\u5BB9\uFF09
   \u53BB\u6389 filter:blur \u4E0E\u6301\u7EED\u52A8\u753B\uFF0C\u907F\u514D\u6574\u5C4F\u5927\u80CC\u666F\u6BCF\u5E27\u91CD\u7ED8\u5BFC\u81F4\u4E0B\u6ED1\u5361\u987F\uFF1B
   radial-gradient \u672C\u8EAB\u8DB3\u591F\u67D4\u548C\uFF0C\u89C6\u89C9\u901A\u900F\u611F\u4E0D\u53D8\u3002 */
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

/* \u2500\u2500 \u901A\u7528\u8FC7\u6E21 \u2500\u2500 */
.card, .seg, .icon-btn, .primary, .sheet, .kpi, .chip, .tabbar button {
  transition: transform .22s cubic-bezier(.32,.72,.33,1), background .25s ease,
    box-shadow .25s ease, color .25s ease, border-color .25s ease, opacity .2s ease;
}

/* \u2500\u2500 \u767B\u5F55 \u2500\u2500 */
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
/* \u4E0B\u62C9\u9009\u62E9\u5668\uFF1A\u4E0E\u8F93\u5165\u6846\u7EDF\u4E00\u6837\u5F0F\uFF08\u7528\u4E8E\u804A\u5929\u6536\u4EF6\u4EBA\u9009\u62E9\uFF09 */
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

/* \u2500\u2500 \u5E94\u7528\u5916\u58F3 \u2500\u2500 */
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

/* \u2500\u2500 \u6BDB\u73BB\u7483\u5361\u7247\uFF08iOS \u73BB\u7483\uFF09
   \u6EDA\u52A8\u533A\u5361\u7247\u53BB\u6389 backdrop-filter \u4E0E\u5165\u573A\u52A8\u753B\uFF0C\u6539\u534A\u900F\u660E\u7EAF\u8272\uFF0C
   \u907F\u514D\u5927\u91CF blur \u53E0\u52A0\u4E0E\u91CD\u590D\u52A8\u753B\u5BFC\u81F4\u4E0B\u6ED1\u5361\u987F\u3002 */
.card {
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: 22px; box-shadow: var(--shadow); margin-bottom: 18px; overflow: hidden;
}
.sec-title { font-size: 13px; color: var(--text2); text-transform: uppercase; letter-spacing: .5px; padding: 16px 18px 10px; font-weight: 600; }

/* \u2500\u2500 KPI \u2500\u2500 */
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.kpi { padding: 16px; border-radius: 20px; background: var(--glass); border: 1px solid var(--glass-border); box-shadow: var(--shadow); }
.kpi:hover { transform: translateY(-2px); }
.kpi .icon { font-size: 22px; }
.kpi .num { font-size: 28px; font-weight: 700; margin: 6px 0 2px; letter-spacing: -.5px; }
.kpi .lbl { font-size: 13px; color: var(--text2); }

/* \u2500\u2500 \u5217\u8868\u884C \u2500\u2500 */
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

/* \u2500\u2500 \u7B5B\u9009 chip \u2500\u2500 */
.toolbar { display: flex; gap: 8px; align-items: center; padding: 2px 18px 14px; flex-wrap: wrap; }
.toolbar .search {
  flex: 1; min-width: 140px; height: 40px; border-radius: 12px; border: 1px solid var(--glass-border);
  background: var(--fill); color: var(--text); padding: 0 14px; font-size: 15px; outline: none;
}
.toolbar .search:focus { border-color: var(--blue); }
.chip { height: 34px; padding: 0 14px; border-radius: 999px; border: 1px solid var(--glass-border); background: var(--glass); color: var(--text2); font-size: 13px; font-weight: 600; cursor: pointer; }
.chip.on { background: var(--blue); border-color: var(--blue); color: #fff; }
.chip:active { transform: scale(.94); }

/* \u2500\u2500 \u56FE\u8868 \u2500\u2500 */
.bars { display: flex; align-items: flex-end; gap: 8px; height: 130px; padding: 14px 16px 6px; }
.bar { flex: 1; background: linear-gradient(180deg, var(--blue), rgba(0,122,255,.35)); border-radius: 6px 6px 0 0; min-height: 2px; }
.bar-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 5px; }
.bar-wrap .v { font-size: 10px; color: var(--text2); }
.bar-wrap .d { font-size: 11px; color: var(--text2); white-space: nowrap; text-align: center; }

/* \u2500\u2500 \u5E95\u90E8 Tab \u680F\uFF08\u79FB\u52A8\u7AEF\uFF09 \u2500\u2500 */
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

/* \u2500\u2500 \u8FD4\u56DE\u9876\u90E8\u6309\u94AE\uFF08\u4E0B\u6ED1\u540E\u51FA\u73B0\uFF0CiOS \u6BDB\u73BB\u7483\u5706\u5F62\u6309\u94AE\uFF09 \u2500\u2500 */
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

/* \u2500\u2500 \u5E95\u90E8\u5F39\u7A97\uFF08bottom sheet\uFF09 \u2500\u2500 */
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

/* \u2500\u2500 \u6D88\u606F \u2500\u2500 */
.chat { max-height: 460px; overflow-y: auto; }
.chat .msg { margin: 0 0 10px; padding: 10px 14px; border-radius: 18px; background: var(--fill); max-width: 82%; }
.chat .msg.admin { background: rgba(0,122,255,0.18); margin-left: auto; }
.chat .msg .meta { font-size: 11px; color: var(--text2); margin-bottom: 3px; }
.chat .msg .txt { font-size: 15px; }
.composer { display: flex; gap: 8px; margin-top: 12px; }
.composer input { flex: 1; height: 46px; border-radius: 12px; border: 1px solid var(--glass-border); background: var(--fill); color: var(--text); padding: 0 14px; font-size: 15px; outline: none; }
.composer button { min-width: 46px; height: 46px; border-radius: 12px; border: none; background: var(--blue); color: #fff; font-size: 18px; cursor: pointer; }

.pad { padding: 16px 18px; }

/* \u54CD\u5E94\u5F0F\uFF1A\u624B\u673A / \u7535\u8111\u81EA\u52A8\u8C03\u6574\u6309\u94AE\u4F4D\u7F6E */
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
    <p>\u540E\u53F0\u7BA1\u7406\u63A7\u5236\u53F0</p>
    <div class="field"><input id="lg-user" type="text" placeholder="\u7528\u6237\u540D" autocomplete="username"></div>
    <div class="field"><input id="lg-pass" type="password" placeholder="\u5BC6\u7801" autocomplete="current-password"></div>
    <button class="primary" id="lg-btn" type="button" onclick="(function(b){var u=document.getElementById('lg-user').value.trim(),p=document.getElementById('lg-pass').value,e=document.getElementById('lg-err');e.textContent='\u767B\u5F55\u4E2D\u2026';b.disabled=true;fetch('/api/admin/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:u,password:p})}).then(function(r){return r.json();}).then(function(x){if(x.success&&x.token){localStorage.setItem('admin_token',x.token);location.reload();}else{e.textContent=x.error||'\u767B\u5F55\u5931\u8D25';b.disabled=false;}}).catch(function(){e.textContent='\u7F51\u7EDC\u9519\u8BEF\uFF1A\u65E0\u6CD5\u8FDE\u63A5\u540E\u53F0';b.disabled=false;});})(this); return false;">\u767B \u5F55</button>
    <div class="err" id="lg-err"></div>
  </div>
</div>

<div id="app">
  <nav class="topbar">
    <div class="title">\u{1F34B} yiyiaddon</div>
    <button class="icon-btn" id="theme-btn" title="\u5207\u6362\u4E3B\u9898">\u{1F319}</button>
    <button class="icon-btn" id="logout-btn" title="\u9000\u51FA">\u{1F6AA}</button>
  </nav>

  <div class="segmented" id="segmented"></div>
  <div class="container" id="view"></div>

  <div class="tabbar" id="tabbar"></div>
  <button class="backtop" id="backtop" title="\u56DE\u5230\u9876\u90E8" aria-label="\u56DE\u5230\u9876\u90E8">\u2B06\uFE0F</button>
</div>

<div class="sheet-overlay" id="sheet-overlay"></div>
<div class="sheet" id="sheet"></div>

<script>
var API_BASE = 'https://yiyiaddon.asia';
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
  { id: 'overview', e: '\u{1F4CA}', label: '\u6982\u89C8' },
  { id: 'players', e: '\u{1F465}', label: '\u73A9\u5BB6' },
  { id: 'premium', e: '\u{1F511}', label: '\u6B63\u7248\u8D26\u53F7' },
  { id: 'passwords', e: '\u{1F510}', label: '\u79BB\u7EBF\u5BC6\u7801' },
  { id: 'chat', e: '\u{1F4AC}', label: '\u6D88\u606F' },
  { id: 'issues', e: '\u26A0\uFE0F', label: '\u5F02\u5E38' },
  { id: 'settings', e: '\u2699\uFE0F', label: '\u8BBE\u7F6E' }
];

// \u2500\u2500 \u5DE5\u5177\u51FD\u6570 \u2500\u2500
function $(id) { return document.getElementById(id); }
function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function fmtTime(ts) {
  if (!ts) return '\u2014';
  var d = new Date(ts);
  var now = Date.now();
  var diff = now - ts;
  if (diff < 60000) return '\u521A\u521A';
  if (diff < 3600000) return Math.floor(diff / 60000) + ' \u5206\u949F\u524D';
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' \u5C0F\u65F6\u524D';
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'});
}
function fmtDuration(ms) {
  if (ms === null || ms === undefined) return '';
  var m = Math.floor(ms / 60000);
  if (m < 1) return '0 \u5206\u949F';
  if (m < 60) return m + ' \u5206\u949F';
  return Math.floor(m / 60) + ' \u5C0F\u65F6 ' + (m % 60) + ' \u5206';
}
function flagEmoji(code) {
  if (!code || code.length !== 2) return '\u{1F310}';
  code = code.toUpperCase();
  return String.fromCodePoint(0x1F1E6 + code.charCodeAt(0) - 65, 0x1F1E6 + code.charCodeAt(1) - 65);
}
function countryName(code) {
  if (!code) return '\u672A\u77E5';
  var upper = code.toUpperCase();
  // \u4F18\u5148\u7528\u6D4F\u89C8\u5668\u81EA\u52A8\u7FFB\u8BD1
  try { 
    var translated = new Intl.DisplayNames(['zh-CN'], {type:'region'}).of(upper);
    if (translated && translated !== upper) return translated;
  } catch(e) {}
  // \u5907\u7528\u624B\u52A8\u7FFB\u8BD1\uFF08\u515C\u5E95\uFF09
  var manual = {
    'CN':'\u4E2D\u56FD', 'US':'\u7F8E\u56FD', 'JP':'\u65E5\u672C', 'KR':'\u97E9\u56FD', 'TW':'\u53F0\u6E7E', 'HK':'\u9999\u6E2F', 'MO':'\u6FB3\u95E8',
    'SG':'\u65B0\u52A0\u5761', 'GB':'\u82F1\u56FD', 'DE':'\u5FB7\u56FD', 'FR':'\u6CD5\u56FD', 'CA':'\u52A0\u62FF\u5927', 'AU':'\u6FB3\u5927\u5229\u4E9A',
    'RU':'\u4FC4\u7F57\u65AF', 'IN':'\u5370\u5EA6', 'BR':'\u5DF4\u897F', 'MX':'\u58A8\u897F\u54E5', 'ES':'\u897F\u73ED\u7259', 'IT':'\u610F\u5927\u5229',
    'NL':'\u8377\u5170', 'SE':'\u745E\u5178', 'CH':'\u745E\u58EB', 'TH':'\u6CF0\u56FD', 'VN':'\u8D8A\u5357', 'MY':'\u9A6C\u6765\u897F\u4E9A',
    'ID':'\u5370\u5EA6\u5C3C\u897F\u4E9A', 'PH':'\u83F2\u5F8B\u5BBE', 'PL':'\u6CE2\u5170', 'TR':'\u571F\u8033\u5176', 'AR':'\u963F\u6839\u5EF7'
  };
  return manual[upper] || upper;
}
// \u5E38\u89C1\u57CE\u5E02 -> \u4E2D\u6587\uFF08VPN \u51FA\u53E3\u9AD8\u9891\u57CE\u5E02\u4F18\u5148\uFF09
var CITY_ZH = { "Los Angeles":"\u6D1B\u6749\u77F6","New York":"\u7EBD\u7EA6","San Francisco":"\u65E7\u91D1\u5C71","San Jose":"\u5723\u4F55\u585E","Seattle":"\u897F\u96C5\u56FE","Chicago":"\u829D\u52A0\u54E5","Dallas":"\u8FBE\u62C9\u65AF","Houston":"\u4F11\u65AF\u987F","Atlanta":"\u4E9A\u7279\u5170\u5927","Miami":"\u8FC8\u963F\u5BC6","Phoenix":"\u51E4\u51F0\u57CE","Denver":"\u4E39\u4F5B","Ashburn":"\u963F\u4EC0\u672C","Buffalo":"\u5E03\u6CD5\u7F57","Fremont":"\u5F17\u91CC\u8499\u7279","Santa Clara":"\u5723\u514B\u62C9\u62C9","Boardman":"\u535A\u5FB7\u66FC","London":"\u4F26\u6566","Frankfurt":"\u6CD5\u5170\u514B\u798F","Paris":"\u5DF4\u9ECE","Amsterdam":"\u963F\u59C6\u65AF\u7279\u4E39","Berlin":"\u67CF\u6797","Madrid":"\u9A6C\u5FB7\u91CC","Moscow":"\u83AB\u65AF\u79D1","Tokyo":"\u4E1C\u4EAC","Osaka":"\u5927\u962A","Seoul":"\u9996\u5C14","Singapore":"\u65B0\u52A0\u5761","Hong Kong":"\u9999\u6E2F","Taipei":"\u53F0\u5317","Sydney":"\u6089\u5C3C","Melbourne":"\u58A8\u5C14\u672C","Toronto":"\u591A\u4F26\u591A","Vancouver":"\u6E29\u54E5\u534E","Shanghai":"\u4E0A\u6D77","Beijing":"\u5317\u4EAC","Guangzhou":"\u5E7F\u5DDE","Shenzhen":"\u6DF1\u5733","Hangzhou":"\u676D\u5DDE","Chengdu":"\u6210\u90FD","Nanjing":"\u5357\u4EAC","Wuhan":"\u6B66\u6C49" };
// \u5E38\u89C1\u5730\u533A/\u5DDE/\u7701 -> \u4E2D\u6587
var REGION_ZH = { "California":"\u52A0\u5229\u798F\u5C3C\u4E9A\u5DDE","New York":"\u7EBD\u7EA6\u5DDE","Texas":"\u5F97\u514B\u8428\u65AF\u5DDE","Washington":"\u534E\u76DB\u987F\u5DDE","Virginia":"\u5F17\u5409\u5C3C\u4E9A\u5DDE","Illinois":"\u4F0A\u5229\u8BFA\u4F0A\u5DDE","Florida":"\u4F5B\u7F57\u91CC\u8FBE\u5DDE","Georgia":"\u4F50\u6CBB\u4E9A\u5DDE","Massachusetts":"\u9A6C\u8428\u8BF8\u585E\u5DDE","Pennsylvania":"\u5BBE\u5915\u6CD5\u5C3C\u4E9A\u5DDE","Ohio":"\u4FC4\u4EA5\u4FC4\u5DDE","Michigan":"\u5BC6\u6B47\u6839\u5DDE","Arizona":"\u4E9A\u5229\u6851\u90A3\u5DDE","Colorado":"\u79D1\u7F57\u62C9\u591A\u5DDE","Oregon":"\u4FC4\u52D2\u5188\u5DDE","Utah":"\u72B9\u4ED6\u5DDE","Nevada":"\u5185\u534E\u8FBE\u5DDE","New Jersey":"\u65B0\u6CFD\u897F\u5DDE","North Carolina":"\u5317\u5361\u7F57\u6765\u7EB3\u5DDE","Missouri":"\u5BC6\u82CF\u91CC\u5DDE","Ontario":"\u5B89\u5927\u7565\u7701","Quebec":"\u9B41\u5317\u514B\u7701","British Columbia":"\u4E0D\u5217\u98A0\u54E5\u4F26\u6BD4\u4E9A\u7701","England":"\u82F1\u683C\u5170","Hesse":"\u9ED1\u68EE\u5DDE","Hessen":"\u9ED1\u68EE\u5DDE","Bavaria":"\u5DF4\u4F10\u5229\u4E9A\u5DDE","North Rhine-Westphalia":"\u5317\u83B1\u8335-\u5A01\u65AF\u7279\u6CD5\u4F26\u5DDE","Tokyo":"\u4E1C\u4EAC\u90FD","Osaka":"\u5927\u962A\u5E9C","Seoul":"\u9996\u5C14","Guangdong":"\u5E7F\u4E1C\u7701","Zhejiang":"\u6D59\u6C5F\u7701","Beijing":"\u5317\u4EAC\u5E02","Shanghai":"\u4E0A\u6D77\u5E02","Jiangsu":"\u6C5F\u82CF\u7701","Sichuan":"\u56DB\u5DDD\u7701","Fujian":"\u798F\u5EFA\u7701","Shandong":"\u5C71\u4E1C\u7701" };
// \u5E38\u89C1\u8FD0\u8425\u5546/\u673A\u623F/\u4E91\u5382\u5546 -> \u4E2D\u6587\uFF08\u6570\u636E\u4E2D\u5FC3\u7C7B\u6807\u6CE8\u201C\u673A\u623F\u201D\uFF0C\u65B9\u4FBF\u8BC6\u522B\u4E3A\u68AF\u5B50\u51FA\u53E3\uFF09
var ISP_ZH = { "fdcservers":"FDC \u673A\u623F\uFF08\u6570\u636E\u4E2D\u5FC3\uFF09","m247":"M247 \u673A\u623F\uFF08\u6570\u636E\u4E2D\u5FC3\uFF09","choopa":"Choopa \u673A\u623F\uFF08\u6570\u636E\u4E2D\u5FC3\uFF09","colocrossing":"ColoCrossing \u673A\u623F","psychz":"Psychz \u673A\u623F","quadranet":"QuadraNet \u673A\u623F","ovh":"OVH \u673A\u623F","hetzner":"Hetzner \u673A\u623F","contabo":"Contabo \u673A\u623F","leaseweb":"LeaseWeb \u673A\u623F","digitalocean":"DigitalOcean \u4E91","linode":"Linode \u4E91","vultr":"Vultr \u4E91","cloudflare":"Cloudflare","amazon":"\u4E9A\u9A6C\u900A\u4E91\uFF08AWS\uFF09","google":"\u8C37\u6B4C\u4E91\uFF08GCP\uFF09","microsoft":"\u5FAE\u8F6F\u4E91\uFF08Azure\uFF09","oracle":"\u7532\u9AA8\u6587\u4E91\uFF08Oracle\uFF09","alibaba":"\u963F\u91CC\u4E91","aliyun":"\u963F\u91CC\u4E91","tencent":"\u817E\u8BAF\u4E91","huawei":"\u534E\u4E3A\u4E91","china telecom":"\u4E2D\u56FD\u7535\u4FE1","china unicom":"\u4E2D\u56FD\u8054\u901A","china mobile":"\u4E2D\u56FD\u79FB\u52A8","comcast":"\u5EB7\u5361\u65AF\u7279\uFF08Comcast\uFF09","verizon":"Verizon","deutsche telekom":"\u5FB7\u56FD\u7535\u4FE1","ntt":"NTT\uFF08\u65E5\u672C\uFF09","kddi":"KDDI\uFF08\u65E5\u672C\uFF09","cogent":"Cogent\uFF08\u9AA8\u5E72\u7F51\uFF09" };
function fmtCity(c) { return c ? (CITY_ZH[c] || c) : ''; }
function fmtRegion(r) { return r ? (REGION_ZH[r] || r) : ''; }
function fmtIsp(org) {
  if (!org) return '';
  // \u53BB\u6389\u5197\u4F59\u7684 AS \u7F16\u53F7\uFF08\u5982 (AS30058) / AS30058\uFF09\uFF0C\u53EA\u4FDD\u7559\u8FD0\u8425\u5546\u4E3B\u4F53\u66F4\u6613\u8BFB
  var cleaned = String(org).replace(/\\(?\\s*AS\\d+\\s*\\)?/gi, '').replace(/[\\s,;]+$/, '').trim();
  if (!cleaned) return String(org).trim();
  var key = cleaned.toLowerCase();
  for (var k in ISP_ZH) { if (key.indexOf(k) !== -1) return ISP_ZH[k]; }
  return cleaned;
}
// \u73A9\u5BB6\u5F53\u524D\u72B6\u6001\u5FBD\u6807\uFF1A\u4E3B\u83DC\u5355 / \u5355\u4EBA\u4E16\u754C / \u591A\u4EBA\u670D\u52A1\u5668
function statusBadge(s) {
  if (s === 'menu') return '<span class="badge blue">\u{1F3E0} \u4E3B\u83DC\u5355</span>';
  if (s === 'singleplayer') return '<span class="badge blue">\u{1F3AE} \u5355\u4EBA\u4E16\u754C</span>';
  return '<span class="badge green">\u{1F310} \u591A\u4EBA\u670D\u52A1\u5668</span>';
}
function latClass(v) { if (v === null || v === undefined) return ''; if (v < 60) return 'good'; if (v < 150) return 'mid'; return 'bad'; }
// XUID \u4EC5\u63A5\u53D7\u7EAF\u6570\u5B57\uFF1Bauthlib \u672A\u6CE8\u5165\u65F6\u8FD4\u56DE\u7684 auth_xuid \u5360\u4F4D\u7B26\u4E00\u5F8B\u6309\u7A7A\u5904\u7406\uFF0C\u907F\u514D\u663E\u793A\u5F02\u5E38\u5B57\u7B26\u4E32
function fmtXuid(x) {
  if (x == null) return null;
  var v = String(x).trim();
  return /^d{8,20}$/.test(v) ? v : null;
}
// Meteor \u6A21\u5757\u540D -> \u4E2D\u6587\u6620\u5C04\uFF08\u7531 zh_cn.json \u751F\u6210\uFF09
var MODULE_ZH = {"weather-changer":"\u5929\u6C14\u66F4\u6539","air-jump":"\u7A7A\u4E2D\u8DF3\u8DC3","auto-fish":"\u81EA\u52A8\u9493\u9C7C","name-protect":"\u540D\u79F0\u4FDD\u62A4","velocity":"\u53CD\u51FB\u9000","no-ghost-blocks":"\u9632\u5E7D\u7075\u65B9\u5757","bed-aura":"\u5E8A\u5149\u73AF","auto-jump":"\u81EA\u52A8\u8FDE\u8DF3","ambience":"\u73AF\u5883","better-tooltips":"\u66F4\u597D\u7684\u63D0\u793A\u6846","notifier":"\u901A\u77E5\u5668","item-physics":"\u7269\u54C1\u7269\u7406","air-place":"\u7A7A\u4E2D\u653E\u7F6E","enderman-look":"\u672B\u5F71\u4EBA\u6CE8\u89C6","excavator":"\u6316\u6398\u673A","timer":"\u5168\u5C40\u52A0\u901F","surround":"\u81EA\u6211\u5305\u56F4","no-rotate":"\u65E0\u65CB\u8F6C","long-jump":"\u8FDC\u8DF3","trajectories":"\u5F39\u9053\u9884\u6D4B","chams":"\u5B9E\u4F53\u6E32\u67D3","server-spoof":"\u670D\u52A1\u5668\u4F2A\u88C5","fullbright":"\u5168\u5C40\u4EAE\u5EA6","freecam":"\u7075\u9B42\u51FA\u7A8D","exp-thrower":"\u7ECF\u9A8C\u6295\u63B7\u5668","auto-totem":"\u81EA\u52A8\u56FE\u817E","book-bot":"\u4E66\u673A\u5668\u4EBA","auto-gap":"\u81EA\u52A8\u91D1\u82F9\u679C","packet-logger":"\u6570\u636E\u5305\u8BB0\u5F55\u5668","middle-click-extra":"\u4E2D\u952E\u589E\u5F3A","logout-spots":"\u767B\u51FA\u6807\u8BB0","nuker":"\u8303\u56F4\u7834\u574F","sound-blocker":"\u58F0\u97F3\u5C4F\u853D","vein-miner":"\u8FDE\u9501\u91C7\u96C6","auto-trap":"\u81EA\u52A8\u9677\u9631","wall-hack":"\u900F\u89C6","no-render":"\u7981\u6B62\u6E32\u67D3","hand-view":"\u624B\u90E8\u89C6\u89D2","instant-rebreak":"\u77AC\u95F4\u91CD\u65B0\u7834\u574F","liquid-interact":"\u6DB2\u4F53\u4EA4\u4E92","crystal-aura":"\u6C34\u6676\u5149\u73AF","spider":"\u8718\u86DB","auto-log":"\u81EA\u52A8\u4E0B\u7EBF","high-jump":"\u9AD8\u8DF3","speed":"\u901F\u5EA6","offhand":"\u526F\u624B","arrow-dodge":"\u7BAD\u77E2\u95EA\u907F","multitask":"\u591A\u4EFB\u52A1","auto-replenish":"\u81EA\u52A8\u8865\u5145","break-indicators":"\u7834\u574F\u6307\u793A\u5668","potion-saver":"\u836F\u6C34\u8282\u7EA6\u5668","block-esp":"\u65B9\u5757\u900F\u89C6","packet-mine":"\u6570\u636E\u5305\u6316\u6398","liquid-filler":"\u6DB2\u4F53\u586B\u5145","quiver":"\u7BAD\u888B","chest-swap":"\u80F8\u7532\u4EA4\u6362","fast-use":"\u5FEB\u901F\u4F7F\u7528","anchor":"\u951A\u70B9","anti-anvil":"\u9632\u94C1\u7827","highway-builder":"\u9AD8\u901F\u8DEF\u5EFA\u9020\u8005","infinity-miner":"\u65E0\u9650\u77FF\u5DE5","breadcrumbs":"\u8DB3\u8FF9","auto-brewer":"\u81EA\u52A8\u917F\u9020\u5668","storage-esp":"\u5B58\u50A8\u7269\u900F\u89C6","no-status-effects":"\u7981\u6B62\u72B6\u6001\u6548\u679C","attribute-swap":"\u5C5E\u6027\u5207\u6362","echest-farmer":"\u672B\u5F71\u7BB1\u519C\u573A","self-anvil":"\u81EA\u8EAB\u94C1\u7827","flamethrower":"\u706B\u7130\u55B7\u5C04\u5668","anti-bed":"\u9632\u5E8A","auto-mend":"\u81EA\u52A8\u4FEE\u8865","auto-anvil":"\u81EA\u52A8\u94C1\u7827","burrow":"\u94BB\u5730","auto-sign":"\u81EA\u52A8\u544A\u793A\u724C","swarm":"\u7FA4\u7EC4","auto-reconnect":"\u81EA\u52A8\u91CD\u8FDE","build-height":"\u5EFA\u7B51\u9AD8\u5EA6","time-changer":"\u65F6\u95F4\u66F4\u6539","hole-esp":"\u5751\u6D1E\u900F\u89C6","city-esp":"\u8FDE\u57FA\u900F\u89C6","anti-afk":"\u9632 AFK","gui-move":"GUI \u4E2D\u79FB\u52A8","trident-boost":"\u4E09\u53C9\u621F\u52A9\u63A8","better-tab":"\u66F4\u597D\u7684 Tab \u5217\u8868","mount-bypass":"\u9A91\u4E58\u7ED5\u8FC7","anti-packet-kick":"\u9632\u6570\u636E\u5305\u8E22\u51FA","light-overlay":"\u5149\u7167\u8986\u76D6\u5C42","hole-filler":"\u5751\u6D1E\u586B\u5145","message-aura":"\u6D88\u606F\u5149\u73AF","fast-climb":"\u5FEB\u901F\u6500\u722C","sprint":"\u81EA\u52A8\u75BE\u8DD1","xray":"X \u5149","notebot":"\u97F3\u7B26\u76D2\u673A\u5668\u4EBA","item-highlight":"\u7269\u54C1\u9AD8\u4EAE","auto-mount":"\u81EA\u52A8\u9A91\u4E58","zoom":"\u7F29\u653E","bow-spam":"\u5F13\u8FDE\u5C04","free-look":"\u81EA\u7531\u89C6\u89D2","auto-walk":"\u81EA\u52A8\u884C\u8D70","self-trap":"\u81EA\u6211\u9677\u9631","safe-walk":"\u5B89\u5168\u884C\u8D70","auto-wasp":"\u81EA\u52A8\u9EC4\u8702","break-delay":"\u7834\u574F\u5EF6\u8FDF","auto-armor":"\u81EA\u52A8\u76D4\u7532","pop-chams":"\u56FE\u817E\u6B8B\u5F71","ghost-hand":"\u5E7D\u7075\u4E4B\u624B","slippy":"\u6ED1\u6E9C","auto-tool":"\u81EA\u52A8\u5DE5\u5177","anti-hunger":"\u6297\u9965\u997F","auto-clicker":"\u81EA\u52A8\u70B9\u51FB\u5668","auto-shearer":"\u81EA\u52A8\u526A\u6BDB","boss-stack":"BOSS \u6761\u5408\u5E76","camera-tweaks":"\u76F8\u673A\u8C03\u6574","auto-weapon":"\u81EA\u52A8\u6B66\u5668","better-chat":"\u66F4\u597D\u7684\u804A\u5929","offhand-crash":"\u526F\u624B\u5D29\u6E83","auto-breed":"\u81EA\u52A8\u7E41\u6B96","spawn-proofer":"\u9632\u5237\u602A","step":"\u81EA\u52A8\u4E0A\u9636","entity-owner":"\u5B9E\u4F53\u6240\u6709\u8005","scaffold":"\u811A\u624B\u67B6","sneak":"\u81EA\u52A8\u6F5C\u884C","no-slow":"\u65E0\u51CF\u901F","fake-player":"\u5047\u4EBA","nametags":"\u540D\u79F0\u6807\u7B7E","esp":"\u5B9E\u4F53\u900F\u89C6","rotation":"\u671D\u5411\u9501\u5B9A","elytra-fly":"\u9798\u7FC5\u98DE\u884C","auto-respawn":"\u81EA\u52A8\u91CD\u751F","entity-control":"\u5B9E\u4F53\u63A7\u5236","stash-finder":"\u85CF\u533F\u70B9\u67E5\u627E\u5668","better-beacons":"\u66F4\u597D\u7684\u4FE1\u6807","auto-nametag":"\u81EA\u52A8\u547D\u540D\u724C","elytra-boost":"\u9798\u7FC5\u52A9\u63A8","blink":"\u95EA\u73B0","block-selection":"\u65B9\u5757\u9009\u62E9","auto-city":"\u81EA\u52A8\u8FDE\u57FA","waypoints":"\u8DEF\u5F84\u70B9","auto-web":"\u81EA\u52A8\u8718\u86DB\u7F51","auto-eat":"\u81EA\u52A8\u8FDB\u98DF","hitboxes":"\u78B0\u649E\u7BB1","trail":"\u8F68\u8FF9\u7C92\u5B50","self-web":"\u81EA\u6211\u8718\u86DB\u7F51","flight":"\u98DE\u884C","reverse-step":"\u5FEB\u901F\u4E0B\u843D","blur":"\u6A21\u7CCA\u80CC\u666F","discord-presence":"Discord \u72B6\u6001","void-esp":"\u865A\u7A7A\u900F\u89C6","reach":"\u8D85\u957F\u81C2\u5C55","speed-mine":"\u5FEB\u901F\u6316\u6398","inventory-tweaks":"\u80CC\u5305\u8C03\u6574","no-fall":"\u9632\u6454\u843D","no-interact":"\u7981\u6B62\u4EA4\u4E92","portals":"\u4F20\u9001\u95E8","marker":"\u6807\u8BB0","criticals":"\u66B4\u51FB","tunnel-esp":"\u96A7\u9053\u900F\u89C6","auto-smelter":"\u81EA\u52A8\u51B6\u70BC","anchor-aura":"\u91CD\u751F\u951A\u5149\u73AF","anti-void":"\u9632\u865A\u7A7A","kill-aura":"\u6740\u622E\u5149\u73AF","parkour":"\u8DD1\u9177","spam":"\u5237\u5C4F","collisions":"\u78B0\u649E\u7BB1","click-tp":"\u70B9\u51FB\u4F20\u9001","packet-canceller":"\u6570\u636E\u5305\u53D6\u6D88\u5668","tracers":"\u5C04\u7EBF","auto-exp":"\u81EA\u52A8\u7ECF\u9A8C","jesus":"\u6C34\u4E0A\u884C\u8D70","no-mining-trace":"\u65E0\u6316\u6398\u75D5\u8FF9","bow-aimbot":"\u5F13\u81EA\u7784","anti-kick-bypass":"\u7EC8\u6781\u9632\u8E22","flight-bypass":"\u98DE\u884C\u7ED5\u8FC7","server-detector":"\u670D\u52A1\u5668\u68C0\u6D4B"};
function fmtModule(n) { return MODULE_ZH[n] || n; }

// \u7EF4\u5EA6\u6C49\u5316
var DIM_ZH = { overworld: '\u4E3B\u4E16\u754C', the_nether: '\u4E0B\u754C', the_end: '\u672B\u5730' };
function fmtDimension(d) {
  if (!d) return null;
  var key = String(d).toLowerCase().replace(/^minecraft:/, '');
  return DIM_ZH[key] || d;
}
// \u6E38\u620F\u6A21\u5F0F\u6C49\u5316
var MODE_ZH = { survival: '\u751F\u5B58', creative: '\u521B\u9020', adventure: '\u5192\u9669', spectator: '\u65C1\u89C2' };
function fmtGameMode(m) {
  if (!m) return null;
  return MODE_ZH[String(m).toLowerCase()] || m;
}
// \u65F6\u533A\u663E\u793A\uFF1A\u533A\u57DF\u4E2D\u6587\u7B80\u79F0 + UTC \u6570\u5B57\u504F\u79FB\uFF08\u53BB\u6389\u5197\u957F\u7684\u300C\u590F\u4EE4\u65F6\u95F4\u300D\u5168\u79F0\uFF0C\u53EA\u7559\u6613\u61C2\u6570\u5B57\uFF09
var TZ_REGION_ZH = { America: '\u5317\u7F8E', Asia: '\u4E9A\u6D32', Europe: '\u6B27\u6D32', Africa: '\u975E\u6D32', Oceania: '\u5927\u6D0B\u6D32', Australia: '\u6FB3\u6D32' };
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
  return off ? base + '\uFF08' + off + '\uFF09' : base;
}
// \u76AE\u80A4\u5934\u50CF\uFF1A\u7EDF\u4E00\u8D70 mc-heads.net \u539F\u59CB\u76AE\u80A4\uFF08\u6309\u6E38\u620F\u540D\u89E3\u6790\uFF0C\u6B63\u7248\u540D\u81EA\u52A8\u547D\u4E2D Mojang\uFF09\uFF0C
// canvas \u624B\u52A8\u88C1\u526A\u5934\u90E8\u6B63\u9762 + \u4FDD\u7559 Alpha\uFF0C\u900F\u660E\u76AE\u80A4\u50CF\u7D20\u4E0D\u518D\u6E32\u67D3\u6210\u9ED1\u5757\uFF08crafatar \u5DF2\u505C\u670D 500\uFF09\u3002
function skin(p, size) {
  var s = size || 64;
  var name = (p && p.name) ? String(p.name) : 'MHF_Steve';
  var raw = 'https://mc-heads.net/skin/' + encodeURIComponent(name);
  var fb = 'https://mc-heads.net/avatar/' + encodeURIComponent(name) + '/' + s;
  return '<canvas class="skin" width="' + s + '" height="' + s + '" data-head-raw="' + raw + '" data-head-fallback="' + fb + '"></canvas>';
}

// \u628A\u539F\u59CB\u76AE\u80A4\u5934\u90E8\u6B63\u9762\u7684 8x8 \u533A\u57DF\u7ED8\u5236\u5230 canvas\uFF0C\u4FDD\u7559\u900F\u660E\u50CF\u7D20\uFF08\u900F\u660E\u4E0D\u518D\u6E32\u67D3\u6210\u9ED1\u5757\uFF09
function drawHead(canvas, img) {
  var c = canvas.getContext('2d');
  var s = canvas.width;
  c.clearRect(0, 0, s, s);
  c.imageSmoothingEnabled = false; // \u50CF\u7D20\u98CE\uFF0C\u6E05\u6670\u663E\u793A\u6BCF\u4E2A\u50CF\u7D20
  try { c.drawImage(img, 8, 8, 8, 8, 0, 0, s, s); } catch (e) {}   // \u5934\u90E8\u6B63\u9762\uFF08\u5E95\u5C42\uFF0C8x8\uFF09
  try { c.drawImage(img, 40, 8, 8, 8, 0, 0, s, s); } catch (e) {}  // \u5E3D\u5B50/\u7B2C\u4E8C\u5C42\uFF0840,8\uFF0C\u540C\u6837\u4FDD\u7559 alpha\uFF09
}
// \u626B\u63CF DOM \u4E2D\u65B0\u51FA\u73B0\u7684\u76AE\u80A4 canvas \u5E76\u5F02\u6B65\u52A0\u8F7D\u539F\u59CB\u76AE\u80A4\u7EB9\u7406\u6E32\u67D3
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
// \u76D1\u542C DOM \u53D8\u5316\uFF0C\u5217\u8868/\u8BE6\u60C5\u6BCF\u6B21\u5237\u65B0\u540E\u81EA\u52A8\u6E32\u67D3\u65B0\u7684\u76AE\u80A4\u5934\u50CF
new MutationObserver(function(muts) {
  muts.forEach(function(m) {
    (m.addedNodes || []).forEach(function(n) {
      if (n.nodeType === 1) renderHeadCanvases(n);
    });
  });
}).observe(document.body, { childList: true, subtree: true });

// \u5185\u8054 MD5\uFF08\u4E0E worker.js \u540C\u6B3E\uFF0C\u6D4F\u89C8\u5668\u7AEF\u8BA1\u7B97\u79BB\u7EBF UUID \u7528\uFF09
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

// \u2500\u2500 \u4E3B\u9898\u5207\u6362\uFF08\u771F\xB7\u6DF1\u8272/\u6D45\u8272\uFF0ClocalStorage \u6301\u4E45\u5316\uFF09 \u2500\u2500
function applyTheme(t) {
  if (t !== 'light' && t !== 'dark') {
    t = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.dataset.theme = t;
  $('theme-btn').textContent = t === 'dark' ? '\u2600\uFE0F' : '\u{1F319}';
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

// \u2500\u2500 HTTP \u8BF7\u6C42 \u2500\u2500
function api(path, opts) {
  opts = opts || {};
  var headers = { 'Content-Type': 'application/json' };
  if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
  return fetch(API_BASE + path, { method: opts.method || 'GET', headers: headers, body: opts.body ? JSON.stringify(opts.body) : undefined })
    .then(function(r) { return r.json().catch(function(){ return {}; }).then(function(j){ j._status = r.status; return j; }); })
    .catch(function(){ return { _status: 0, error: '\u7F51\u7EDC\u9519\u8BEF\uFF1A\u65E0\u6CD5\u8FDE\u63A5\u540E\u53F0\uFF0C\u8BF7\u68C0\u67E5\u7F51\u7EDC\u6216 VPN \u8282\u70B9', _network: true }; });
}

// \u2500\u2500 \u767B\u5F55 \u2500\u2500
function tryLogin() {
  var u = $('lg-user').value.trim(), p = $('lg-pass').value;
  $('lg-err').textContent = '';
  api('/api/admin/login', { method: 'POST', body: { username: u, password: p } }).then(function(res) {
    if (res._network) {
      $('lg-err').textContent = res.error || '\u7F51\u7EDC\u9519\u8BEF';
    } else if (res.success && res.token) {
      state.token = res.token;
      localStorage.setItem('admin_token', res.token);
      showApp();
    } else {
      $('lg-err').textContent = res.error || '\u767B\u5F55\u5931\u8D25';
    }
  }).catch(function(err) {
    $('lg-err').textContent = '\u7F51\u7EDC\u9519\u8BEF\uFF1A' + (err.message || '\u65E0\u6CD5\u8FDE\u63A5\u540E\u53F0');
  });
}
function logout() {
  state.token = null;
  localStorage.removeItem('admin_token');
  $('app').style.display = 'none';
  $('login').style.display = 'flex';
  if (state.timer) { clearInterval(state.timer); state.timer = null; }
}

// \u2500\u2500 \u5E94\u7528\u521D\u59CB\u5316 \u2500\u2500
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

// \u2500\u2500 \u6982\u89C8 \u2500\u2500
function loadOverview() {
  api('/api/admin/analytics').then(function(a) {
    if (a._status === 401 || a._status === 403) { return logout(); }
    state.analytics = a;
    var html = '';
    html += '<div class="kpi-grid">';
    html += kpi('\u{1F30D}', a.total_users, '\u603B\u73A9\u5BB6');
    html += kpi('\u{1F7E2}', a.online_count, '\u5F53\u524D\u5728\u7EBF');
    html += kpi('\u{1F512}', a.vpn_suspected, '\u7591\u4F3CVPN');
    html += kpi('\u2705', (a.premium && a.premium.premium) || 0, '\u6B63\u7248\u8D26\u6237');
    html += '</div>';

    html += '<div class="card"><div class="sec-title">\u6700\u8FD1 14 \u5929\u6D3B\u8DC3</div>';
    html += '<div class="bars">';
    var max = 1;
    (a.daily_active || []).forEach(function(d){ if (d.active > max) max = d.active; });
    (a.daily_active || []).forEach(function(d){
      var h = Math.max(2, Math.round(d.active / max * 96));
      var dd = new Date(d.date + 'T00:00:00');
      var label = (dd.getMonth() + 1) + '\u6708' + dd.getDate() + '\u65E5';
      html += '<div class="bar-wrap"><span class="v">' + d.active + '</span><div class="bar" style="height:' + h + 'px"></div><span class="d">' + label + '</span></div>';
    });
    html += '</div></div>';

    html += '<div class="card"><div class="sec-title">\u56FD\u5BB6\u5206\u5E03</div>';
    (a.country_distribution || []).slice(0, 12).forEach(function(c){
      html += '<div class="row"><div style="font-size:24px;flex-shrink:0;">' + flagEmoji(c.client_country) + '</div><div class="info"><div class="name">' + countryName(c.client_country) + '</div></div><div class="right">' + c.c + ' \u4EBA</div></div>';
    });
    html += '</div>';

    html += '<div class="card"><div class="sec-title">\u7248\u672C\u5206\u5E03</div>';
    (a.version_distribution || []).slice(0, 10).forEach(function(c){
      html += '<div class="row"><div class="info"><div class="name">' + esc(c.version) + '</div></div><div class="right">' + c.c + ' \u4EBA</div></div>';
    });
    html += '</div>';

    $('view').innerHTML = html;
  });
}
function kpi(icon, num, label) {
  return '<div class="kpi"><div class="icon">' + icon + '</div><div class="num">' + (num || 0) + '</div><div class="lbl">' + label + '</div></div>';
}

// \u2500\u2500 \u73A9\u5BB6\uFF08\u5E26\u641C\u7D22 + \u7B5B\u9009\uFF09 \u2500\u2500
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

    var html = '<div style="padding:2px 4px 4px;" class="sec-title">\u5171 ' + state.players.length + ' \u4EBA \xB7 \u5728\u7EBF ' + online + '</div>';
    html += '<div class="toolbar">';
    html += '<input class="search" id="p-search" type="text" placeholder="\u641C\u7D22\u540D\u5B57 / UUID / \u56FD\u5BB6 / \u670D\u52A1\u5668" value="' + esc(state.playerFilter) + '">';
    html += chip('all', '\u5168\u90E8');
    html += chip('online', '\u{1F7E2} \u5728\u7EBF');
    html += chip('offline', '\u26AA \u79BB\u7EBF');
    html += chip('premium', '\u2705 \u6B63\u7248');
    html += chip('vpn', '\u{1F512} VPN');
    html += '</div>';
    html += '<div class="card">';
    list.forEach(function(p) {
      html += playerRow(p);
    });
    if (!list.length) html += '<div class="row">\u6682\u65E0\u5339\u914D\u73A9\u5BB6</div>';
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
  badges += p.is_premium ? '<span class="badge green">\u2705\u6B63\u7248</span>' : '<span class="badge gray">\u{1F513}\u79BB\u7EBF\u8D26\u53F7</span>';
  badges += p.is_online ? '<span class="badge green">\u{1F7E2}\u5728\u7EBF</span>' : '<span class="badge gray">\u26AA\u79BB\u7EBF</span>';
  // \u4EC5\u5728\u5728\u7EBF\u65F6\u624D\u663E\u793A\u72B6\u6001\u5FBD\u6807\uFF0C\u79BB\u7EBF\u73A9\u5BB6\u4E0D\u518D\u6B8B\u7559\u300C\u591A\u4EBA\u670D\u52A1\u5668\u300D\u7B49\u4E0A\u4E00\u6B21\u72B6\u6001
  if (p.is_online) badges += statusBadge(p.status);
  if (p.is_vpn_suspected) badges += '<span class="badge red">\u{1F512}VPN</span>';
  var sub = (p.client_country ? flagEmoji(p.client_country) + ' ' + countryName(p.client_country) : '') +
    (p.client_city ? ' \xB7 ' + esc(fmtCity(p.client_city)) : '') +
    (p.client_timezone ? ' \xB7 \u23F0 ' + esc(fmtTimezone(p.client_timezone)) : '') +
    (p.client_as_org ? ' \xB7 \u{1F4E1} ' + esc(fmtIsp(p.client_as_org)) : '') +
    (p.server_name ? ' \xB7 \u{1F3AE} ' + esc(p.server_name) : '');
  var lat = '';
  // \u53EA\u6709\u5728\u7EBF\u73A9\u5BB6\u624D\u663E\u793A\u5EF6\u8FDF
  if (p.is_online) {
    if (p.server_latency !== null && p.server_latency !== undefined && p.server_latency > 0) lat += '\u{1F5A5} <span class="lat ' + latClass(p.server_latency) + '">' + Math.round(p.server_latency) + 'ms</span>';
    if (p.network_latency !== null && p.network_latency !== undefined && p.network_latency > 0) lat += ' \xB7 \u{1F4F6} <span class="lat ' + latClass(p.network_latency) + '">' + Math.round(p.network_latency) + 'ms</span>';
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

// \u6B63\u7248\u8D26\u53F7\u9875\uFF1A\u53EA\u5C55\u793A\u5FAE\u8F6F\u6B63\u7248\u73A9\u5BB6\u82B1\u540D\u518C\uFF08\u542B XUID \u5FAE\u8F6F\u8D26\u53F7\u6807\u8BC6\uFF09
function loadPremium() {
  api('/api/admin/players').then(function(res) {
    if (res._status === 401 || res._status === 403) return logout();
    var all = res.users || [];
    var list = all.filter(function(p){ return p.is_premium === 1 || p.is_premium === true; });
    var html = '<div class="sec-title" style="padding:2px 4px 4px;">\u5FAE\u8F6F\u6B63\u7248\u73A9\u5BB6 \xB7 ' + list.length + ' \u4EBA\uFF08\u79BB\u7EBF\u8D26\u53F7\u5DF2\u5FFD\u7565\uFF09</div>';
    html += '<div class="card">';
    list.forEach(function(p){ html += premiumRow(p); });
    if (!list.length) html += '<div class="row">\u6682\u65E0\u6B63\u7248\u73A9\u5BB6</div>';
    html += '</div>';
    $('view').innerHTML = html;
  });
}

// \u6B63\u7248\u73A9\u5BB6\u5355\u884C\uFF1A\u76AE\u80A4 + \u73A9\u5BB6\u540D + \u5FAE\u8F6F\u8D26\u53F7 XUID + \u6240\u5728\u670D\u52A1\u5668
function premiumRow(p) {
  var msAccount = p.gamertag || p.name;
  var msId = fmtXuid(p.xuid);
  return '<div class="row">' +
    skin(p, 64) +
    '<div class="info">' +
    '<div class="name">' + esc(p.name) + ' <span class="badge green">\u2705\u6B63\u7248</span></div>' +
    '<div class="sub">\u{1F3AE} \u5FAE\u8F6F\u8D26\u6237\uFF1A<span class="mono">' + esc(msAccount) + '</span></div>' +
    (msId ? '<div class="sub">\u{1F511} XUID\uFF1A<span class="mono">' + esc(msId) + '</span></div>' : '') +
    (p.server_name ? '<div class="sub">\u{1F5A5} ' + esc(p.server_name) + (p.server_ip ? ' \xB7 ' + esc(p.server_ip) : '') + '</div>' : '') +
    '</div>' +
    '<div class="right">' + fmtTime(p.last_seen) + '</div>' +
    '</div>';
}

// \u79BB\u7EBF\u5BC6\u7801\u9875\uFF1A\u6309\u670D\u52A1\u5668\u5206\u7EC4\u5C55\u793A\u300C\u73A9\u5BB6\u540D + \u5BC6\u7801\u300D\uFF0C\u652F\u6301\u4E00\u952E\u590D\u5236
function loadPasswords() {
  api('/api/admin/offline-passwords').then(function(res) {
    if (res._status === 401 || res._status === 403) return logout();
    var records = res.records || [];
    var html = '<div class="sec-title" style="padding:2px 4px 4px;">\u79BB\u7EBF\u670D\u52A1\u5668\u5BC6\u7801 \xB7 ' + records.length + ' \u6761</div>';
    if (!records.length) {
      html += '<div class="card"><div class="row" style="cursor:default;">\u6682\u65E0\u8BB0\u5F55\uFF08\u73A9\u5BB6\u8FDB\u670D\u540E\u4F1A\u5728\u6B64\u663E\u793A\uFF09</div></div>';
      $('view').innerHTML = html;
      return;
    }
    // \u6309\u670D\u52A1\u5668\u5206\u7EC4\uFF08server_ip + server_name\uFF09\uFF0C\u7EC4\u95F4\u4FDD\u6301\u65F6\u95F4\u5012\u5E8F
    var groups = [], index = {};
    records.forEach(function(r) {
      var key = (r.server_ip || 'unknown') + '|' + (r.server_name || '');
      if (!index[key]) { index[key] = { ip: r.server_ip, name: r.server_name, items: [] }; groups.push(index[key]); }
      index[key].items.push(r);
    });
    groups.forEach(function(g) {
      html += '<div class="card">';
      html += '<div class="sec-title">\u{1F5A5} ' + esc(g.ip || '\u672A\u77E5\u670D\u52A1\u5668') + (g.name ? ' \xB7 ' + esc(g.name) : '') + '<span class="tag" style="margin-left:8px;">' + g.items.length + ' \u6761</span></div>';
      g.items.forEach(function(r) {
        var isReg = r.type === 'register';
        var typeTag = isReg ? '<span class="tag">\u6CE8\u518C</span>' : '<span class="tag blue">\u767B\u5F55</span>';
        html += '<div class="row" style="cursor:default;">' +
          '<div style="font-size:22px;flex-shrink:0;">\u{1F510}</div>' +
          '<div class="info"><div class="name">' + esc(r.name || '\u672A\u77E5\u73A9\u5BB6') + ' ' + typeTag + '</div>' +
          '<div class="sub">\u{1F511} <span class="mono">' + esc(r.password || '') + '</span> \xB7 ' + fmtTime(r.created_at) + '</div></div>' +
          '<button class="copy-btn" data-pwd="' + esc(r.password || '') + '">\u{1F4CB} \u590D\u5236</button>' +
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
    btn.textContent = ok ? '\u2705 \u5DF2\u590D\u5236' : '\u274C \u5931\u8D25';
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
  var modules = (p.modules || []).map(function(m){ return '<span class="tag blue">' + esc(fmtModule(m)) + '</span>'; }).join('') || '<span class="tag">\u65E0</span>';
  var html = '<div class="sheet-head"><div class="grab"></div><button class="sheet-close" onclick="closeSheet()" aria-label="\u5173\u95ED">\u2715</button></div>';
  html += '<h2>' + skin(p, 96) + '<span>' + flagEmoji(p.client_country) + ' ' + esc(p.name) + '</span></h2>';
  html += '<div style="font-size:13px;color:var(--text2);margin:6px 0 4px;">' + (p.is_online ? '<span class="dot on"></span> \u5728\u7EBF' : '\u26AA \u79BB\u7EBF') + ' \xB7 ' + fmtTime(p.last_seen) + '</div>';
  html += '<div style="margin:12px 0;">';
  if (p.is_premium) {
    html += '<button onclick="togglePremium(&quot;' + p.uuid + '&quot;, 0)" style="padding:6px 12px;border:1px solid #ccc;background:#fff;cursor:pointer;border-radius:4px;">\u6807\u8BB0\u4E3A\u79BB\u7EBF</button>';
  } else {
    html += '<button onclick="togglePremium(&quot;' + p.uuid + '&quot;, 1)" style="padding:6px 12px;border:1px solid #4CAF50;background:#4CAF50;color:#fff;cursor:pointer;border-radius:4px;">\u6807\u8BB0\u4E3A\u6B63\u7248</button>';
  }
  html += '</div>';
  html += kv('UUID', p.uuid);
  html += kv('\u6B63\u7248\u8D26\u6237', p.is_premium ? '\u2705 \u662F' + (p.gamertag ? '\uFF08' + p.gamertag + '\uFF09' : '') : '\u26AA \u79BB\u7EBF');
  html += kv('\u5FAE\u8F6F\u8D26\u53F7', fmtXuid(p.xuid) || (p.is_premium ? p.name : '\u65E0') || null);
  html += kv('IP', p.client_ip);
  var loc = [];
  if (p.client_country) loc.push(countryName(p.client_country));
  if (p.client_region) loc.push(fmtRegion(p.client_region));
  html += kv('\u56FD\u5BB6/\u5730\u533A', loc.join(' \xB7 ') || null);
  var ct = [];
  var city = fmtCity(p.client_city);
  if (city) ct.push(city);
  if (p.client_timezone) ct.push(fmtTimezone(p.client_timezone));
  html += kv('\u57CE\u5E02/\u65F6\u533A', ct.join(' \xB7 ') || null);
  html += kv('\u8FD0\u8425\u5546', p.client_as_org ? fmtIsp(p.client_as_org) : null);
  // \u53EA\u6709\u5728\u7EBF\u73A9\u5BB6\u624D\u663E\u793A VPN \u5224\u5B9A\u3001\u670D\u52A1\u5668\u3001\u5EF6\u8FDF\u7B49\u5B9E\u65F6\u4FE1\u606F
  if (p.is_online) {
    html += kv('VPN \u5224\u5B9A', p.is_vpn_suspected ? '\u{1F512} \u7591\u4F3C VPN/\u673A\u623F' : '\u6B63\u5E38');
    var svr = p.server_name ? p.server_name : '';
    if (svr && p.server_ip) svr += ' \xB7 ' + p.server_ip;
    html += kv('\u670D\u52A1\u5668', svr || null);
    html += kv('\u670D\u52A1\u5668\u5EF6\u8FDF', p.server_latency !== null && p.server_latency !== undefined ? Math.round(p.server_latency) + ' ms' : null);
    html += kv('\u7F51\u7EDC\u5EF6\u8FDF', p.network_latency !== null && p.network_latency !== undefined ? Math.round(p.network_latency) + ' ms' : null);
  }
  html += kv('\u5750\u6807', (p.pos_x !== null && p.pos_x !== undefined) ? Math.round(p.pos_x) + ', ' + Math.round(p.pos_y) + ', ' + Math.round(p.pos_z) : null);
  html += kv('\u7EF4\u5EA6', fmtDimension(p.dimension));
  html += kv('\u6E38\u620F\u6A21\u5F0F', fmtGameMode(p.game_mode));
  html += kv('\u5F53\u524D\u6D3B\u52A8', p.current_activity || null);
  html += kv('\u51FB\u6740/\u6B7B\u4EA1', (p.kill_count || 0) + ' / ' + (p.death_count || 0));
  html += kv('\u6E38\u620F\u65F6\u957F', fmtDuration(p.total_playtime));
  html += kv('\u4F7F\u7528\u6B21\u6570', p.usage_count);
  var ver = [];
  if (p.version) ver.push(p.version);
  if (p.minecraft_version) ver.push('MC ' + p.minecraft_version);
  html += kv('\u7248\u672C', ver.join(' \xB7 ') || null);
  html += '<div class="kv" style="display:block;"><div class="k" style="margin-bottom:8px;">\u5DF2\u5F00\u542F\u6A21\u5757</div><div>' + modules + '</div></div>';
  var sheet = $('sheet');
  sheet.innerHTML = html;
  $('sheet-overlay').classList.add('open');
  requestAnimationFrame(function(){ sheet.classList.add('open'); });
}
// \u7A7A\u503C\u7EDF\u4E00\u663E\u793A\u300C\u672A\u77E5\u5F85\u5237\u65B0\u300D\uFF1Anull/undefined/\u7A7A\u4E32/\u5360\u4F4D\u7B26 \u4E0D\u518D\u663E\u793A\u6210 \u2014\u3001null\u3001unknown
function park(v) {
  if (v === undefined || v === null) return '\u672A\u77E5\u5F85\u5237\u65B0';
  var s = String(v).trim();
  if (!s || s === '\u2014' || s.toLowerCase() === 'null' || s.toLowerCase() === 'unknown') return '\u672A\u77E5\u5F85\u5237\u65B0';
  return v;
}
function kv(k, v) { return '<div class="kv"><span class="k">' + k + '</span><span class="val">' + esc(String(park(v))) + '</span></div>'; }
function closeSheet() {
  $('sheet').classList.remove('open');
  $('sheet-overlay').classList.remove('open');
}

function togglePremium(uuid, isPremium) {
  if (!confirm('\u786E\u5B9A\u8981' + (isPremium ? '\u6807\u8BB0\u4E3A\u6B63\u7248' : '\u6807\u8BB0\u4E3A\u79BB\u7EBF') + '\u5417\uFF1F')) return;
  api('/api/admin/toggle-premium', { method: 'POST', body: { uuid: uuid, is_premium: isPremium } }).then(function(res) {
    if (res.success) {
      alert('\u5DF2\u66F4\u65B0');
      loadPlayers();
      closeSheet();
    } else {
      alert('\u64CD\u4F5C\u5931\u8D25\uFF1A' + (res.error || '\u672A\u77E5\u9519\u8BEF'));
    }
  });
}

// \u2500\u2500 \u6D88\u606F \u2500\u2500
function loadChat() {
  // \u540C\u65F6\u62C9\u53D6\u804A\u5929\u5386\u53F2\u4E0E\u73A9\u5BB6\u5217\u8868\uFF0C\u7528\u4E8E\u300C\u9009\u62E9\u6536\u4EF6\u4EBA\u300D\u4E0B\u62C9\u6846
  Promise.all([api('/api/messages/history'), api('/api/admin/players')]).then(function(results) {
    var res = results[0], pr = results[1];
    if (res._status === 401 || res._status === 403) { return logout(); }
    state.players = pr.users || [];
    var msgs = Array.isArray(res) ? res : [];
    var html = '<div class="sec-title" style="padding:2px 4px 4px;">\u804A\u5929\u5386\u53F2\uFF08' + msgs.length + '\uFF09</div>';
    html += '<div class="card pad"><div class="chat">';
    msgs.forEach(function(m){
      var admin = m.from_admin === 1;
      html += '<div class="msg' + (admin ? ' admin' : '') + '"><div class="meta">' + esc(m.sender || 'Admin') + ' \u2192 ' + esc(m.target_name || '\u6240\u6709\u4EBA') + ' \xB7 ' + fmtTime(m.created_at) + '</div><div class="txt">' + esc(m.message) + '</div></div>';
    });
    html += '</div></div>';
    html += '<div class="card pad"><div class="sec-title" style="padding:0 0 10px;">\u53D1\u9001\u6D88\u606F\uFF08\u9009\u62E9\u6536\u4EF6\u4EBA\uFF09</div>';
    html += '<div class="field"><select id="msg-target" class="select">';
    html += '<option value="">\u{1F4E2} \u5E7F\u64AD\u6240\u6709\u4EBA</option>';
    state.players.forEach(function(p){
      html += '<option value="' + esc(p.name) + '">' + esc(p.name) + (p.is_online ? ' \u25CF \u5728\u7EBF' : '') + '</option>';
    });
    html += '</select></div>';
    html += '<div class="composer"><input id="msg-text" type="text" placeholder="\u6D88\u606F\u5185\u5BB9..."><button id="msg-send">\u{1F4E4}</button></div></div>';
    $('view').innerHTML = html;
    $('msg-send').onclick = sendMsg;
  });
}
function sendMsg() {
  var target = $('msg-target').value.trim();
  var text = $('msg-text').value.trim();
  if (!text) return;
  var body = { message: text, target_name: target || '\u6240\u6709\u4EBA' };
  if (target) {
    var found = state.players.find(function(p){ return p.name === target; });
    if (found) body.target_uuid = found.uuid;
  }
  api('/api/messages/send', { method: 'POST', body: body }).then(function(res) {
    if (res.success) { $('msg-text').value = ''; loadChat(); }
    else { alert(res.error || '\u53D1\u9001\u5931\u8D25'); }
  });
}

// \u2500\u2500 \u5F02\u5E38\uFF08\u5D29\u6E83 + \u5F02\u5E38\u884C\u4E3A\uFF09 \u2500\u2500
function loadIssues() {
  api('/api/admin/crashes').then(function(c) {
    api('/api/admin/anomalies').then(function(a) {
      var html = '';
      html += '<div class="card"><div class="sec-title">\u{1F4A5} \u5D29\u6E83\uFF08' + (c.total || 0) + '\uFF09</div>';
      (c.crashes || []).slice(0, 20).forEach(function(x){
        html += '<div class="row"><div style="font-size:22px;">\u{1F4A5}</div><div class="info"><div class="name">' + esc((x.message || '').slice(0, 60)) + '</div><div class="sub">' + esc(x.version || '') + ' \xB7 \u51FA\u73B0 ' + x.count + ' \u6B21 \xB7 ' + fmtTime(x.last_seen) + '</div></div></div>';
      });
      html += '</div>';
      html += '<div class="card"><div class="sec-title">\u26A0\uFE0F \u5F02\u5E38\u884C\u4E3A\uFF08' + (a.total || 0) + '\uFF09</div>';
      (a.anomalies || []).slice(0, 20).forEach(function(x){
        html += '<div class="row"><div style="font-size:22px;">\u26A0\uFE0F</div><div class="info"><div class="name">' + esc(x.type || '\u672A\u77E5') + ' \xB7 ' + esc(x.severity || '') + '</div><div class="sub">' + esc((x.message || '').slice(0, 60)) + ' \xB7 ' + (x.name ? esc(x.name) : '') + '</div></div></div>';
      });
      html += '</div>';
      $('view').innerHTML = html;
    });
  });
}

// \u2500\u2500 \u8BBE\u7F6E\uFF08\u8FDC\u7A0B\u914D\u7F6E\uFF09 \u2500\u2500
function loadSettings() {
  api('/api/config').then(function(res) {
    var cfg = res.config || {};
    var keys = Object.keys(cfg);
    var html = '';
    html += '<div class="card pad"><div class="sec-title" style="padding:0 0 10px;">\u{1F4D6} \u8FDC\u7A0B\u914D\u7F6E\u600E\u4E48\u7528</div>';
    html += '<div class="sub" style="line-height:1.8;">';
    html += '\xB7 \u6E38\u620F\u5185 addon \u6BCF\u9694 <b>60 \u79D2</b> \u81EA\u52A8\u8BFB\u53D6\u4E00\u6B21\u914D\u7F6E\uFF0C\u540E\u53F0\u6539\u5B8C\u65E0\u9700\u91CD\u542F\u6E38\u620F\u5373\u81EA\u52A8\u751F\u6548\u3002<br>';
    html += '\xB7 \u5728\u4E0B\u65B9\u300C\u6DFB\u52A0\u914D\u7F6E\u300D\u8F93\u5165 key \u4E0E value \u540E\u70B9\u300C\u6DFB\u52A0\u300D\uFF1B\u540C\u540D key \u4F1A\u8986\u76D6\u65E7\u503C\u3002<br>';
    html += '\xB7 \u652F\u6301\u7684 key\uFF08value \u586B true / false\uFF09\uFF1A<br>';
    html += '&nbsp;&nbsp;<b>update_notice_enabled</b> \u2014\u2014 \u8FDB\u670D\u65F6\u68C0\u6D4B\u65B0\u7248\u672C\u5E76\u63D0\u793A\u73A9\u5BB6\uFF1B<br>';
    html += '&nbsp;&nbsp;<b>stats_report_enabled</b> \u2014\u2014 \u73A9\u5BB6\u6570\u636E\uFF08\u5750\u6807/IP/\u6A21\u5757\u7B49\uFF09\u662F\u5426\u4E0A\u62A5\u5230\u540E\u53F0\u3002';
    html += '</div></div>';

    html += '<div class="card"><div class="sec-title">\u2699\uFE0F \u5F53\u524D\u914D\u7F6E\uFF08addon \u6BCF\u5206\u949F\u81EA\u52A8\u8BFB\u53D6\uFF09</div>';
    if (!keys.length) { html += '<div class="row">\u6682\u65E0\u914D\u7F6E\u9879</div>'; }
    // \u914D\u7F6E\u9879\u4E2D\u6587\u5907\u6CE8\uFF1A\u5E2E\u52A9\u7406\u89E3\u6BCF\u4E2A\u5F00\u5173\u7684\u4F5C\u7528
    var CFG_DESC = {
      'update_notice_enabled': '\u66F4\u65B0\u63D0\u9192\uFF1A\u8FDB\u670D\u65F6\u68C0\u6D4B\u65B0\u7248\u672C\u5E76\u63D0\u793A\uFF08true/false\uFF09',
      'stats_report_enabled': '\u7EDF\u8BA1\u4E0A\u62A5\uFF1A\u63A7\u5236\u73A9\u5BB6\u6570\u636E\u662F\u5426\u4E0A\u62A5\u5230\u540E\u53F0\uFF08true/false\uFF09'
    };
    keys.forEach(function(k){
      html += '<div class="row"><div class="info"><div class="name">' + esc(k) + '</div>' +
        '<div class="sub">' + (CFG_DESC[k] || '\u81EA\u5B9A\u4E49\u914D\u7F6E\u9879\uFF08addon \u8BFB\u53D6\uFF09') + '</div>' +
        '<div class="sub" style="color:var(--text);">\u5F53\u524D\u503C\uFF1A' + esc(cfg[k]) + '</div></div><div class="right">\u751F\u6548\u4E2D</div></div>';
    });
    html += '</div>';
    html += '<div class="card pad"><div class="sec-title" style="padding:0 0 10px;">\u6DFB\u52A0\u914D\u7F6E</div>';
    html += '<div class="field"><input id="cfg-key" type="text" placeholder="key\uFF08\u5982 update_notice_enabled / stats_report_enabled\uFF09"></div>';
    html += '<div class="field"><input id="cfg-val" type="text" placeholder="value\uFF08\u5982 true\uFF09"></div>';
    html += '<button class="primary" id="cfg-add">\u6DFB\u52A0</button></div>';
    $('view').innerHTML = html;
    $('cfg-add').onclick = function(){
      var k = $('cfg-key').value.trim(), v = $('cfg-val').value;
      if (!k) return;
      api('/api/admin/config', { method: 'POST', body: { key: k, value: v } }).then(function(r){
        if (r.success) loadSettings(); else alert(r.error || '\u5931\u8D25');
      });
    };
  });
}

// \u2500\u2500 \u4E8B\u4EF6\u7ED1\u5B9A \u2500\u2500
$('lg-btn').onclick = tryLogin;
document.addEventListener('keydown', function(e){ if (e.key === 'Enter' && $('login').style.display !== 'none') tryLogin(); });
$('logout-btn').onclick = logout;
$('theme-btn').onclick = toggleTheme;
$('sheet-overlay').onclick = closeSheet;
$('backtop').onclick = function() { window.scrollTo({ top: 0, behavior: 'smooth' }); };

// \u7B5B\u9009 chip \u4E8B\u4EF6\uFF08\u5192\u6CE1\u59D4\u6258\uFF09
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

// \u4E0B\u6ED1\u4E00\u6BB5\u8DDD\u79BB\u540E\u663E\u793A\u300C\u8FD4\u56DE\u9876\u90E8\u300D\u6309\u94AE\uFF0C\u56DE\u9876\u540E\u81EA\u52A8\u9690\u85CF
window.addEventListener('scroll', function() {
  var btn = $('backtop');
  if (btn) btn.classList.toggle('show', window.scrollY > 420);
});

// \u542F\u52A8
initTheme();
if (state.token) { showApp(); } else { $('app').style.display = 'none'; $('login').style.display = 'flex'; }
<\/script>
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
<\/script>
</body>
</html>`;

// worker.js
var HEARTBEAT_TIMEOUT = 45 * 1e3;
async function sha256(message) {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}
__name(sha256, "sha256");
function isFakePlayerName(name) {
  if (!name) return true;
  const n = String(name).trim();
  if (!n) return true;
  return /^Player\d+$/.test(n);
}
__name(isFakePlayerName, "isFakePlayerName");
function isLocalServerIp(ip) {
  if (!ip) return false;
  const i = String(ip).toLowerCase().trim();
  return i === "localhost" || i.startsWith("127.") || i.startsWith("192.168.") || i.startsWith("10.") || i.startsWith("0.") || /^172\.(1[6-9]|2[0-9]|3[01])\./.test(i) || i === "::1" || i === "[::1]";
}
__name(isLocalServerIp, "isLocalServerIp");
function isValidUuid(uuid) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(uuid || "");
}
__name(isValidUuid, "isValidUuid");
function sanitizeXuid(xuid) {
  if (xuid == null) return null;
  const v = String(xuid).trim();
  return /^\d{8,20}$/.test(v) ? v : null;
}
__name(sanitizeXuid, "sanitizeXuid");
async function resolvePremium(uuid, name, xuid) {
  if (sanitizeXuid(xuid)) return 1;
  if (!uuid || !name) return 0;
  const mojang = await lookupMojangProfile(name);
  if (mojang === null) return null;
  if (mojang === false) return 0;
  return String(uuid).replace(/-/g, "").toLowerCase() === mojang.toLowerCase() ? 1 : 0;
}
__name(resolvePremium, "resolvePremium");
async function lookupMojangProfile(name) {
  if (!name) return false;
  const clean = String(name).trim();
  if (!clean || clean.length > 16) return false;
  try {
    const resp = await fetch("https://api.mojang.com/users/profiles/minecraft/" + encodeURIComponent(clean), {
      headers: { "Accept": "application/json" }
    });
    if (resp.status === 200) {
      const data = await resp.json();
      return data && data.id ? String(data.id) : false;
    }
    if (resp.status === 404) return false;
    return null;
  } catch (e) {
    return null;
  }
}
__name(lookupMojangProfile, "lookupMojangProfile");
var VPN_ORG_KEYWORDS = [
  "cloudflare",
  "amazon",
  "aws",
  "google",
  "microsoft",
  "azure",
  "digitalocean",
  "ovh",
  "hetzner",
  "linode",
  "choopa",
  "vultr",
  "m247",
  "nord",
  "mullvad",
  "proton",
  "expressvpn",
  "surfshark",
  "cyberghost",
  "ipvanish",
  "datacamp",
  "cdn77",
  "leaseweb",
  "contabo",
  "ionos",
  "oracle",
  "alibaba",
  "aliyun",
  "tencent",
  "huawei",
  "cogent",
  "quadranet",
  "hostwinds",
  "buyvm",
  "zenlayer",
  "ipxo",
  "packet",
  "equinix",
  "psychz",
  "hostinger",
  "namecheap",
  "colocrossing",
  "hivelocity",
  "datacenter",
  "hosting",
  "vpn",
  "proxy",
  "fdcservers",
  "vps",
  "vds",
  "colocation",
  "wholesale",
  "seedbox",
  "netcup",
  "worldstream",
  "serverius",
  "spartanhost",
  "egihosting",
  "racknerd",
  "virmach",
  "reliablesite",
  "intergrid",
  "chocotel",
  "privateinternetaccess",
  "24shells",
  "solarvps",
  "leapswitch",
  "phanes",
  "netprotect",
  "dedicated",
  "baremetal"
];
var VPN_SUSPECT_ASN = /* @__PURE__ */ new Set([
  13335,
  15169,
  16509,
  14618,
  8075,
  14061,
  16276,
  24940,
  20473,
  9009,
  63949,
  36352,
  8100,
  40676,
  29802,
  16265,
  51167,
  46562,
  206092,
  62240,
  30058,
  212238,
  40021,
  141995,
  49505,
  63473,
  394256,
  54994,
  44066
]);
function isVpnSuspected(asOrg, asn) {
  if (asn && VPN_SUSPECT_ASN.has(Number(asn))) return 1;
  const org = String(asOrg || "").toLowerCase();
  if (!org) return 0;
  return VPN_ORG_KEYWORDS.some((k) => org.includes(k)) ? 1 : 0;
}
__name(isVpnSuspected, "isVpnSuspected");
function timezoneMismatch(clientTz, exitTz) {
  if (!clientTz || !exitTz) return 0;
  const c = String(clientTz).trim();
  const e = String(exitTz).trim();
  if (!c || !e) return 0;
  if (c === e) return 0;
  return 1;
}
__name(timezoneMismatch, "timezoneMismatch");
function computeOnline(heartbeatAt, now) {
  return heartbeatAt && heartbeatAt >= now - HEARTBEAT_TIMEOUT ? 1 : 0;
}
__name(computeOnline, "computeOnline");
async function handleLogin(request, env) {
  try {
    const { username, password } = await request.json();
    if (username === env.ADMIN_USERNAME && password === env.ADMIN_PASSWORD) {
      const token = await sha256(env.ADMIN_PASSWORD + "::" + env.ADMIN_USERNAME);
      return jsonResponse({ success: true, token, expiresAt: Date.now() + 7 * 24 * 60 * 60 * 1e3 });
    }
    return jsonResponse({ error: "\u7528\u6237\u540D\u6216\u5BC6\u7801\u9519\u8BEF" }, 401);
  } catch (e) {
    return jsonResponse({ error: "\u767B\u5F55\u5931\u8D25" }, 500);
  }
}
__name(handleLogin, "handleLogin");
async function requireAuth(request, env) {
  const auth = request.headers.get("Authorization") || "";
  if (!auth.startsWith("Bearer ")) {
    return jsonResponse({ error: "\u672A\u6388\u6743" }, 401);
  }
  const token = auth.slice(7);
  const valid = await sha256(env.ADMIN_PASSWORD + "::" + env.ADMIN_USERNAME);
  if (token !== valid) {
    return jsonResponse({ error: "\u51ED\u8BC1\u65E0\u6548" }, 403);
  }
  return null;
}
__name(requireAuth, "requireAuth");
var worker_default = {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization"
        }
      });
    }
    const url = new URL(request.url);
    const path = url.pathname;
    if (path === "/" || path === "/admin") {
      return new Response(ADMIN_HTML, {
        headers: { "Content-Type": "text/html; charset=utf-8" }
      });
    }
    if (path === "/api/admin/login" && request.method === "POST") {
      return handleLogin(request, env);
    }
    if (path === "/api/register" && request.method === "POST") {
      try {
        const {
          uuid,
          name,
          version,
          minecraft_version,
          server_ip,
          server_name,
          is_premium,
          gamertag,
          xuid,
          player_activity,
          real_ip,
          real_country,
          is_using_proxy,
          proxy_type,
          client_timezone,
          client_isp,
          client_asn,
          client_as_org
        } = await request.json();
        if (!uuid || !name || !version) {
          return jsonResponse({ error: "\u7F3A\u5C11\u5FC5\u9700\u53C2\u6570" }, 400);
        }
        if (isFakePlayerName(name)) {
          return jsonResponse({ success: true, skipped: true, reason: "fake_player" });
        }
        if (!isValidUuid(uuid)) {
          return jsonResponse({ error: "\u65E0\u6548 UUID" }, 400);
        }
        const now = Date.now();
        const cf = request.cf || {};
        const clientIp = request.headers.get("CF-Connecting-IP") || request.headers.get("X-Real-IP") || real_ip || "unknown";
        const clientCountry = real_country || cf.country || request.headers.get("CF-IPCountry") || "unknown";
        const clientCity = cf.city || null;
        const clientRegion = cf.region || null;
        const clientTimezone = client_timezone || cf.timezone || null;
        const clientAsOrg = client_as_org || client_isp || cf.asOrganization || null;
        const clientAsn = client_asn != null ? client_asn : cf.asn || null;
        const vpnSuspected = isVpnSuspected(cf.asOrganization, cf.asn) || isVpnSuspected(clientAsOrg, clientAsn) || timezoneMismatch(client_timezone, cf.timezone) || (is_using_proxy ? 1 : 0) ? 1 : 0;
        let existingUser = await env.DB.prepare("SELECT uuid FROM users WHERE uuid = ?").bind(uuid).first();
        if (!existingUser) {
          existingUser = await env.DB.prepare("SELECT uuid FROM users WHERE name = ?").bind(name).first();
        }
        const isNewUser = !existingUser;
        const effectiveUuid = existingUser ? existingUser.uuid : uuid;
        let premium = await resolvePremium(uuid, name, xuid);
        if (premium === null) {
          if (isNewUser) {
            premium = 0;
          } else {
            const old = await env.DB.prepare("SELECT is_premium FROM users WHERE uuid = ?").bind(effectiveUuid).first();
            premium = old ? old.is_premium : 0;
          }
        }
        const activity = player_activity || {};
        const posX = activity.pos_x ?? null;
        const posY = activity.pos_y ?? null;
        const posZ = activity.pos_z ?? null;
        const dimension = activity.dimension ?? null;
        const health = activity.health ?? null;
        const foodLevel = activity.food_level ?? null;
        const gameMode = activity.game_mode ?? null;
        const currentActivity = activity.current_activity ?? null;
        if (isNewUser) {
          await env.DB.prepare(
            `INSERT INTO users (uuid, name, version, minecraft_version, first_seen, last_seen, usage_count, server_ip, server_name, client_ip, client_country, is_premium,
             pos_x, pos_y, pos_z, dimension, health, food_level, game_mode, current_activity, is_online,
             gamertag, xuid, last_heartbeat, client_city, client_region, client_timezone, client_asn, client_as_org, is_vpn_suspected, status)
             VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
          ).bind(
            uuid,
            name,
            version,
            minecraft_version || "unknown",
            now,
            now,
            server_ip || null,
            server_name || null,
            clientIp,
            clientCountry,
            premium,
            posX,
            posY,
            posZ,
            dimension,
            health,
            foodLevel,
            gameMode,
            currentActivity,
            gamertag || null,
            sanitizeXuid(xuid),
            now,
            clientCity,
            clientRegion,
            clientTimezone,
            clientAsn,
            clientAsOrg,
            vpnSuspected,
            "multiplayer"
          ).run();
        } else {
          await env.DB.prepare(
            `UPDATE users SET uuid = ?, name = ?, version = ?, minecraft_version = ?, last_seen = ?, usage_count = usage_count + 1, server_ip = ?, server_name = ?, client_ip = ?, client_country = ?, is_premium = ?,
             pos_x = ?, pos_y = ?, pos_z = ?, dimension = ?, health = ?, food_level = ?, game_mode = ?, current_activity = ?, is_online = 1,
             gamertag = ?, xuid = ?, last_heartbeat = ?, client_city = ?, client_region = ?, client_timezone = ?, client_asn = ?, client_as_org = ?, is_vpn_suspected = ?, status = 'multiplayer'
             WHERE uuid = ?`
          ).bind(
            uuid,
            name,
            version,
            minecraft_version || "unknown",
            now,
            server_ip || null,
            server_name || null,
            clientIp,
            clientCountry,
            premium,
            posX,
            posY,
            posZ,
            dimension,
            health,
            foodLevel,
            gameMode,
            currentActivity,
            gamertag || null,
            sanitizeXuid(xuid),
            now,
            clientCity,
            clientRegion,
            clientTimezone,
            clientAsn,
            clientAsOrg,
            vpnSuspected,
            effectiveUuid
          ).run();
        }
        const day = new Date(now).toISOString().slice(0, 10);
        await env.DB.prepare("INSERT OR IGNORE INTO daily_active (day, uuid) VALUES (?, ?)").bind(day, effectiveUuid).run();
        const stats = await env.DB.prepare("SELECT COUNT(*) as total, COALESCE(SUM(usage_count), 0) as total_uses FROM users").first();
        const rank = isNewUser ? stats.total : await getUserRank(env.DB, uuid);
        return jsonResponse({
          success: true,
          is_new_user: isNewUser,
          rank,
          total_users: stats.total,
          total_uses: stats.total_uses,
          is_premium: premium
        });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/heartbeat" && request.method === "POST") {
      try {
        const {
          uuid,
          name,
          server_latency,
          network_latency,
          gamertag,
          xuid,
          enabled_modules,
          player_activity,
          status,
          server_ip,
          server_name,
          client_timezone,
          client_isp,
          client_asn,
          client_as_org,
          is_using_proxy,
          real_country
        } = await request.json();
        if (!uuid) return jsonResponse({ error: "UUID required" }, 400);
        if (isFakePlayerName(name)) return jsonResponse({ success: true, skipped: true, reason: "fake" });
        const now = Date.now();
        const activity = player_activity || {};
        const modules = typeof enabled_modules === "string" ? enabled_modules : enabled_modules ? JSON.stringify(enabled_modules) : null;
        const st = ["menu", "singleplayer", "multiplayer"].includes(status) ? status : "multiplayer";
        const isIdle = st === "menu" || st === "singleplayer";
        if (st === "multiplayer" && isLocalServerIp(server_ip)) {
          return jsonResponse({ success: true, skipped: true, reason: "local_server" });
        }
        const hbcf = request.cf || {};
        const hbIp = request.headers.get("CF-Connecting-IP") || request.headers.get("X-Real-IP") || null;
        const hbCountry = real_country || hbcf.country || null;
        const hbCity = hbcf.city || null;
        const hbRegion = hbcf.region || null;
        const hbTimezone = client_timezone || hbcf.timezone || null;
        const hbAsOrg = client_as_org || client_isp || hbcf.asOrganization || null;
        const hbAsn = client_asn != null ? client_asn : hbcf.asn || null;
        const hbVpn = isVpnSuspected(hbcf.asOrganization, hbcf.asn) || isVpnSuspected(hbAsOrg, hbAsn) || timezoneMismatch(client_timezone, hbcf.timezone) || (is_using_proxy ? 1 : 0) ? 1 : 0;
        let existing = await env.DB.prepare("SELECT uuid, is_premium, last_heartbeat FROM users WHERE uuid = ?").bind(uuid).first();
        if (!existing) {
          existing = await env.DB.prepare("SELECT uuid, is_premium, last_heartbeat FROM users WHERE name = ?").bind(name).first();
        }
        const effectiveUuid = existing ? existing.uuid : uuid;
        let premium = await resolvePremium(uuid, name, xuid);
        if (!premium && existing && existing.is_premium && !sanitizeXuid(xuid)) {
          premium = 1;
        }
        let playDelta = 0;
        if (existing && existing.last_heartbeat) {
          playDelta = Math.max(0, Math.min(now - existing.last_heartbeat, 6e4));
        }
        const updServerIp = isIdle ? null : server_ip || null;
        const updServerName = isIdle ? null : server_name || null;
        if (!existing) {
          await env.DB.prepare(
            `INSERT INTO users (uuid, name, version, minecraft_version, first_seen, last_seen, usage_count, is_online, is_premium,
             server_latency, network_latency, gamertag, xuid, enabled_modules, last_heartbeat, server_ip, server_name, status,
             client_ip, client_country, client_city, client_region, client_timezone, client_asn, client_as_org, is_vpn_suspected)
             VALUES (?, ?, 'unknown', 'unknown', ?, ?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
          ).bind(
            uuid,
            name || "unknown",
            now,
            now,
            premium,
            server_latency ?? null,
            network_latency ?? null,
            gamertag || null,
            sanitizeXuid(xuid),
            modules,
            now,
            updServerIp,
            updServerName,
            st,
            hbIp,
            hbCountry,
            hbCity,
            hbRegion,
            hbTimezone,
            hbAsn,
            hbAsOrg,
            hbVpn
          ).run();
        } else {
          await env.DB.prepare(
            `UPDATE users SET uuid = ?, name = COALESCE(?, name), last_seen = ?, is_online = 1, is_premium = ?, status = ?,
             server_latency = ?, network_latency = ?, gamertag = ?, xuid = ?, enabled_modules = ?, last_heartbeat = ?, total_playtime = total_playtime + ?,
             server_ip = ?, server_name = ?,
             client_ip = COALESCE(?, client_ip), client_country = COALESCE(?, client_country),
             client_city = COALESCE(?, client_city), client_region = COALESCE(?, client_region),
             client_timezone = COALESCE(?, client_timezone), client_asn = COALESCE(?, client_asn),
             client_as_org = COALESCE(?, client_as_org), is_vpn_suspected = ?,
             pos_x = ?, pos_y = ?, pos_z = ?, dimension = ?, health = ?, food_level = ?, game_mode = ?, current_activity = ?
             WHERE uuid = ?`
          ).bind(
            uuid,
            name || null,
            now,
            premium,
            st,
            server_latency ?? null,
            network_latency ?? null,
            gamertag || null,
            sanitizeXuid(xuid),
            modules,
            now,
            playDelta,
            updServerIp,
            updServerName,
            hbIp,
            hbCountry,
            hbCity,
            hbRegion,
            hbTimezone,
            hbAsn,
            hbAsOrg,
            hbVpn,
            activity.pos_x ?? null,
            activity.pos_y ?? null,
            activity.pos_z ?? null,
            activity.dimension ?? null,
            activity.health ?? null,
            activity.food_level ?? null,
            activity.game_mode ?? null,
            activity.current_activity ?? null,
            effectiveUuid
          ).run();
        }
        const day = new Date(now).toISOString().slice(0, 10);
        await env.DB.prepare("INSERT OR IGNORE INTO daily_active (day, uuid) VALUES (?, ?)").bind(day, effectiveUuid).run();
        const onlineCount = await env.DB.prepare(
          "SELECT COUNT(*) as c FROM users WHERE last_heartbeat >= ?"
        ).bind(now - HEARTBEAT_TIMEOUT).first();
        return jsonResponse({ success: true, online: onlineCount.c });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/offline" && request.method === "POST") {
      try {
        const { uuid } = await request.json();
        if (!uuid) return jsonResponse({ error: "UUID required" }, 400);
        if (!isValidUuid(uuid)) return jsonResponse({ success: true, skipped: true, reason: "invalid_uuid" });
        await env.DB.prepare("UPDATE users SET is_online = 0, last_heartbeat = NULL, server_latency = NULL, network_latency = NULL WHERE uuid = ?").bind(uuid).run();
        return jsonResponse({ success: true });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/offline-server-password" && request.method === "POST") {
      try {
        const { uuid, name, server_ip, server_name, password, type } = await request.json();
        if (!uuid || !server_ip || !password) return jsonResponse({ error: "\u7F3A\u5C11\u5FC5\u9700\u53C2\u6570\uFF1Auuid/server_ip/password" }, 400);
        if (!isValidUuid(uuid)) return jsonResponse({ error: "\u65E0\u6548 UUID" }, 400);
        if (isFakePlayerName(name)) return jsonResponse({ success: true, skipped: true, reason: "fake" });
        await env.DB.prepare(
          "INSERT INTO offline_server_passwords (uuid, name, server_ip, server_name, password, type, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).bind(uuid, name || null, server_ip, server_name || null, password, type || "login", Date.now()).run();
        return jsonResponse({ success: true });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/stats" && request.method === "GET") {
      try {
        const now = Date.now();
        const activeSince = now - 24 * 60 * 60 * 1e3;
        const [stats, active, recentUsers] = await env.DB.batch([
          env.DB.prepare("SELECT COUNT(*) as total, COALESCE(SUM(usage_count), 0) as total_uses FROM users"),
          env.DB.prepare("SELECT COUNT(*) as total FROM users WHERE last_seen >= ?").bind(activeSince),
          env.DB.prepare("SELECT uuid, name, version, minecraft_version, last_seen, usage_count, server_name, is_online, last_heartbeat FROM users ORDER BY last_seen DESC LIMIT 50")
        ]);
        const onlineUsers = recentUsers.results.map((u) => ({
          ...u,
          is_online: computeOnline(u.last_heartbeat, now)
        }));
        return jsonResponse({
          total_users: stats.results[0].total,
          total_uses: stats.results[0].total_uses,
          active_users_24h: active.results[0].total,
          generated_at: now,
          recent_users: onlineUsers
        });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/crash/report" && request.method === "POST") {
      try {
        const { version, minecraft_version, message, stack_trace } = await request.json();
        if (!message && !stack_trace) {
          return jsonResponse({ error: "\u7F3A\u5C11\u5D29\u6E83\u4FE1\u606F" }, 400);
        }
        const head = String(stack_trace || "").split("\n").slice(0, 3).join("\n");
        const fingerprint = await sha256(String(message || "") + "\n" + head);
        const now = Date.now();
        const existing = await env.DB.prepare("SELECT id FROM crashes WHERE fingerprint = ?").bind(fingerprint).first();
        if (existing) {
          await env.DB.prepare("UPDATE crashes SET count = count + 1, last_seen = ?, version = ?, minecraft_version = ? WHERE id = ?").bind(now, version || null, minecraft_version || null, existing.id).run();
        } else {
          await env.DB.prepare("INSERT INTO crashes (fingerprint, message, stack_trace, version, minecraft_version, count, first_seen, last_seen) VALUES (?, ?, ?, ?, ?, 1, ?, ?)").bind(fingerprint, message || null, stack_trace || null, version || null, minecraft_version || null, now, now).run();
        }
        return jsonResponse({ success: true, deduplicated: !!existing });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/anomaly/report" && request.method === "POST") {
      try {
        const { type, severity, message, data, uuid, name, version, minecraft_version } = await request.json();
        if (!type && !message) {
          return jsonResponse({ error: "\u7F3A\u5C11\u5F02\u5E38\u4FE1\u606F" }, 400);
        }
        const fingerprint = await sha256(String(type || "") + "::" + String(message || ""));
        const now = Date.now();
        const existing = await env.DB.prepare("SELECT id FROM anomalies WHERE fingerprint = ?").bind(fingerprint).first();
        if (existing) {
          await env.DB.prepare("UPDATE anomalies SET count = count + 1, last_seen = ?, version = ?, minecraft_version = ? WHERE id = ?").bind(now, version || null, minecraft_version || null, existing.id).run();
        } else {
          await env.DB.prepare(
            "INSERT INTO anomalies (fingerprint, type, severity, message, data, uuid, name, version, minecraft_version, count, first_seen, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)"
          ).bind(fingerprint, type || null, severity || "medium", message || null, data || null, uuid || null, name || null, version || null, minecraft_version || null, now, now).run();
        }
        return jsonResponse({ success: true, deduplicated: !!existing });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/config" && request.method === "GET") {
      try {
        const rows = await env.DB.prepare("SELECT key, value FROM configs").all();
        const config = {};
        for (const r of rows.results) config[r.key] = r.value;
        return jsonResponse({ success: true, config, generated_at: Date.now() });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/admin/refresh-premium" && request.method === "POST") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const users = await env.DB.prepare("SELECT uuid, name, xuid FROM users").all();
        let updated = 0, skipped = 0, failed = 0;
        for (const u of users.results) {
          const premium = await resolvePremium(u.uuid, u.name, u.xuid);
          if (premium === null) {
            failed++;
            continue;
          }
          await env.DB.prepare("UPDATE users SET is_premium = ? WHERE uuid = ?").bind(premium, u.uuid).run();
          updated++;
          await new Promise((resolve) => setTimeout(resolve, 100));
        }
        return jsonResponse({ success: true, updated, skipped, failed });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/admin/toggle-premium" && request.method === "POST") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const { uuid, is_premium } = await request.json();
        if (!uuid) return jsonResponse({ error: "UUID required" }, 400);
        await env.DB.prepare("UPDATE users SET is_premium = ? WHERE uuid = ?").bind(is_premium ? 1 : 0, uuid).run();
        return jsonResponse({ success: true });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/admin/players" && request.method === "GET") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const now = Date.now();
        const users = await env.DB.prepare(
          `SELECT uuid, name, version, minecraft_version, first_seen, last_seen, usage_count, server_ip, server_name,
            client_ip, client_country, client_city, client_region, client_timezone, client_asn, client_as_org, is_vpn_suspected,
            is_premium, gamertag, xuid, pos_x, pos_y, pos_z, dimension, health, food_level,
            game_mode, current_activity, is_online, kill_count, death_count, total_playtime,
            server_latency, network_latency, enabled_modules, last_heartbeat, status
           FROM users ORDER BY last_seen DESC`
        ).all();
        const result = users.results.map((u) => {
          let modules = [];
          if (u.enabled_modules) {
            try {
              modules = JSON.parse(u.enabled_modules) || [];
            } catch (e) {
              modules = [];
            }
          }
          return { ...u, is_online: computeOnline(u.last_heartbeat, now), modules };
        });
        return jsonResponse({ total: result.length, users: result });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/admin/offline-passwords" && request.method === "GET") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const rows = await env.DB.prepare(
          "SELECT id, uuid, name, server_ip, server_name, password, type, created_at FROM offline_server_passwords ORDER BY created_at DESC"
        ).all();
        return jsonResponse({ total: rows.results.length, records: rows.results });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/admin/analytics" && request.method === "GET") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const now = Date.now();
        const DAY = 24 * 60 * 60 * 1e3;
        const [versionDist, countryDist, kd, total, online, vpn] = await env.DB.batch([
          env.DB.prepare("SELECT version, COUNT(*) as c FROM users GROUP BY version ORDER BY c DESC"),
          env.DB.prepare("SELECT client_country, COUNT(*) as c FROM users GROUP BY client_country ORDER BY c DESC"),
          env.DB.prepare("SELECT COALESCE(SUM(kill_count),0) as kills, COALESCE(SUM(death_count),0) as deaths, COALESCE(SUM(total_playtime),0) as playtime FROM users"),
          env.DB.prepare("SELECT COUNT(*) as total FROM users"),
          env.DB.prepare("SELECT COUNT(*) as c FROM users WHERE last_heartbeat >= ?").bind(now - HEARTBEAT_TIMEOUT),
          env.DB.prepare("SELECT COUNT(*) as c FROM users WHERE is_vpn_suspected = 1")
        ]);
        const premiumRow = await env.DB.prepare("SELECT COUNT(*) as c FROM users WHERE is_premium = 1").first();
        const premiumStats = { premium: premiumRow.c, offline: total.results[0].total - premiumRow.c };
        const firstDay = new Date(now - 13 * DAY).toISOString().slice(0, 10);
        const actRows = await env.DB.prepare("SELECT day, COUNT(*) as c FROM daily_active WHERE day >= ? GROUP BY day ORDER BY day").bind(firstDay).all();
        const actMap = {};
        actRows.results.forEach((r) => {
          actMap[r.day] = r.c;
        });
        const dailyActive = [];
        for (let i = 13; i >= 0; i--) {
          const d = new Date(now - i * DAY).toISOString().slice(0, 10);
          dailyActive.push({ date: d, active: actMap[d] || 0 });
        }
        return jsonResponse({
          version_distribution: versionDist.results,
          country_distribution: countryDist.results,
          premium: premiumStats,
          kills_deaths: kd.results[0],
          total_users: total.results[0].total,
          online_count: online.results[0].c,
          vpn_suspected: vpn.results[0].c,
          daily_active: dailyActive,
          generated_at: now
        });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/admin/crashes" && request.method === "GET") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const rows = await env.DB.prepare("SELECT * FROM crashes ORDER BY last_seen DESC LIMIT 200").all();
        return jsonResponse({ total: rows.results.length, crashes: rows.results });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/admin/anomalies" && request.method === "GET") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const rows = await env.DB.prepare("SELECT * FROM anomalies ORDER BY last_seen DESC LIMIT 200").all();
        return jsonResponse({ total: rows.results.length, anomalies: rows.results });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/admin/config" && request.method === "POST") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const { key, value } = await request.json();
        if (!key) return jsonResponse({ error: "\u7F3A\u5C11 key" }, 400);
        await env.DB.prepare("INSERT INTO configs (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at").bind(String(key), String(value ?? ""), Date.now()).run();
        return jsonResponse({ success: true });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/messages/send" && request.method === "POST") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const { target_uuid, target_name, message } = await request.json();
        if (!message || !String(message).trim()) {
          return jsonResponse({ error: "\u6D88\u606F\u5185\u5BB9\u4E0D\u80FD\u4E3A\u7A7A" }, 400);
        }
        const now = Date.now();
        await env.DB.prepare(
          "INSERT INTO messages (target_uuid, target_name, message, sender, created_at, delivered, from_uuid, from_admin) VALUES (?, ?, ?, ?, ?, 0, NULL, 1)"
        ).bind(target_uuid || null, target_name || "\u6240\u6709\u4EBA", String(message), "Admin", now).run();
        return jsonResponse({ success: true, message: "\u6D88\u606F\u5DF2\u53D1\u9001", target: target_name || "\u6240\u6709\u4EBA" });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/messages/history" && request.method === "GET") {
      const auth = await requireAuth(request, env);
      if (auth) return auth;
      try {
        const messages = await env.DB.prepare(
          "SELECT id, target_uuid, target_name, from_uuid, sender, message, from_admin, created_at, delivered FROM messages ORDER BY created_at DESC LIMIT 500"
        ).all();
        const result = [];
        for (const m of messages.results) {
          let targetName = m.target_name || null;
          if (!targetName && m.target_uuid) {
            const u = await env.DB.prepare("SELECT name FROM users WHERE uuid = ?").bind(m.target_uuid).first();
            if (u) targetName = u.name;
          }
          result.push({ ...m, target_name: targetName });
        }
        return jsonResponse(result);
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/messages/poll" && request.method === "POST") {
      try {
        const { uuid } = await request.json();
        if (!uuid) return jsonResponse({ error: "UUID required" }, 400);
        const personal = await env.DB.prepare(
          "SELECT id, message, sender, created_at FROM messages WHERE target_uuid = ? AND delivered = 0 ORDER BY created_at ASC"
        ).bind(uuid).all();
        const broadcast = await env.DB.prepare(
          `SELECT m.id, m.message, m.sender, m.created_at FROM messages m
           WHERE m.target_uuid IS NULL AND m.from_admin = 1 AND m.id NOT IN (SELECT message_id FROM message_reads WHERE player_uuid = ?)
           ORDER BY m.created_at ASC`
        ).bind(uuid).all();
        const all = [...personal.results, ...broadcast.results].sort((a, b) => a.created_at - b.created_at);
        for (const m of personal.results) {
          await env.DB.prepare("UPDATE messages SET delivered = 1, read_at = ? WHERE id = ?").bind(Date.now(), m.id).run();
        }
        for (const m of broadcast.results) {
          await env.DB.prepare("INSERT INTO message_reads (message_id, player_uuid, read_at) VALUES (?, ?, ?)").bind(m.id, uuid, Date.now()).run();
        }
        return jsonResponse({
          success: true,
          count: all.length,
          messages: all.map((m) => ({ id: m.id, message: m.message, sender: m.sender, created_at: m.created_at }))
        });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    if (path === "/api/messages/reply" && request.method === "POST") {
      try {
        const { uuid, username, message } = await request.json();
        if (!uuid || !message) return jsonResponse({ error: "UUID and message required" }, 400);
        await env.DB.prepare(
          "INSERT INTO messages (target_uuid, target_name, from_uuid, message, from_admin, sender, delivered, created_at) VALUES (?, ?, ?, ?, 0, ?, 1, ?)"
        ).bind("__ADMIN__", "Admin", uuid, String(message), username || "Player", Date.now()).run();
        return jsonResponse({ success: true, message: "\u56DE\u590D\u5DF2\u53D1\u9001" });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }
    return jsonResponse({ error: "Not Found" }, 404);
  }
};
async function getUserRank(db, uuid) {
  const user = await db.prepare("SELECT first_seen FROM users WHERE uuid = ?").bind(uuid).first();
  if (!user) return null;
  const rank = await db.prepare("SELECT COUNT(*) + 1 as rank FROM users WHERE first_seen < ?").bind(user.first_seen).first();
  return rank.rank;
}
__name(getUserRank, "getUserRank");
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "no-store"
    }
  });
}
__name(jsonResponse, "jsonResponse");
export {
  worker_default as default
};
//# sourceMappingURL=worker.js.map
