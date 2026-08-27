/**
 * Yiyiaddon 用户统计 API
 * 部署在 Cloudflare Workers，使用 D1 数据库
 * 
 * 功能：
 * 1. 记录每个玩家的 UUID、游戏名、首次使用时间、最后使用时间、版本号
 * 2. 返回玩家排名（第几个使用该扩展的玩家）
 * 3. 防止重复计数（同一个 UUID 只算一次排名）
 */

export default {
  async fetch(request, env) {
    // CORS 预检请求
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type',
        },
      });
    }

    const url = new URL(request.url);

    // 注册/更新用户
    if (url.pathname === '/api/register' && request.method === 'POST') {
      try {
        const { uuid, name, version, minecraft_version, server_ip, server_name } = await request.json();

        if (!uuid || !name || !version) {
          return jsonResponse({ error: '缺少必需参数' }, 400);
        }

        const now = Date.now();

        // 检查用户是否已存在
        const existingUser = await env.DB.prepare(
          'SELECT uuid, first_seen FROM users WHERE uuid = ?'
        ).bind(uuid).first();

        const isNewUser = !existingUser;

        if (isNewUser) {
          await env.DB.prepare(
            `INSERT INTO users (uuid, name, version, minecraft_version, first_seen, last_seen, usage_count, server_ip, server_name) 
             VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)`
          ).bind(uuid, name, version, minecraft_version || 'unknown', now, now, server_ip || null, server_name || null).run();
        } else {
          await env.DB.prepare(
            `UPDATE users 
             SET name = ?, version = ?, minecraft_version = ?, last_seen = ?, usage_count = usage_count + 1, server_ip = ?, server_name = ? 
             WHERE uuid = ?`
          ).bind(name, version, minecraft_version || 'unknown', now, server_ip || null, server_name || null, uuid).run();
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
          env.DB.prepare('SELECT name, version, last_seen, server_ip, server_name FROM users ORDER BY last_seen DESC LIMIT 50')
        ]);

        return jsonResponse({
          total_users: stats.results[0].total,
          total_uses: stats.results[0].total_uses,
          active_users_24h: active.results[0].total,
          generated_at: now,
          recent_users: recentUsers.results.map(u => ({
            name: u.name,
            version: u.version,
            last_seen: u.last_seen,
            server_ip: u.server_ip,
            server_name: u.server_name
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
