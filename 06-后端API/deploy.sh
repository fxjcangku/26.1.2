#!/bin/bash

# Yiyiaddon 用户统计系统一键部署脚本

set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Yiyiaddon 用户统计系统部署"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 检查 wrangler 是否安装
if ! command -v wrangler &> /dev/null; then
    echo "❌ 未检测到 wrangler CLI"
    echo "正在安装 wrangler..."
    npm install -g wrangler
fi

echo "✅ wrangler CLI 已安装"
echo ""

# 登录 Cloudflare
echo "📝 请登录 Cloudflare 账号..."
wrangler login

echo ""
echo "📊 创建 D1 数据库..."
wrangler d1 create yiyiaddon-users

echo ""
echo "⚠️  请复制上面输出的 database_id，并填入 wrangler.toml 的 database_id 字段"
echo "按回车继续..."
read

echo ""
echo "📦 初始化数据库表..."
wrangler d1 execute yiyiaddon-users --file=schema.sql

echo ""
echo "🚀 部署到 Cloudflare Workers..."
wrangler deploy

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✅ 部署成功！"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 下一步："
echo "1. 复制上面显示的 API 地址"
echo "2. 修改 YiyiaddonWelcomeService.java 第 32 行的 STATS_API_URL"
echo "3. 重新编译：./gradlew.bat buildOfficial"
echo ""
echo "🎮 测试 API："
echo "curl -X POST 你的API地址/api/register \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '{\"uuid\":\"test\",\"name\":\"测试\",\"version\":\"1.1-beta2\"}'"
echo ""
