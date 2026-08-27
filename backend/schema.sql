-- Yiyiaddon 用户统计数据库表结构

CREATE TABLE IF NOT EXISTS users (
    uuid TEXT PRIMARY KEY,                  -- 玩家 UUID（唯一标识）
    name TEXT NOT NULL,                     -- 玩家游戏名
    version TEXT NOT NULL,                  -- 扩展版本号
    minecraft_version TEXT DEFAULT 'unknown', -- Minecraft 版本
    first_seen INTEGER NOT NULL,            -- 首次使用时间（Unix 时间戳）
    last_seen INTEGER NOT NULL,             -- 最后使用时间（Unix 时间戳）
    usage_count INTEGER NOT NULL DEFAULT 1,
    server_ip TEXT,
    server_name TEXT,
    client_ip TEXT,
    client_country TEXT,
    is_premium INTEGER DEFAULT 0
);

-- 索引：加速排名查询
CREATE INDEX IF NOT EXISTS idx_first_seen ON users(first_seen);
CREATE INDEX IF NOT EXISTS idx_last_seen ON users(last_seen);

-- 管理员 Token 表（动态生成的临时令牌）
CREATE TABLE IF NOT EXISTS admin_tokens (
  token TEXT PRIMARY KEY,
  expires_at INTEGER NOT NULL,
  created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);

-- 清理过期 Token 的索引
CREATE INDEX IF NOT EXISTS idx_tokens_expires ON admin_tokens(expires_at);
