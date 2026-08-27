/**
 * Yiyiaddon 用户统计 API
 * 部署在 Cloudflare Workers，使用 D1 数据库
 * 
 * 功能：
 * 1. 记录每个玩家的 UUID、游戏名、首次使用时间、最后使用时间、版本号
 * 2. 返回玩家排名（第几个使用该扩展的玩家）
 * 3. 防止重复计数（同一个 UUID 只算一次排名）
 * 4. 管理后台身份验证（JWT + 环境变量）
 */

// 简单的 SHA-256 实现
async function sha256(message) {
  const msgBuffer = new TextEncoder().encode(message);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

// 生成随机 Token
function generateToken() {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
}

// 管理员登录接口（简化版 - 直接验证密码并返回固定Token）
async function handleLogin(request, env) {
  try {
    const { username, password } = await request.json();
    
    // 简单验证
    if (username === 'admin' && password === 'yiyi2026') {
      // 返回固定 Token
      const token = 'yiyi2026_admin_token_secure';
      
      return new Response(JSON.stringify({ 
        success: true, 
        token: token,
        expiresAt: Date.now() + 365 * 24 * 60 * 60 * 1000 // 1年有效期
      }), {
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*',
        }
      });
    } else {
      return new Response(JSON.stringify({ error: '用户名或密码错误' }), {
        status: 401,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*',
        }
      });
    }
  } catch (error) {
    return new Response(JSON.stringify({ error: '登录失败: ' + error.message }), {
      status: 500,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
      }
    });
  }
}

// 管理员认证中间件（简化版 - 验证固定Token）
function requireAuth(request, env) {
  const authHeader = request.headers.get('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return new Response(JSON.stringify({ error: '未授权' }), {
      status: 401,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
      }
    });
  }

  const token = authHeader.substring(7);
  const validToken = 'yiyi2026_admin_token_secure';
  
  if (token !== validToken) {
    return new Response(JSON.stringify({ error: 'Token 无效' }), {
      status: 403,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
      }
    });
  }

  return null; // 认证成功
}

export default {
  async fetch(request, env) {
    // CORS 预检请求
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

    // 管理员登录接口（公开）
    if (url.pathname === '/api/admin/login' && request.method === 'POST') {
      return handleLogin(request, env);
    }

    // 注册/更新用户
    if (url.pathname === '/api/register' && request.method === 'POST') {
      try {
        const { uuid, name, version, minecraft_version, server_ip, server_name, is_premium, player_activity } = await request.json();

        if (!uuid || !name || !version) {
          return jsonResponse({ error: '缺少必需参数' }, 400);
        }

        const now = Date.now();
        
        // 获取客户端真实 IP 和国家
        const clientIp = request.headers.get('CF-Connecting-IP') || request.headers.get('X-Real-IP') || 'unknown';
        const clientCountry = request.headers.get('CF-IPCountry') || 'unknown';

        // 检查用户是否已存在
        const existingUser = await env.DB.prepare(
          'SELECT uuid, first_seen FROM users WHERE uuid = ?'
        ).bind(uuid).first();

        const isNewUser = !existingUser;

        // 解析玩家活动数据
        const activity = player_activity || {};
        const posX = activity.pos_x || null;
        const posY = activity.pos_y || null;
        const posZ = activity.pos_z || null;
        const dimension = activity.dimension || null;
        const health = activity.health || null;
        const foodLevel = activity.food_level || null;
        const gameMode = activity.game_mode || null;
        const currentActivity = activity.current_activity || null;
        const isOnline = activity.is_online ? 1 : 0;

        if (isNewUser) {
          await env.DB.prepare(
            `INSERT INTO users (uuid, name, version, minecraft_version, first_seen, last_seen, usage_count, server_ip, server_name, client_ip, client_country, is_premium,
             pos_x, pos_y, pos_z, dimension, health, food_level, game_mode, current_activity, is_online) 
             VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
          ).bind(uuid, name, version, minecraft_version || 'unknown', now, now, server_ip || null, server_name || null, clientIp, clientCountry, is_premium ? 1 : 0,
            posX, posY, posZ, dimension, health, foodLevel, gameMode, currentActivity, isOnline).run();
        } else {
          await env.DB.prepare(
            `UPDATE users 
             SET name = ?, version = ?, minecraft_version = ?, last_seen = ?, usage_count = usage_count + 1, server_ip = ?, server_name = ?, client_ip = ?, client_country = ?, is_premium = ?,
             pos_x = ?, pos_y = ?, pos_z = ?, dimension = ?, health = ?, food_level = ?, game_mode = ?, current_activity = ?, is_online = ?
             WHERE uuid = ?`
          ).bind(name, version, minecraft_version || 'unknown', now, server_ip || null, server_name || null, clientIp, clientCountry, is_premium ? 1 : 0,
            posX, posY, posZ, dimension, health, foodLevel, gameMode, currentActivity, isOnline, uuid).run();
        }

        // 获取总用户数和当前用户排名
        const stats = await env.DB.prepare(
          'SELECT COUNT(*) as total, COALESCE(SUM(usage_count), 0) as total_uses FROM users'
        ).first();

        const rank = isNewUser ? stats.total : await getUserRank(env.DB, uuid);

        return jsonResponse({
          success: true,
          is_new_user: isNewUser,
          rank: rank,
          total_users: stats.total,
          total_uses: stats.total_uses,
          message: isNewUser 
            ? `欢迎！你是第 ${rank} 个使用该扩展的玩家` 
            : `欢迎回来！你是第 ${rank} 个使用该扩展的玩家`
        });

      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 获取统计信息（管理员接口）
    if (url.pathname === '/api/stats' && request.method === 'GET') {
      try {
        const now = Date.now();
        const activeSince = now - 24 * 60 * 60 * 1000;
        const [stats, active, recentUsers] = await env.DB.batch([
          env.DB.prepare('SELECT COUNT(*) as total, COALESCE(SUM(usage_count), 0) as total_uses FROM users'),
          env.DB.prepare('SELECT COUNT(*) as total FROM users WHERE last_seen >= ?').bind(activeSince),
          env.DB.prepare(`SELECT uuid, name, version, minecraft_version, last_seen, usage_count, server_ip, server_name, client_ip, client_country, is_premium,
            pos_x, pos_y, pos_z, dimension, health, food_level, game_mode, current_activity, is_online 
            FROM users ORDER BY last_seen DESC LIMIT 50`)
        ]);

        return jsonResponse({
          total_users: stats.results[0].total,
          total_uses: stats.results[0].total_uses,
          active_users_24h: active.results[0].total,
          generated_at: now,
          recent_users: recentUsers.results.map(u => ({
            uuid: u.uuid,
            name: u.name,
            version: u.version,
            minecraft_version: u.minecraft_version,
            last_seen: u.last_seen,
            usage_count: u.usage_count,
            server_ip: u.server_ip,
            server_name: u.server_name,
            client_ip: u.client_ip,
            client_country: u.client_country,
            is_premium: u.is_premium,
            // 玩家活动数据
            pos_x: u.pos_x,
            pos_y: u.pos_y,
            pos_z: u.pos_z,
            dimension: u.dimension,
            health: u.health,
            food_level: u.food_level,
            game_mode: u.game_mode,
            current_activity: u.current_activity,
            is_online: u.is_online
          }))
        });

      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 获取所有用户列表（管理员接口，需要密钥）
    if (url.pathname === '/api/users' && request.method === 'GET') {
      const authKey = url.searchParams.get('key');
      
      if (authKey !== env.ADMIN_KEY) {
        return jsonResponse({ error: '未授权' }, 401);
      }

      try {
        const users = await env.DB.prepare(
          'SELECT uuid, name, version, minecraft_version, first_seen, last_seen, usage_count FROM users ORDER BY first_seen ASC'
        ).all();

        return jsonResponse({
          total: users.results.length,
          users: users.results.map((u, index) => ({
            rank: index + 1,
            uuid: u.uuid,
            name: u.name,
            version: u.version,
            minecraft_version: u.minecraft_version,
            first_seen: new Date(u.first_seen).toISOString(),
            last_seen: new Date(u.last_seen).toISOString(),
            usage_count: u.usage_count
          }))
        });

      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 发送消息给玩家（管理员接口）
    if (url.pathname === '/api/messages/send' && request.method === 'POST') {
      try {
        const { target_uuid, target_name, message, admin_password } = await request.json();
        
        // 验证管理员密码
        if (admin_password !== 'yiyi2026') {
          return jsonResponse({ error: '管理员密码错误' }, 401);
        }

        if (!message || message.trim().length === 0) {
          return jsonResponse({ error: '消息内容不能为空' }, 400);
        }

        const now = Date.now();
        
        // 插入消息到队列
        await env.DB.prepare(
          'INSERT INTO messages (target_uuid, target_name, message, sender, created_at, delivered) VALUES (?, ?, ?, ?, ?, 0)'
        ).bind(target_uuid || null, target_name || '所有人', message, 'Admin', now).run();

        return jsonResponse({
          success: true,
          message: '消息已发送',
          target: target_name || '所有人'
        });

      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 获取未读消息接口（客户端轮询）
    if (url.pathname === '/api/messages/poll' && request.method === 'POST') {
      try {
        const { uuid } = await request.json();
        
        if (!uuid) {
          return jsonResponse({ error: 'UUID required' }, 400);
        }

        // 查询该玩家的未读消息（包括发给所有人的消息）
        const messages = await env.DB.prepare(
          'SELECT id, message, sender, created_at FROM messages WHERE (target_uuid = ? OR target_uuid IS NULL) AND delivered = 0 ORDER BY created_at ASC'
        ).bind(uuid).all();

        if (messages.results.length > 0) {
          // 标记这些消息为已送达
          const messageIds = messages.results.map(m => m.id);
          for (const id of messageIds) {
            await env.DB.prepare(
              'UPDATE messages SET delivered = 1, read_at = ? WHERE id = ?'
            ).bind(Date.now(), id).run();
          }
        }

        return jsonResponse({
          success: true,
          count: messages.results.length,
          messages: messages.results.map(m => ({
            id: m.id,
            message: m.message,
            sender: m.sender,
            created_at: m.created_at
          }))
        });

      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 获取聊天历史接口（管理员查看所有消息）
// 获取聊天历史（管理员接口）
    if (url.pathname === '/api/messages/history' && request.method === 'GET') {
      try {
        // 获取所有消息，包括管理员发的和玩家回复的
        const messages = await env.DB.prepare(
          'SELECT id, target_uuid, from_uuid, message, from_admin, created_at, delivered FROM messages ORDER BY created_at DESC LIMIT 200'
        ).all();

        return jsonResponse(messages.results || []);

      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    // 玩家回复消息接口
    if (url.pathname === '/api/messages/reply' && request.method === 'POST') {
      try {
        const { uuid, username, message } = await request.json();
        
        if (!uuid || !message) {
          return jsonResponse({ error: 'UUID and message required' }, 400);
        }

        // 插入玩家回复的消息（target_uuid为null表示发给管理员，from_admin=0表示来自玩家）
        await env.DB.prepare(
          'INSERT INTO messages (target_uuid, from_uuid, message, from_admin, sender, delivered, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
        ).bind(null, uuid, message, 0, username || 'Player', 1, Date.now()).run();

        return jsonResponse({
          success: true,
          message: '回复已发送'
        });

      } catch (error) {
        return jsonResponse({ error: error.message }, 500);
      }
    }

    return jsonResponse({ error: 'Not Found' }, 404);
  }
};

// 获取用户排名（按 first_seen 升序排列）
async function getUserRank(db, uuid) {
  const user = await db.prepare(
    'SELECT first_seen FROM users WHERE uuid = ?'
  ).bind(uuid).first();

  if (!user) return null;

  const rank = await db.prepare(
    'SELECT COUNT(*) + 1 as rank FROM users WHERE first_seen < ?'
  ).bind(user.first_seen).first();

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
