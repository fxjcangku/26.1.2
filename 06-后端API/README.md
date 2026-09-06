# Yiyiaddon 用户统计系统部署指南

## 📊 系统概述

这是一个完全免费的用户统计系统，用于记录使用 Yiyiaddon 扩展的玩家数量和排名。

**功能特性：**
- ✅ 记录每个玩家的 UUID、游戏名、首次使用时间、最后使用时间、版本号
- ✅ 进入游戏时显示玩家排名（第几个使用该扩展的玩家）
- ✅ 防止重复计数（同一个 UUID 只算一次排名）
- ✅ 管理员接口查看所有用户列表和统计数据
- ✅ 完全免费（使用 Cloudflare Workers + D1 数据库）

---

## 🚀 快速部署（5分钟搞定）

### 第一步：注册 Cloudflare 账号

1. 访问 https://dash.cloudflare.com/sign-up
2. 免费注册账号（不需要信用卡）

### 第二步：安装 Wrangler CLI

```bash
npm install -g wrangler
```

### 第三步：登录 Cloudflare

```bash
wrangler login
```

### 第四步：创建 D1 数据库

```bash
cd 06-后端API
wrangler d1 create yiyiaddon-users
```

**复制输出的 `database_id`，填入 `wrangler.toml` 的 `database_id` 字段。**

### 第五步：初始化数据库表

```bash
wrangler d1 execute yiyiaddon-users --file=schema.sql
```

### 第六步：修改配置文件

打开 `wrangler.toml`，修改：

```toml
[[d1_databases]]
binding = "DB"
database_name = "yiyiaddon-users"
database_id = "你刚才复制的 database_id"

[vars]
ADMIN_KEY = "随便设置一个密钥（用于查看用户列表）"
```

### 第七步：部署到 Cloudflare Workers

```bash
wrangler deploy
```

**部署成功后会显示你的 API 地址，类似：**
```
https://yiyiaddonadmin.fxjggyx.workers.dev
```

### 第八步：更新客户端代码

修改 `YiyiaddonWelcomeService.java` 第 32 行：

```java
private static final String STATS_API_URL = "https://yiyiaddonadmin.fxjggyx.workers.dev/api/register";
```

改成你刚才部署后显示的地址。

### 第九步：重新编译

```bash
cd ..
./gradlew.bat buildOfficial
```

---

## 🎮 效果展示

玩家进入游戏时会看到：

```
§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[Yiyiaddon | 统计] yiyijia 你是第 50 个使用该扩展的玩家 ✓
[Yiyiaddon | 统计] 当前已有 50 位玩家使用该扩展
§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

老玩家（已注册过）会看到：

```
§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[Yiyiaddon | 统计] 欢迎回来 yiyijia！你是第 50 个使用该扩展的玩家
[Yiyiaddon | 统计] 当前已有 123 位玩家使用该扩展
§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 📊 管理员接口

### 1. 查看统计信息（公开接口）

```bash
curl https://yiyiaddonadmin.fxjggyx.workers.dev/api/stats
```

返回：
```json
{
  "total_users": 123,
  "recent_users": [
    {
      "name": "yiyijia",
      "version": "1.1-beta2",
      "last_seen": "2026-08-26T12:34:56.789Z"
    }
  ]
}
```

### 2. 查看所有用户列表（需要管理员密钥）

```bash
curl "https://yiyiaddonadmin.fxjggyx.workers.dev/api/users?key=你的ADMIN_KEY"
```

返回：
```json
{
  "total": 123,
  "users": [
    {
      "rank": 1,
      "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "name": "player1",
      "version": "1.1-beta2",
      "minecraft_version": "26.1.2",
      "first_seen": "2026-08-01T10:00:00.000Z",
      "last_seen": "2026-08-26T12:34:56.789Z"
    }
  ]
}
```

---

## 💾 数据库结构

```sql
CREATE TABLE users (
    uuid TEXT PRIMARY KEY,              -- 玩家 UUID（唯一标识）
    name TEXT NOT NULL,                 -- 玩家游戏名
    version TEXT NOT NULL,              -- 扩展版本号
    minecraft_version TEXT,             -- Minecraft 版本
    first_seen INTEGER NOT NULL,        -- 首次使用时间（Unix 时间戳）
    last_seen INTEGER NOT NULL          -- 最后使用时间（Unix 时间戳）
);
```

---

## 🔒 隐私说明

**收集的数据：**
- 玩家 UUID（Mojang 官方 UUID，用于防止重复计数）
- 游戏名（当前使用的游戏名）
- 扩展版本号
- Minecraft 版本号
- 首次使用时间和最后使用时间

**不收集的数据：**
- IP 地址
- 游戏内行为数据
- 服务器信息
- 任何敏感信息

**数据用途：**
- 仅用于统计玩家数量和排名
- 管理员可查看用户列表（需要密钥）
- 不会出售或分享给第三方

---

## 🛠️ 故障排查

### 问题1：部署失败

**解决方案：**
```bash
# 检查 wrangler 版本
wrangler --version

# 更新到最新版
npm update -g wrangler

# 重新登录
wrangler login

# 重新部署
wrangler deploy
```

### 问题2：玩家进游戏看不到排名

**可能原因：**
1. API 地址配置错误 → 检查 `YiyiaddonWelcomeService.java` 第 32 行
2. 统计服务器未部署成功 → 浏览器访问 API 地址测试
3. 网络问题 → 客户端会静默失败，不影响游戏

**测试方法：**
```bash
# 测试 API 是否正常
curl -X POST https://yiyiaddonadmin.fxjggyx.workers.dev/api/register \
  -H "Content-Type: application/json" \
  -d '{"uuid":"test-uuid","name":"测试玩家","version":"1.1-beta2","minecraft_version":"26.1.2"}'
```

### 问题3：排名不准确

**解决方案：**
- 排名按 `first_seen` 升序排列，先注册的玩家排名靠前
- 如果需要重置数据库：
  ```bash
  wrangler d1 execute yiyiaddon-users --command "DELETE FROM users"
  ```

---

## 📈 费用说明

**完全免费！**

Cloudflare Workers 免费额度：
- 每天 100,000 次请求
- D1 数据库免费 5GB 存储
- 免费 10,000,000 行读取/天

**你的扩展就算有 10,000 个活跃玩家，也完全在免费额度内。**

---

## 🎯 下一步优化建议

1. **添加数据看板**：用 Cloudflare Pages 部署一个网页，实时显示用户统计
2. **地理位置统计**：通过 Cloudflare 的 `request.cf.country` 统计玩家来自哪些国家
3. **版本分布统计**：统计每个版本有多少玩家使用
4. **活跃度统计**：统计每日/每周/每月活跃玩家数

---

## 📞 技术支持

- GitHub Issues: https://github.com/fxjcangku/26.1.2/issues
- Cloudflare Workers 文档: https://developers.cloudflare.com/workers/
- Cloudflare D1 文档: https://developers.cloudflare.com/d1/
