# Yiyiaddon 用户管理后台

## 🌐 网站地址

**生产环境：**
```
https://yiyiaddon-dashboard.pages.dev
```

## 📋 功能介绍

### 实时统计
- 📊 累计用户数
- 🔢 累计使用次数
- ⏰ 24小时活跃用户
- 💚 当前在线玩家

### 玩家详细信息
- 👤 玩家名称和UUID
- ✅ 正版/离线账户检测
- 🌍 客户端真实IP和国家（带国旗emoji）
- 🎮 服务器IP和名称
- 📅 最后上线时间
- 🔢 累计使用次数
- 🟢 在线状态（5分钟内活跃=在线）

### 其他功能
- 🔍 实时搜索（支持玩家名、UUID、IP、国家、服务器）
- 🔄 自动刷新（每30秒）
- 🎨 美化界面（渐变背景、动画效果、光效）

## 🚀 部署方式

### 方法1：使用 Wrangler CLI 部署

```powershell
# 进入后台目录
cd "C:\Users\fxjpc\Documents\trae_projects\参考代码\26.1.2"

# 部署到 Cloudflare Pages
wrangler pages deploy admin-dashboard --project-name=yiyiaddon-dashboard --branch=main
```

### 方法2：本地预览

```powershell
# 使用 Python 启动本地服务器
cd "C:\Users\fxjpc\Documents\trae_projects\参考代码\26.1.2\admin-dashboard"
python -m http.server 8080

# 浏览器访问：http://localhost:8080
```

或者直接双击 `index.html` 文件在浏览器打开。

## 📡 后端 API

**API 地址：**
```
https://yiyiaddon.fxjcangku.workers.dev/api/stats
```

**返回数据：**
```json
{
  "total_users": 29,
  "total_uses": 29,
  "active_users_24h": 3,
  "recent_users": [
    {
      "name": "玩家名",
      "uuid": "uuid",
      "version": "1.1-beta4-personal",
      "minecraft_version": "1.21.2",
      "client_ip": "1.2.3.4",
      "client_country": "CN",
      "is_premium": 1,
      "server_ip": "play.hypixel.net",
      "server_name": "Hypixel",
      "last_seen": 1724739445000,
      "usage_count": 1
    }
  ]
}
```

## 🔧 技术栈

- **前端：** 纯HTML + CSS + JavaScript
- **托管：** Cloudflare Pages
- **后端：** Cloudflare Workers + D1 数据库
- **CDN：** Cloudflare 全球节点
- **HTTPS：** 免费自动证书

## 📝 更新历史

### 2026-08-27
- ✅ 初始部署
- ✅ 添加IP地理位置和国家显示
- ✅ 添加正版账户检测
- ✅ 添加服务器信息记录
- ✅ 修复在线状态bug
- ✅ 美化界面（渐变背景、动画效果）
- ✅ 删除副标题文字

## 📞 联系方式

- **GitHub：** https://github.com/fxjcangku/26.1.2
- **邮箱：** fxjggyx@gmail.com

## 🎯 使用场景

1. **统计分析：** 查看有多少人在使用你的扩展
2. **地理分布：** 了解用户来自哪些国家
3. **服务器发现：** 找到热门服务器一起玩
4. **活跃度监控：** 查看24小时活跃用户
5. **账户类型：** 统计正版和离线玩家比例

---

**提示：** 此后台数据完全公开透明，所有使用你扩展的玩家信息都会显示在这里。
