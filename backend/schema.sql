-- Yiyiaddon 用户统计数据库表结构

CREATE TABLE IF NOT EXISTS users (
    uuid TEXT PRIMARY KEY,                  -- 玩家 UUID（唯一标识）
    name TEXT NOT NULL,                     -- 玩家游戏名
    version TEXT NOT NULL,                  -- 扩展版本号
    minecraft_version TEXT DEFAULT 'unknown', -- Minecraft 版本
    first_seen INTEGER NOT NULL,            -- 首次使用时间（Unix 时间戳）
    last_seen INTEGER NOT NULL              -- 最后使用时间（Unix 时间戳）
);

-- 索引：加速排名查询
CREATE INDEX IF NOT EXISTS idx_first_seen ON users(first_seen);
CREATE INDEX IF NOT EXISTS idx_last_seen ON users(last_seen);
